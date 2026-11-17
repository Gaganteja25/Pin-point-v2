import { initializeApp, deleteApp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  signOut
} from "https://www.gstatic.com/firebasejs/11.6.1/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  query,
  where,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.6.1/firebase-firestore.js";

// Provisioned Firebase Web Configuration (AI Studio Project: gen-lang-client-0954821327)
const FIREBASE_CONFIG = {
  apiKey: "AIzaSyDrOAcjCW4KueS4qXfh1321RjJfF_AJGFQ",
  authDomain: "gen-lang-client-0954821327.firebaseapp.com",
  projectId: "gen-lang-client-0954821327",
  storageBucket: "gen-lang-client-0954821327.firebasestorage.app",
  messagingSenderId: "428630167982",
  appId: "1:428630167982:android:f0df3e6b338e5005df783f"
};

const FIRESTORE_DATABASE_ID = "ai-studio-android-apkdirec-94ade64d-cd60-4368-abc6-2261d83b2f94";
const OWNER_EMAIL = "gaganteja25042003@gmail.com";

const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DATABASE_ID);

const state = {
  user: null,
  isOwner: false,
  adminDoc: null,
  permissions: {
    config: false,
    businesses: false,
    payments: false,
    feedback: false,
    admins: false
  },
  businesses: [],
  promotions: [],
  payments: [],
  feedback: [],
  admins: []
};

const el = (id) => document.getElementById(id);

function showToast(msg) {
  const banner = el("toast-banner");
  banner.textContent = msg;
  banner.classList.remove("hidden");
  setTimeout(() => banner.classList.add("hidden"), 4000);
}

function showLoginError(msg) {
  const box = el("login-error");
  box.textContent = msg;
  box.classList.remove("hidden");
}

function clearLoginError() {
  el("login-error").classList.add("hidden");
}

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatDate(msOrTs) {
  if (!msOrTs) return "—";
  const ms = typeof msOrTs === "number"
    ? msOrTs
    : (msOrTs.seconds ? msOrTs.seconds * 1000 : 0);
  if (!ms) return "—";
  return new Date(ms).toLocaleString();
}

// --- Auth Handlers ---
el("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  clearLoginError();
  const rawUser = el("login-username").value.trim();
  const password = el("login-password").value;
  let email = rawUser;
  if (rawUser.toLowerCase() === "owner") {
    email = OWNER_EMAIL;
  } else if (!rawUser.includes("@")) {
    email = `${rawUser.toLowerCase()}@pinpoint-admin.local`;
  }
  try {
    await signInWithEmailAndPassword(auth, email, password);
  } catch (err) {
    showLoginError(
      err.message || "Sign-in failed. Ensure Email/Password Auth is enabled in Firebase Console or sign in with Google."
    );
  }
});

el("google-login-btn").addEventListener("click", async () => {
  clearLoginError();
  try {
    await signInWithPopup(auth, new GoogleAuthProvider());
  } catch (err) {
    showLoginError(err.message || "Google Sign-In failed.");
  }
});

el("logout-btn").addEventListener("click", async () => {
  await signOut(auth);
});

el("refresh-all-btn").addEventListener("click", () => loadPermittedData());

onAuthStateChanged(auth, async (user) => {
  state.user = user;
  if (!user) {
    el("login-view").classList.remove("hidden");
    el("dashboard-view").classList.add("hidden");
    return;
  }

  el("login-view").classList.add("hidden");
  el("dashboard-view").classList.remove("hidden");
  el("signed-in-email").textContent = user.email || user.uid;

  await resolvePermissions(user);
  renderNavigationAndPanels();
  await loadPermittedData();
});

async function resolvePermissions(user) {
  const isOwner = Boolean(
    user.emailVerified &&
    user.email &&
    user.email.toLowerCase() === OWNER_EMAIL.toLowerCase()
  );
  state.isOwner = isOwner;
  state.adminDoc = null;

  if (isOwner) {
    state.permissions = {
      config: true,
      businesses: true,
      payments: true,
      feedback: true,
      admins: true
    };
    return;
  }

  try {
    const snap = await getDoc(doc(db, "admins", user.uid));
    if (snap.exists() && snap.data().active === true) {
      const d = snap.data();
      state.adminDoc = d;
      const full = Boolean(d.fullControl);
      state.permissions = {
        config: full || Boolean(d.canManageConfig),
        businesses: full || Boolean(d.canManageBusinesses),
        payments: full || Boolean(d.canViewPayments),
        feedback: full || Boolean(d.canViewFeedback),
        admins: false // ONLY Owner can create/delete admins
      };
      return;
    }
  } catch (err) {
    console.warn("Admin lookup failed:", err);
  }

  state.permissions = {
    config: false,
    businesses: false,
    payments: false,
    feedback: false,
    admins: false
  };
}

function renderNavigationAndPanels() {
  const p = state.permissions;
  const roleRow = el("role-badge-row");
  roleRow.innerHTML = "";

  if (state.isOwner) {
    roleRow.innerHTML = `<span class="badge badge-owner">OWNER (Full Access + Admin Control)</span>`;
  } else if (state.adminDoc) {
    const tags = [];
    if (state.adminDoc.fullControl) tags.push("Full Control");
    if (p.config) tags.push("App Config");
    if (p.businesses) tags.push("Businesses");
    if (p.payments) tags.push("Payments");
    if (p.feedback) tags.push("Feedback");
    roleRow.innerHTML = `<span class="badge badge-live">Admin: ${escapeHtml(state.adminDoc.username)}</span>` +
      tags.map((t) => `<span class="badge badge-owner">${escapeHtml(t)}</span>`).join("");
  } else {
    roleRow.innerHTML = `<span class="badge badge-danger">Unauthorized</span>`;
  }

  const sections = [
    { key: "config", id: "section-config", label: "App Settings" },
    { key: "businesses", id: "section-businesses", label: "Businesses & Ads" },
    { key: "payments", id: "section-payments", label: "Payments" },
    { key: "feedback", id: "section-feedback", label: "Feedback" },
    { key: "admins", id: "section-admins", label: "Admin Accounts (Owner)" }
  ];

  const allowed = sections.filter((s) => p[s.key]);
  const nav = el("section-nav");
  nav.innerHTML = "";

  if (allowed.length === 0) {
    el("no-access-panel").classList.remove("hidden");
    sections.forEach((s) => el(s.id).classList.add("hidden"));
    return;
  }

  el("no-access-panel").classList.add("hidden");
  sections.forEach((s) => {
    if (p[s.key]) {
      el(s.id).classList.remove("hidden");
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "nav-pill";
      btn.textContent = s.label;
      btn.addEventListener("click", () => {
        el(s.id).scrollIntoView({ behavior: "smooth", block: "start" });
      });
      nav.appendChild(btn);
    } else {
      el(s.id).classList.add("hidden");
    }
  });
}

async function loadPermittedData() {
  const p = state.permissions;
  const tasks = [];
  if (p.config) tasks.push(loadAppConfig());
  if (p.businesses) tasks.push(loadBusinessesAndPromotions());
  if (p.payments) tasks.push(loadPayments());
  if (p.feedback) tasks.push(loadFeedback());
  if (p.admins && state.isOwner) tasks.push(loadAdmins());
  await Promise.all(tasks);
}

// --- Section 1: App Config ---
async function loadAppConfig() {
  try {
    const snap = await getDoc(doc(db, "appConfig", "main"));
    const d = snap.exists() ? snap.data() : {};
    el("cfg-appName").value = d.appName || "PINPOINT";
    el("cfg-tagline").value = d.tagline || "Live crowd coordination & local discovery";
    el("cfg-supportEmail").value = d.supportEmail || "breakfreeentertainments0@gmail.com";
    el("cfg-mapProvider").value = d.mapProvider || "openstreetmap";
    el("cfg-mapTileUrl").value = d.mapTileUrl || "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    el("cfg-mapAttribution").value = d.mapAttribution || "© OpenStreetMap contributors";
    el("cfg-mapApiKey").value = d.mapApiKey || "";
    el("cfg-paymentProvider").value = d.paymentProvider || "razorpay";
    el("cfg-razorpayKeyId").value = d.razorpayKeyId || "";
    el("cfg-razorpayKeySecret").value = ""; // Never populated in plain text
    el("cfg-dailyAdPriceInr").value = d.dailyAdPriceInr ?? 3;
    el("cfg-syncServerUrl").value = d.syncServerUrl || "";
    el("cfg-enableChat").checked = d.enableChat !== false;
    el("cfg-enableNearbyPlaces").checked = d.enableNearbyPlaces !== false;
    el("cfg-enableBusinessAds").checked = d.enableBusinessAds !== false;

    el("secret-status-label").textContent = d.paymentSecretConfigured
      ? `Secret saved securely (ending ••••${d.paymentSecretLast4 || "****"}; hidden for security)`
      : "Secret status: Not configured yet";
    el("config-last-saved").textContent = d.updatedAt
      ? `Last saved: ${formatDate(d.updatedAt)}`
      : "Using default config (click Save to publish to Firestore)";
  } catch (err) {
    showToast(`Failed to load appConfig: ${err.message}`);
  }
}

el("config-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!state.permissions.config) return;

  const newSecret = el("cfg-razorpayKeySecret").value.trim();
  const prevSnap = await getDoc(doc(db, "appConfig", "main")).catch(() => null);
  const prevData = prevSnap && prevSnap.exists() ? prevSnap.data() : {};
  const secretConfigured = Boolean(newSecret) || Boolean(prevData.paymentSecretConfigured);
  const secretLast4 = newSecret
    ? newSecret.slice(-4)
    : (prevData.paymentSecretLast4 || "");

  const payload = {
    appName: el("cfg-appName").value.trim() || "PINPOINT",
    tagline: el("cfg-tagline").value.trim(),
    supportEmail: el("cfg-supportEmail").value.trim(),
    mapProvider: el("cfg-mapProvider").value,
    mapTileUrl: el("cfg-mapTileUrl").value.trim() || "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    mapAttribution: el("cfg-mapAttribution").value.trim() || "© OpenStreetMap contributors",
    mapApiKey: el("cfg-mapApiKey").value.trim(),
    paymentProvider: el("cfg-paymentProvider").value,
    razorpayKeyId: el("cfg-razorpayKeyId").value.trim(),
    paymentSecretConfigured: secretConfigured,
    paymentSecretLast4: secretLast4,
    dailyAdPriceInr: Math.max(1, parseInt(el("cfg-dailyAdPriceInr").value, 10) || 3),
    syncServerUrl: el("cfg-syncServerUrl").value.trim(),
    enableChat: el("cfg-enableChat").checked,
    enableNearbyPlaces: el("cfg-enableNearbyPlaces").checked,
    enableBusinessAds: el("cfg-enableBusinessAds").checked,
    updatedBy: state.user.uid,
    updatedAt: serverTimestamp()
  };

  try {
    await setDoc(doc(db, "appConfig", "main"), payload, { merge: false });
    if (newSecret) {
      await setDoc(doc(db, "serverSecrets", "payments"), {
        paymentProvider: payload.paymentProvider,
        razorpayKeyId: payload.razorpayKeyId,
        razorpayKeySecret: newSecret,
        updatedBy: state.user.uid,
        updatedAt: serverTimestamp()
      });
      el("cfg-razorpayKeySecret").value = "";
    }
    showToast("Settings saved to Firebase! App reads changes on next startup.");
    await loadAppConfig();
  } catch (err) {
    showToast(`Save failed: ${err.message}`);
  }
});

// --- Section 2: Businesses & Ads ---
async function loadBusinessesAndPromotions() {
  try {
    const [bizSnap, promoSnap] = await Promise.all([
      getDocs(collection(db, "businesses")),
      getDocs(collection(db, "promotions"))
    ]);
    state.businesses = bizSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    state.promotions = promoSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    renderBusinesses();
    renderPromotions();
  } catch (err) {
    showToast(`Failed to load businesses: ${err.message}`);
  }
}

el("biz-filter").addEventListener("change", renderBusinesses);

function renderBusinesses() {
  const filter = el("biz-filter").value;
  const rows = state.businesses.filter((b) => filter === "ALL" || b.approvalStatus === filter);
  const wrap = el("businesses-list");
  if (rows.length === 0) {
    wrap.innerHTML = `<p class="muted">No businesses match the selected filter.</p>`;
    return;
  }

  wrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Business</th>
          <th>Owner Email</th>
          <th>Location / Category</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${rows.map((b) => `
          <tr>
            <td>
              <strong>${escapeHtml(b.name)}</strong><br/>
              <span class="muted">${escapeHtml(b.description)}</span>
            </td>
            <td>${escapeHtml(b.ownerEmail)}<br/><span class="muted">${escapeHtml(b.contactPhone || "")}</span></td>
            <td>${escapeHtml(b.category)}<br/><span class="muted">${escapeHtml(b.addressLabel)}</span></td>
            <td><span class="badge ${badgeClassForStatus(b.approvalStatus)}">${escapeHtml(b.approvalStatus)}</span></td>
            <td>
              <div class="action-row">
                ${b.approvalStatus !== "APPROVED" ? `<button class="btn btn-success btn-sm" data-biz-action="APPROVE" data-uid="${escapeHtml(b.id)}">Approve</button>` : ""}
                ${b.approvalStatus !== "SUSPENDED" ? `<button class="btn btn-warn btn-sm" data-biz-action="SUSPEND" data-uid="${escapeHtml(b.id)}">Suspend</button>` : ""}
                <button class="btn btn-danger btn-sm" data-biz-action="DELETE" data-uid="${escapeHtml(b.id)}">Delete</button>
              </div>
            </td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;

  wrap.querySelectorAll("[data-biz-action]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const action = btn.dataset.bizAction;
      const uid = btn.dataset.uid;
      try {
        if (action === "APPROVE") {
          await updateDoc(doc(db, "businesses", uid), {
            approvalStatus: "APPROVED",
            rejectionReason: "",
            updatedAt: serverTimestamp()
          });
          showToast("Business approved!");
        } else if (action === "SUSPEND") {
          const reason = prompt("Suspension reason (optional):", "Suspended by administrator") ?? "";
          await updateDoc(doc(db, "businesses", uid), {
            approvalStatus: "SUSPENDED",
            rejectionReason: reason,
            updatedAt: serverTimestamp()
          });
          // Also suspend any active ads for this business
          const batch = writeBatch(db);
          state.promotions.filter((p) => p.businessUid === uid).forEach((p) => {
            batch.update(doc(db, "promotions", p.id), {
              status: "SUSPENDED",
              updatedAt: serverTimestamp()
            });
          });
          await batch.commit();
          showToast("Business and its ads suspended.");
        } else if (action === "DELETE") {
          if (!confirm("Permanently delete this business account and all of its ads?")) return;
          const batch = writeBatch(db);
          state.promotions.filter((p) => p.businessUid === uid).forEach((p) => {
            batch.delete(doc(db, "promotions", p.id));
          });
          batch.delete(doc(db, "businesses", uid));
          await batch.commit();
          showToast("Business and its ads deleted.");
        }
        await loadBusinessesAndPromotions();
      } catch (err) {
        showToast(`Action failed: ${err.message}`);
      }
    });
  });
}

function renderPromotions() {
  const wrap = el("promotions-list");
  if (state.promotions.length === 0) {
    wrap.innerHTML = `<p class="muted">No promotion campaigns created yet.</p>`;
    return;
  }
  wrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Business</th>
          <th>Radius / Duration</th>
          <th>Price</th>
          <th>Status</th>
          <th>Expires</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${state.promotions.map((p) => `
          <tr>
            <td><strong>${escapeHtml(p.businessName)}</strong><br/><span class="muted">${escapeHtml(p.category)}</span></td>
            <td>${p.radiusKm} km • ${p.durationDays} days</td>
            <td>₹${p.priceInr}</td>
            <td><span class="badge ${badgeClassForStatus(p.status)}">${escapeHtml(p.status)}</span></td>
            <td>${p.expiresAtMs ? formatDate(p.expiresAtMs) : "—"}</td>
            <td>
              <div class="action-row">
                ${p.status !== "ACTIVE" ? `<button class="btn btn-success btn-sm" data-promo-action="ACTIVATE" data-id="${escapeHtml(p.id)}" data-days="${p.durationDays}">Activate</button>` : ""}
                ${p.status === "ACTIVE" ? `<button class="btn btn-warn btn-sm" data-promo-action="SUSPEND" data-id="${escapeHtml(p.id)}">Pause</button>` : ""}
                <button class="btn btn-danger btn-sm" data-promo-action="DELETE" data-id="${escapeHtml(p.id)}">Delete</button>
              </div>
            </td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;

  wrap.querySelectorAll("[data-promo-action]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const action = btn.dataset.promoAction;
      const id = btn.dataset.id;
      const days = parseInt(btn.dataset.days || "30", 10);
      try {
        if (action === "ACTIVATE") {
          const expiresAtMs = Date.now() + days * 86400000;
          await updateDoc(doc(db, "promotions", id), {
            status: "ACTIVE",
            expiresAtMs,
            updatedAt: serverTimestamp()
          });
          showToast("Campaign set to ACTIVE.");
        } else if (action === "SUSPEND") {
          await updateDoc(doc(db, "promotions", id), {
            status: "SUSPENDED",
            updatedAt: serverTimestamp()
          });
          showToast("Campaign suspended.");
        } else if (action === "DELETE") {
          if (!confirm("Delete this ad campaign?")) return;
          await deleteDoc(doc(db, "promotions", id));
          showToast("Campaign deleted.");
        }
        await loadBusinessesAndPromotions();
      } catch (err) {
        showToast(`Failed: ${err.message}`);
      }
    });
  });
}

// --- Section 3: Payments ---
async function loadPayments() {
  try {
    const snap = await getDocs(collection(db, "payments"));
    state.payments = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    renderPayments();
  } catch (err) {
    showToast(`Failed to load payments: ${err.message}`);
  }
}

el("payment-filter").addEventListener("change", renderPayments);

function renderPayments() {
  const filter = el("payment-filter").value;
  const rows = state.payments.filter((p) => filter === "ALL" || p.status === filter);

  const totalPaid = state.payments
    .filter((p) => p.status === "PAID")
    .reduce((acc, p) => acc + (p.amountInr || 0), 0);
  const failedCount = state.payments.filter((p) => p.status === "FAILED").length;
  const activeCount = state.payments.filter((p) => p.status === "PAID" && (p.expiryDateMs || 0) > Date.now()).length;

  el("payment-stats").innerHTML = `
    <div class="stat-box"><span class="muted">Verified Revenue</span><strong>₹${totalPaid}</strong></div>
    <div class="stat-box"><span class="muted">Active Paid Ads</span><strong>${activeCount}</strong></div>
    <div class="stat-box"><span class="muted">Failed Payments</span><strong>${failedCount}</strong></div>
    <div class="stat-box"><span class="muted">Total Records</span><strong>${state.payments.length}</strong></div>
  `;

  const wrap = el("payments-table");
  if (rows.length === 0) {
    wrap.innerHTML = `<p class="muted">No payment records match the selected filter.</p>`;
    return;
  }
  wrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Business</th>
          <th>Amount</th>
          <th>Days</th>
          <th>Expiry Date</th>
          <th>Provider / Order</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        ${rows.map((p) => `
          <tr>
            <td><strong>${escapeHtml(p.businessName)}</strong><br/><span class="muted">${escapeHtml(p.businessEmail || p.businessUid)}</span></td>
            <td>₹${p.amountInr}</td>
            <td>${p.durationDays}d</td>
            <td>${p.expiryDateMs ? formatDate(p.expiryDateMs) : "—"}</td>
            <td>${escapeHtml(p.provider)}<br/><span class="muted">${escapeHtml(p.orderId || p.id)}</span></td>
            <td>
              <span class="badge ${badgeClassForStatus(p.status)}">${escapeHtml(p.status)}</span>
              <div class="muted">${escapeHtml(p.note)}</div>
            </td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

// --- Section 4: User Feedback ---
async function loadFeedback() {
  try {
    const snap = await getDocs(collection(db, "feedback"));
    state.feedback = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    renderFeedback();
  } catch (err) {
    showToast(`Failed to load feedback: ${err.message}`);
  }
}

function renderFeedback() {
  const wrap = el("feedback-list");
  if (state.feedback.length === 0) {
    wrap.innerHTML = `<p class="muted">No user feedback or reports received yet.</p>`;
    return;
  }
  wrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Category</th>
          <th>Sender</th>
          <th>Message</th>
          <th>Date</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        ${state.feedback.map((f) => `
          <tr>
            <td><span class="badge badge-owner">${escapeHtml(f.category)}</span></td>
            <td>${escapeHtml(f.senderName)}<br/><span class="muted">${escapeHtml(f.contactEmail || f.senderSessionId)}</span></td>
            <td>${escapeHtml(f.message)}</td>
            <td>${formatDate(f.createdAt)}</td>
            <td>
              <select class="filter-select" data-fb-id="${escapeHtml(f.id)}">
                <option value="OPEN" ${f.status === "OPEN" ? "selected" : ""}>OPEN</option>
                <option value="REVIEWED" ${f.status === "REVIEWED" ? "selected" : ""}>REVIEWED</option>
                <option value="RESOLVED" ${f.status === "RESOLVED" ? "selected" : ""}>RESOLVED</option>
              </select>
            </td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;

  wrap.querySelectorAll("[data-fb-id]").forEach((sel) => {
    sel.addEventListener("change", async () => {
      try {
        await updateDoc(doc(db, "feedback", sel.dataset.fbId), {
          status: sel.value,
          updatedAt: serverTimestamp()
        });
        showToast("Feedback status updated.");
      } catch (err) {
        showToast(`Failed: ${err.message}`);
      }
    });
  });
}

// --- Section 5: OWNER ONLY Admin Account Creation & Deletion ---
async function loadAdmins() {
  if (!state.isOwner) return;
  try {
    const snap = await getDocs(collection(db, "admins"));
    state.admins = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    renderAdmins();
  } catch (err) {
    showToast(`Failed to load admins: ${err.message}`);
  }
}

function renderAdmins() {
  const wrap = el("admins-list");
  if (state.admins.length === 0) {
    wrap.innerHTML = `<p class="muted">No admins created yet. Use the form above to add role-based admins.</p>`;
    return;
  }
  wrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Username / Email</th>
          <th>Permissions</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${state.admins.map((a) => {
          const perms = [];
          if (a.fullControl) perms.push("Full Control");
          if (a.canViewPayments) perms.push("Payments");
          if (a.canViewFeedback) perms.push("Feedback");
          if (a.canManageBusinesses) perms.push("Businesses");
          if (a.canManageConfig) perms.push("Config");
          return `
            <tr>
              <td><strong>${escapeHtml(a.username)}</strong><br/><span class="muted">${escapeHtml(a.email)}</span></td>
              <td>${perms.map((p) => `<span class="badge badge-owner">${escapeHtml(p)}</span>`).join(" ") || "None"}</td>
              <td><span class="badge ${a.active ? "badge-live" : "badge-danger"}">${a.active ? "ACTIVE" : "DISABLED"}</span></td>
              <td>
                <div class="action-row">
                  <button class="btn btn-warn btn-sm" data-adm-toggle="${escapeHtml(a.id)}" data-active="${a.active}">${a.active ? "Disable" : "Enable"}</button>
                  <button class="btn btn-danger btn-sm" data-adm-delete="${escapeHtml(a.id)}">Delete</button>
                </div>
              </td>
            </tr>
          `;
        }).join("")}
      </tbody>
    </table>
  `;

  wrap.querySelectorAll("[data-adm-toggle]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const uid = btn.dataset.admToggle;
      const current = btn.dataset.active === "true";
      await updateDoc(doc(db, "admins", uid), { active: !current, updatedAt: serverTimestamp() });
      showToast(`Admin ${!current ? "enabled" : "disabled"}.`);
      await loadAdmins();
    });
  });

  wrap.querySelectorAll("[data-adm-delete]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (!confirm("Delete this admin account and immediately revoke all dashboard access?")) return;
      await deleteDoc(doc(db, "admins", btn.dataset.admDelete));
      showToast("Admin deleted.");
      await loadAdmins();
    });
  });
}

el("create-admin-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!state.isOwner) return;

  const username = el("adm-username").value.trim();
  const email = el("adm-email").value.trim();
  const password = el("adm-password").value;

  let secondaryApp = null;
  try {
    // Use secondary Firebase app instance so Owner remains logged in while creating Admin credentials
    secondaryApp = initializeApp(FIREBASE_CONFIG, `AdminCreator_${Date.now()}`);
    const secondaryAuth = getAuth(secondaryApp);
    const cred = await createUserWithEmailAndPassword(secondaryAuth, email, password);
    const newUid = cred.user.uid;
    await signOut(secondaryAuth);

    await setDoc(doc(db, "admins", newUid), {
      uid: newUid,
      username,
      email,
      canViewPayments: el("adm-canViewPayments").checked,
      canViewFeedback: el("adm-canViewFeedback").checked,
      canManageConfig: el("adm-canManageConfig").checked,
      canManageBusinesses: el("adm-canManageBusinesses").checked,
      fullControl: el("adm-fullControl").checked,
      active: true,
      createdBy: state.user.uid,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });

    el("create-admin-form").reset();
    showToast(`Admin '${username}' created! They can log in immediately with ${email}.`);
    await loadAdmins();
  } catch (err) {
    showToast(`Failed to create admin: ${err.message}`);
  } finally {
    if (secondaryApp) {
      await deleteApp(secondaryApp).catch(() => {});
    }
  }
});

function badgeClassForStatus(status) {
  if (status === "APPROVED" || status === "ACTIVE" || status === "PAID") return "badge-live";
  if (status === "PENDING" || status === "PENDING_PAYMENT" || status === "PENDING_ORDER" || status === "AWAITING_CONFIG") return "badge-pending";
  return "badge-danger";
}
