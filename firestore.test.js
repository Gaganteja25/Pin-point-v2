const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const OWNER_UID = "owner_gagan";
const OWNER_CLAIMS = {
  email: "gaganteja25042003@gmail.com",
  email_verified: true,
};

const PAYMENTS_ADMIN_UID = "admin_pay";
const FEEDBACK_ADMIN_UID = "admin_fb";
const FULL_CONTROL_ADMIN_UID = "admin_full";
const BIZ_A_UID = "biz_user_a";
const BIZ_B_UID = "biz_user_b";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAppConfig(now = new Date(Date.now() - 1000)) {
  return {
    appName: "PINPOINT",
    tagline: "Live crowd coordination & local discovery",
    supportEmail: "breakfreeentertainments0@gmail.com",
    mapProvider: "openstreetmap",
    mapTileUrl: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    mapAttribution: "© OpenStreetMap contributors",
    mapApiKey: "",
    paymentProvider: "razorpay",
    razorpayKeyId: "rzp_test_1234567890",
    paymentSecretConfigured: true,
    paymentSecretLast4: "9999",
    dailyAdPriceInr: 3,
    syncServerUrl: "",
    enableChat: true,
    enableNearbyPlaces: true,
    enableBusinessAds: true,
    updatedBy: OWNER_UID,
    updatedAt: now,
  };
}

function validBusiness(uid, status = "PENDING", now = new Date(Date.now() - 1000)) {
  return {
    uid,
    ownerEmail: `${uid}@gmail.com`,
    name: "Cafe Deccan",
    category: "Restaurants",
    description: "Artisanal coffee and snacks near the stadium",
    website: "https://cafedeccan.example",
    contactPhone: "+919876543210",
    contactEmail: `${uid}@gmail.com`,
    addressLabel: "Madhapur, Hyderabad",
    latitude: 17.4483,
    longitude: 78.3915,
    approvalStatus: status,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("appConfig/main: public guest can read, normal/payments-only user cannot write, owner can write", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("appConfig").doc("main").set(validAppConfig());
  });

  const guestDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(guestDb.collection("appConfig").doc("main").get());
  // Listing appConfig collection is blocked for guests
  await assertFails(guestDb.collection("appConfig").get());
  await assertFails(
    guestDb.collection("appConfig").doc("main").update({ dailyAdPriceInr: 99 })
  );

  // Spoofed unverified owner email must fail
  const spoofDb = testEnv
    .authenticatedContext("spoof", {
      email: "gaganteja25042003@gmail.com",
      email_verified: false,
    })
    .firestore();
  await assertFails(
    spoofDb.collection("appConfig").doc("main").set(validAppConfig())
  );

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, OWNER_CLAIMS).firestore();
  await assertSucceeds(
    ownerDb.collection("appConfig").doc("main").set(validAppConfig())
  );
});

test("serverSecrets/payments: write-only for owner/config admin; nobody can read plaintext secrets back", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, OWNER_CLAIMS).firestore();
  await assertSucceeds(
    ownerDb.collection("serverSecrets").doc("payments").set({
      paymentProvider: "razorpay",
      razorpayKeyId: "rzp_test_123",
      razorpayKeySecret: "super_secret_do_not_leak",
      updatedBy: OWNER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Even owner cannot read plaintext secret back via client SDK
  await assertFails(ownerDb.collection("serverSecrets").doc("payments").get());

  const guestDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(guestDb.collection("serverSecrets").doc("payments").get());
});

test("RBAC admins: ONLY owner can create/delete admins; full-control admin CANNOT create admins", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, OWNER_CLAIMS).firestore();
  const past = new Date(Date.now() - 2000);

  // Owner creates payments-only, feedback-only, and full-control admins
  await assertSucceeds(
    ownerDb.collection("admins").doc(PAYMENTS_ADMIN_UID).set({
      uid: PAYMENTS_ADMIN_UID,
      username: "payops",
      email: "payops@pinpoint.local",
      canViewPayments: true,
      canViewFeedback: false,
      canManageConfig: false,
      canManageBusinesses: false,
      fullControl: false,
      active: true,
      createdBy: OWNER_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    ownerDb.collection("admins").doc(FULL_CONTROL_ADMIN_UID).set({
      uid: FULL_CONTROL_ADMIN_UID,
      username: "superops",
      email: "superops@pinpoint.local",
      canViewPayments: true,
      canViewFeedback: true,
      canManageConfig: true,
      canManageBusinesses: true,
      fullControl: true,
      active: true,
      createdBy: OWNER_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Full-control admin can edit appConfig/main, but CANNOT create/list/delete admins
  const fullAdminDb = testEnv.authenticatedContext(FULL_CONTROL_ADMIN_UID).firestore();
  await assertSucceeds(
    fullAdminDb.collection("appConfig").doc("main").set(validAppConfig())
  );
  await assertFails(fullAdminDb.collection("admins").get());
  await assertFails(
    fullAdminDb.collection("admins").doc("rogue_admin").set({
      uid: "rogue_admin",
      username: "rogue",
      email: "rogue@pinpoint.local",
      canViewPayments: true,
      canViewFeedback: true,
      canManageConfig: true,
      canManageBusinesses: true,
      fullControl: true,
      active: true,
      createdBy: FULL_CONTROL_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Payments-only admin can read payments, CANNOT read feedback or modify config
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("feedback").doc("fb_1").set({
      senderSessionId: "sess_1",
      senderName: "Guest",
      category: "GENERAL",
      message: "Great app!",
      status: "OPEN",
      createdAt: past,
      updatedAt: past,
    });
    await ctx.firestore().collection("payments").doc("tx_1").set({
      businessUid: BIZ_A_UID,
      businessName: "Cafe Deccan",
      campaignId: "cmp_1",
      amountInr: 90,
      durationDays: 30,
      expiryDateMs: Date.now() + 86400000,
      provider: "razorpay",
      status: "PAID",
      note: "Verified",
      createdAt: past,
      updatedAt: past,
    });
  });

  const payAdminDb = testEnv.authenticatedContext(PAYMENTS_ADMIN_UID).firestore();
  await assertSucceeds(payAdminDb.collection("payments").get());
  await assertFails(payAdminDb.collection("feedback").get());
  await assertFails(
    payAdminDb.collection("appConfig").doc("main").set(validAppConfig())
  );
});

test("Business accounts: must register PENDING, cannot self-approve, unapproved cannot create promotion", async () => {
  const bizADb = testEnv.authenticatedContext(BIZ_A_UID).firestore();
  const bizBDb = testEnv.authenticatedContext(BIZ_B_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, OWNER_CLAIMS).firestore();
  const past = new Date(Date.now() - 2000);

  // Attempting to register pre-approved fails
  await assertFails(
    bizADb.collection("businesses").doc(BIZ_A_UID).set(validBusiness(BIZ_A_UID, "APPROVED", past))
  );

  // Valid PENDING registration succeeds
  await assertSucceeds(
    bizADb.collection("businesses").doc(BIZ_A_UID).set(validBusiness(BIZ_A_UID, "PENDING", past))
  );

  // Cross-user read is blocked (PII isolation)
  await assertFails(bizBDb.collection("businesses").doc(BIZ_A_UID).get());

  // Business self-approval attempt fails
  await assertFails(
    bizADb.collection("businesses").doc(BIZ_A_UID).update({
      approvalStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unapproved business CANNOT create promotion
  const promoPayload = {
    businessUid: BIZ_A_UID,
    businessName: "Cafe Deccan",
    category: "Restaurants",
    description: "Artisanal coffee and snacks near the stadium",
    website: "https://cafedeccan.example",
    latitude: 17.4483,
    longitude: 78.3915,
    radiusKm: 5,
    durationDays: 30,
    dailyRateInr: 3,
    priceInr: 90,
    status: "PENDING_PAYMENT",
    expiresAtMs: 0,
    createdAt: past,
    updatedAt: past,
  };
  await assertFails(bizADb.collection("promotions").doc("cmp_1").set(promoPayload));

  // Owner approves business
  await assertSucceeds(
    ownerDb.collection("businesses").doc(BIZ_A_UID).update({
      approvalStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Approved business can now create PENDING_PAYMENT promo, but NOT pre-ACTIVE promo
  await assertFails(
    bizADb.collection("promotions").doc("cmp_1").set({ ...promoPayload, status: "ACTIVE" })
  );
  await assertSucceeds(
    bizADb.collection("promotions").doc("cmp_1").set(promoPayload)
  );

  // Business queries its own promotions & payments cleanly
  await assertSucceeds(
    bizADb.collection("promotions").where("businessUid", "==", BIZ_A_UID).get()
  );
  await assertFails(bizBDb.collection("promotions").where("businessUid", "==", BIZ_A_UID).get());

  // Business can create PENDING_ORDER or AWAITING_CONFIG payment, CANNOT forge PAID payment
  const basePayment = {
    businessUid: BIZ_A_UID,
    businessName: "Cafe Deccan",
    campaignId: "cmp_1",
    amountInr: 90,
    durationDays: 30,
    expiryDateMs: 0,
    provider: "razorpay",
    status: "PENDING_ORDER",
    note: "Order initiated",
    createdAt: past,
    updatedAt: past,
  };
  await assertFails(
    bizADb.collection("payments").doc("tx_forged").set({ ...basePayment, status: "PAID" })
  );
  await assertSucceeds(
    bizADb.collection("payments").doc("tx_valid").set(basePayment)
  );
  await assertSucceeds(
    bizADb.collection("payments").where("businessUid", "==", BIZ_A_UID).get()
  );
  await assertFails(bizBDb.collection("payments").where("businessUid", "==", BIZ_A_UID).get());

  // Guest normal user can query ACTIVE promotions only
  await assertFails(testEnv.unauthenticatedContext().firestore().collection("promotions").get());
  await assertSucceeds(
    testEnv.unauthenticatedContext().firestore().collection("promotions").where("status", "==", "ACTIVE").get()
  );

  // Business cannot delete its own record, but owner can
  await assertFails(bizADb.collection("businesses").doc(BIZ_A_UID).delete());
  await assertSucceeds(ownerDb.collection("businesses").doc(BIZ_A_UID).delete());
});

test("Feedback: app guests can create OPEN feedback, cannot read feedback; feedback admin can read/update", async () => {
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, OWNER_CLAIMS).firestore();
  const past = new Date(Date.now() - 1000);

  await assertSucceeds(
    ownerDb.collection("admins").doc(FEEDBACK_ADMIN_UID).set({
      uid: FEEDBACK_ADMIN_UID,
      username: "fb_mod",
      email: "fb@pinpoint.local",
      canViewPayments: false,
      canViewFeedback: true,
      canManageConfig: false,
      canManageBusinesses: false,
      fullControl: false,
      active: true,
      createdBy: OWNER_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    guestDb.collection("feedback").doc("fb_guest_1").set({
      senderSessionId: "guest_123",
      senderName: "Guest Traveller",
      category: "FEATURE",
      message: "Please add dark mode map tiles!",
      contactEmail: "user@example.com",
      appVersion: "1.0",
      status: "OPEN",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Guest cannot list or read feedback
  await assertFails(guestDb.collection("feedback").get());

  // Feedback admin can read and update status, cannot delete business or touch payments
  const fbAdminDb = testEnv.authenticatedContext(FEEDBACK_ADMIN_UID).firestore();
  await assertSucceeds(fbAdminDb.collection("feedback").get());
  await assertSucceeds(
    fbAdminDb.collection("feedback").doc("fb_guest_1").update({
      status: "REVIEWED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(fbAdminDb.collection("payments").get());
});
