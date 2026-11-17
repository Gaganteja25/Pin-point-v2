const { onRequest } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("crypto");

initializeApp();

const DATABASE_ID = "ai-studio-android-apkdirec-94ade64d-cd60-4368-abc6-2261d83b2f94";
const db = getFirestore(DATABASE_ID);

async function resolvePaymentCredentials() {
  let keyId = process.env.RAZORPAY_KEY_ID || "";
  let keySecret = process.env.RAZORPAY_KEY_SECRET || "";

  if (!keySecret) {
    const secretSnap = await db.collection("serverSecrets").doc("payments").get();
    if (secretSnap.exists) {
      const s = secretSnap.data();
      keyId = keyId || s.razorpayKeyId || "";
      keySecret = s.razorpayKeySecret || "";
    }
  }
  return { keyId, keySecret };
}

/**
 * POST /api/v1/payments/razorpay/order
 * Creates a Razorpay order server-side using the Key Secret stored ONLY on the server.
 */
exports.createRazorpayOrder = onRequest({ cors: true }, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }
  try {
    const { campaignId, businessId, durationDays, idempotencyKey } = req.body || {};
    if (!campaignId || !businessId || !durationDays) {
      return res.status(400).json({ error: "Missing campaignId, businessId, or durationDays" });
    }

    const bizSnap = await db.collection("businesses").doc(businessId).get();
    if (!bizSnap.exists || bizSnap.data().approvalStatus !== "APPROVED") {
      return res.status(403).json({ error: "Business account is not approved by owner." });
    }

    const cfgSnap = await db.collection("appConfig").doc("main").get();
    const dailyRateInr = cfgSnap.exists ? Number(cfgSnap.data().dailyAdPriceInr || 3) : 3;
    const clampedDays = Math.min(100, Math.max(7, Number(durationDays)));
    const amountPaise = clampedDays * dailyRateInr * 100;

    const { keyId, keySecret } = await resolvePaymentCredentials();
    if (!keyId || !keySecret) {
      return res.status(503).json({
        error: "Razorpay credentials not configured on server yet."
      });
    }

    const authHeader = "Basic " + Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const rzpRes = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": authHeader,
        ...(idempotencyKey ? { "X-Razorpay-Idempotency-Key": String(idempotencyKey) } : {})
      },
      body: JSON.stringify({
        amount: amountPaise,
        currency: "INR",
        receipt: String(campaignId).slice(0, 40),
        notes: { campaignId, businessId, durationDays: String(clampedDays) }
      })
    });

    const rzpBody = await rzpRes.json();
    if (!rzpRes.ok) {
      return res.status(rzpRes.status).json({ error: rzpBody.error?.description || "Razorpay order creation failed" });
    }

    return res.status(200).json({
      orderId: rzpBody.id,
      amountPaise: rzpBody.amount,
      currency: rzpBody.currency,
      keyId
    });
  } catch (err) {
    console.error("createRazorpayOrder error:", err);
    return res.status(500).json({ error: "Internal server error" });
  }
});

/**
 * POST /api/v1/payments/razorpay/verify
 * Verifies Razorpay HMAC-SHA256 signature using server Key Secret before marking promotion ACTIVE.
 */
exports.verifyRazorpayPayment = onRequest({ cors: true }, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }
  try {
    const { campaignId, razorpayOrderId, razorpayPaymentId, razorpaySignature } = req.body || {};
    if (!campaignId || !razorpayOrderId || !razorpayPaymentId || !razorpaySignature) {
      return res.status(400).json({ error: "Missing verification parameters" });
    }

    const { keySecret } = await resolvePaymentCredentials();
    if (!keySecret) {
      return res.status(503).json({ error: "Payment secret not configured." });
    }

    const expectedSig = crypto
      .createHmac("sha256", keySecret)
      .update(`${razorpayOrderId}|${razorpayPaymentId}`)
      .digest("hex");

    const sigBuf = Buffer.from(String(razorpaySignature));
    const expBuf = Buffer.from(expectedSig);
    if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
      await db.collection("payments").add({
        businessUid: "unknown",
        businessName: "Signature Mismatch",
        campaignId,
        amountInr: 0,
        durationDays: 0,
        expiryDateMs: 0,
        provider: "razorpay",
        orderId: razorpayOrderId,
        paymentId: razorpayPaymentId,
        status: "FAILED",
        note: "HMAC-SHA256 verification failed",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });
      return res.status(400).json({ verified: false, campaignStatus: "FAILED", message: "Invalid payment signature" });
    }

    const promoRef = db.collection("promotions").doc(campaignId);
    const promoSnap = await promoRef.get();
    if (!promoSnap.exists) {
      return res.status(404).json({ error: "Campaign not found" });
    }
    const promo = promoSnap.data();
    const expiryDateMs = Date.now() + Number(promo.durationDays || 30) * 86400000;

    await promoRef.update({
      status: "ACTIVE",
      razorpayOrderId,
      expiresAtMs: expiryDateMs,
      updatedAt: FieldValue.serverTimestamp()
    });

    await db.collection("payments").add({
      businessUid: promo.businessUid,
      businessName: promo.businessName,
      campaignId,
      amountInr: promo.priceInr,
      durationDays: promo.durationDays,
      expiryDateMs,
      provider: "razorpay",
      orderId: razorpayOrderId,
      paymentId: razorpayPaymentId,
      status: "PAID",
      note: "Verified server-side via HMAC-SHA256",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    return res.status(200).json({
      verified: true,
      campaignStatus: "ACTIVE",
      message: "Payment verified and campaign activated."
    });
  } catch (err) {
    console.error("verifyRazorpayPayment error:", err);
    return res.status(500).json({ error: "Internal verification error" });
  }
});
