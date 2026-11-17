package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.domain.TokenAndQrUtils
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class RuntimeAppConfig(
    val appName: String = "PINPOINT",
    val tagline: String = "Live crowd coordination & local discovery",
    val supportEmail: String = "breakfreeentertainments0@gmail.com",
    val mapProvider: String = "openstreetmap",
    val mapTileUrl: String = DEFAULT_OSM_TILE_URL,
    val mapAttribution: String = DEFAULT_OSM_ATTRIBUTION,
    val mapApiKey: String = "",
    val paymentProvider: String = "none",
    val razorpayKeyId: String = "",
    val paymentSecretConfigured: Boolean = false,
    val dailyAdPriceInr: Int = 3,
    val syncServerUrl: String = "",
    val enableChat: Boolean = true,
    val enableNearbyPlaces: Boolean = true,
    val enableBusinessAds: Boolean = true,
    val loadedFromRemote: Boolean = false
) {
    companion object {
        const val DEFAULT_OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val DEFAULT_OSM_ATTRIBUTION = "© OpenStreetMap contributors"
        val FALLBACK = RuntimeAppConfig()

        fun fromSnapshot(doc: DocumentSnapshot): RuntimeAppConfig {
            if (!doc.exists()) return FALLBACK
            val data = doc.data ?: return FALLBACK
            val rawPrice = (data["dailyAdPriceInr"] as? Number)?.toInt() ?: 3
            val rawProvider = (data["mapProvider"] as? String).orEmpty().ifBlank { "openstreetmap" }
            val rawTileUrl = (data["mapTileUrl"] as? String).orEmpty().ifBlank { DEFAULT_OSM_TILE_URL }
            val rawAttribution = (data["mapAttribution"] as? String).orEmpty().ifBlank { DEFAULT_OSM_ATTRIBUTION }
            val rawPaymentProvider = (data["paymentProvider"] as? String).orEmpty().ifBlank { "none" }
            val rawKeyId = (data["razorpayKeyId"] as? String).orEmpty().trim()
            val effectiveTileUrl = if (rawProvider == "google_maps" && rawTileUrl == DEFAULT_OSM_TILE_URL) {
                DEFAULT_OSM_TILE_URL
            } else {
                rawTileUrl
            }
            return RuntimeAppConfig(
                appName = (data["appName"] as? String).orEmpty().ifBlank { "PINPOINT" },
                tagline = (data["tagline"] as? String).orEmpty().ifBlank { FALLBACK.tagline },
                supportEmail = (data["supportEmail"] as? String).orEmpty().ifBlank { FALLBACK.supportEmail },
                mapProvider = rawProvider,
                mapTileUrl = effectiveTileUrl,
                mapAttribution = rawAttribution,
                mapApiKey = (data["mapApiKey"] as? String).orEmpty().trim(),
                paymentProvider = rawPaymentProvider,
                razorpayKeyId = rawKeyId,
                paymentSecretConfigured = (data["paymentSecretConfigured"] as? Boolean) ?: false,
                dailyAdPriceInr = rawPrice.coerceIn(1, 10000),
                syncServerUrl = (data["syncServerUrl"] as? String).orEmpty().trim(),
                enableChat = (data["enableChat"] as? Boolean) ?: true,
                enableNearbyPlaces = (data["enableNearbyPlaces"] as? Boolean) ?: true,
                enableBusinessAds = (data["enableBusinessAds"] as? Boolean) ?: true,
                loadedFromRemote = true
            )
        }
    }
}

enum class BusinessApprovalStatus {
    PENDING, APPROVED, SUSPENDED, REJECTED;

    companion object {
        fun safeParse(raw: String?): BusinessApprovalStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreBusinessAccount(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val uid: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val category: String = "Restaurants",
    val description: String = "",
    val website: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val addressLabel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val approvalStatus: String = "PENDING",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val approvalEnum: BusinessApprovalStatus
        get() = BusinessApprovalStatus.safeParse(approvalStatus)
}

@Keep
@IgnoreExtraProperties
data class FirestorePromotionCampaign(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val businessUid: String = "",
    val businessName: String = "",
    val category: String = "",
    val description: String = "",
    val website: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusKm: Long = 5L,
    val durationDays: Long = 30L,
    val dailyRateInr: Long = 3L,
    val priceInr: Long = 90L,
    val status: String = "PENDING_PAYMENT",
    val razorpayOrderId: String = "",
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun remainingDays(nowMs: Long = System.currentTimeMillis()): Int {
        if (expiresAtMs <= nowMs) return 0
        val diff = expiresAtMs - nowMs
        return ((diff + 86_400_000L - 1L) / 86_400_000L).toInt().coerceAtLeast(1)
    }
}

@Keep
@IgnoreExtraProperties
data class FirestorePaymentRecord(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val businessUid: String = "",
    val businessName: String = "",
    val businessEmail: String = "",
    val campaignId: String = "",
    val amountInr: Long = 0L,
    val durationDays: Long = 0L,
    val expiryDateMs: Long = 0L,
    val provider: String = "razorpay",
    val orderId: String = "",
    val paymentId: String = "",
    val status: String = "PENDING_ORDER",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirebaseRemoteDataSource(
    private val context: Context,
    dbOverride: FirebaseFirestore? = null,
    authOverride: FirebaseAuth? = null
) {
    private val db: FirebaseFirestore by lazy {
        dbOverride ?: FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    }
    private val auth: FirebaseAuth by lazy {
        authOverride ?: Firebase.auth
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Business account sign-in with Google is required.")
    }

    fun observeAuthUser(): Flow<FirebaseUser?> = flow {
        emit(auth.currentUser)
        val channel = kotlinx.coroutines.channels.Channel<FirebaseUser?>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            channel.trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        try {
            for (user in channel) {
                emit(user)
            }
        } finally {
            auth.removeAuthStateListener(listener)
        }
    }

    fun observeAppConfig(): Flow<RuntimeAppConfig> = flow {
        val path = "appConfig/main"
        emitAll(
            db.collection("appConfig").document("main")
                .snapshots()
                .map { snapshot -> RuntimeAppConfig.fromSnapshot(snapshot) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    // Graceful fallback to safe built-in defaults if Firebase is unreachable
                    emit(RuntimeAppConfig.FALLBACK)
                }
        )
    }

    suspend fun fetchAppConfigOnce(): RuntimeAppConfig {
        val path = "appConfig/main"
        return try {
            val snap = db.collection("appConfig").document("main").get().await()
            RuntimeAppConfig.fromSnapshot(snap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            RuntimeAppConfig.FALLBACK
        }
    }

    suspend fun fetchActivePublicPromotions(): List<FirestorePromotionCampaign> {
        val path = "promotions"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
            snapshot.toObjects(
                FirestorePromotionCampaign::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    suspend fun submitFeedback(
        senderSessionId: String,
        senderName: String,
        category: String,
        message: String,
        contactEmail: String
    ): Result<Unit> {
        val cleanMsg = message.trim().take(1000)
        if (cleanMsg.length < 3) {
            return Result.failure(IllegalArgumentException("Feedback message must be at least 3 characters."))
        }
        val safeCategory = if (category in listOf("BUG", "FEATURE", "SAFETY", "GENERAL")) category else "GENERAL"
        val docId = TokenAndQrUtils.generateUniqueId("fb")
        val path = "feedback/$docId"
        return try {
            val payload = mapOf<String, Any?>(
                "senderSessionId" to senderSessionId.take(128).ifBlank { "guest_app" },
                "senderName" to senderName.trim().take(64).ifBlank { "Guest Traveller" },
                "category" to safeCategory,
                "message" to cleanMsg,
                "contactEmail" to contactEmail.trim().take(120).takeIf { it.isNotEmpty() },
                "appVersion" to "1.0",
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("feedback").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeBusinessAccount(uid: String): Flow<FirestoreBusinessAccount?> = flow {
        if (uid.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "businesses/$uid"
        emitAll(
            db.collection("businesses").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        FirestoreBusinessAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeBusinessPromotions(uid: String): Flow<List<FirestorePromotionCampaign>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "promotions"
        emitAll(
            db.collection(path)
                .whereEqualTo("businessUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestorePromotionCampaign::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBusinessPayments(uid: String): Flow<List<FirestorePaymentRecord>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "payments"
        emitAll(
            db.collection(path)
                .whereEqualTo("businessUid", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestorePaymentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveBusinessAccount(
        name: String,
        category: String,
        description: String,
        website: String,
        phone: String,
        contactEmail: String,
        addressLabel: String,
        latitude: Double,
        longitude: Double
    ): Result<FirestoreBusinessAccount> {
        val cleanName = name.trim().take(100)
        val cleanDesc = description.trim().take(500)
        val cleanEmail = contactEmail.trim().take(120)
        if (cleanName.length < 2 || cleanDesc.length < 5 || cleanEmail.length < 3) {
            return Result.failure(
                IllegalArgumentException("Business name (min 2 chars), description (min 5 chars), and email are required.")
            )
        }
        var path = "businesses/unauthenticated"
        return try {
            val uid = requireUserId()
            val ownerEmail = auth.currentUser?.email?.take(120)?.ifBlank { cleanEmail } ?: cleanEmail
            path = "businesses/$uid"
            val docRef = db.collection("businesses").document(uid)
            val existing = docRef.get().await()

            val cleanWebsite = website.trim().take(200).takeIf { it.isNotEmpty() }
            val cleanPhone = phone.trim().take(30).takeIf { it.isNotEmpty() }
            val cleanAddress = addressLabel.trim().take(150).ifBlank { "India" }
            val clampedLat = latitude.coerceIn(-90.0, 90.0)
            val clampedLng = longitude.coerceIn(-180.0, 180.0)

            if (!existing.exists()) {
                val payload = mapOf<String, Any?>(
                    "uid" to uid,
                    "ownerEmail" to ownerEmail,
                    "name" to cleanName,
                    "category" to category.take(40),
                    "description" to cleanDesc,
                    "website" to cleanWebsite,
                    "contactPhone" to cleanPhone,
                    "contactEmail" to cleanEmail,
                    "addressLabel" to cleanAddress,
                    "latitude" to clampedLat,
                    "longitude" to clampedLng,
                    "approvalStatus" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "category" to category.take(40),
                    "description" to cleanDesc,
                    "website" to (cleanWebsite ?: ""),
                    "contactPhone" to (cleanPhone ?: ""),
                    "contactEmail" to cleanEmail,
                    "addressLabel" to cleanAddress,
                    "latitude" to clampedLat,
                    "longitude" to clampedLng,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            val refreshed = docRef.get().await().toObject(
                FirestoreBusinessAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: FirestoreBusinessAccount(
                id = uid,
                uid = uid,
                ownerEmail = ownerEmail,
                name = cleanName,
                category = category,
                description = cleanDesc,
                website = cleanWebsite.orEmpty(),
                contactPhone = cleanPhone.orEmpty(),
                contactEmail = cleanEmail,
                addressLabel = cleanAddress,
                latitude = clampedLat,
                longitude = clampedLng,
                approvalStatus = "PENDING"
            )
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createBusinessPromotion(
        business: FirestoreBusinessAccount,
        radiusKm: Int,
        durationDays: Int,
        dailyRateInr: Int
    ): Result<FirestorePromotionCampaign> {
        if (business.approvalEnum != BusinessApprovalStatus.APPROVED) {
            return Result.failure(
                IllegalStateException("Your business account is currently ${business.approvalStatus}. Wait for owner approval before publishing ads.")
            )
        }
        val campaignId = TokenAndQrUtils.generateUniqueId("cmp")
        val path = "promotions/$campaignId"
        return try {
            val uid = requireUserId()
            val clampedRate = dailyRateInr.coerceIn(1, 10000)
            val totalInr = durationDays * clampedRate
            val payload = mapOf<String, Any?>(
                "businessUid" to uid,
                "businessName" to business.name.take(100),
                "category" to business.category.take(40),
                "description" to business.description.take(500),
                "website" to business.website.take(200).takeIf { it.isNotBlank() },
                "latitude" to business.latitude.coerceIn(-90.0, 90.0),
                "longitude" to business.longitude.coerceIn(-180.0, 180.0),
                "radiusKm" to radiusKm.coerceIn(1, 50),
                "durationDays" to durationDays.coerceIn(7, 365),
                "dailyRateInr" to clampedRate,
                "priceInr" to totalInr,
                "status" to "PENDING_PAYMENT",
                "expiresAtMs" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("promotions").document(campaignId).set(payload).await()
            val created = FirestorePromotionCampaign(
                id = campaignId,
                businessUid = uid,
                businessName = business.name,
                category = business.category,
                description = business.description,
                website = business.website,
                latitude = business.latitude,
                longitude = business.longitude,
                radiusKm = radiusKm.toLong(),
                durationDays = durationDays.toLong(),
                dailyRateInr = clampedRate.toLong(),
                priceInr = totalInr.toLong(),
                status = "PENDING_PAYMENT",
                expiresAtMs = 0L
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordBusinessPaymentAttempt(
        business: FirestoreBusinessAccount,
        campaign: FirestorePromotionCampaign,
        provider: String,
        orderId: String?,
        status: String,
        note: String
    ): Result<Unit> {
        val txId = TokenAndQrUtils.generateUniqueId("tx")
        val path = "payments/$txId"
        return try {
            val uid = requireUserId()
            val payload = mapOf<String, Any?>(
                "businessUid" to uid,
                "businessName" to business.name.take(100),
                "businessEmail" to business.contactEmail.take(120).takeIf { it.isNotBlank() },
                "campaignId" to campaign.id.ifBlank { "cmp_unknown" },
                "amountInr" to campaign.priceInr.toInt(),
                "durationDays" to campaign.durationDays.toInt(),
                "expiryDateMs" to campaign.expiresAtMs,
                "provider" to provider.take(40).ifBlank { "razorpay" },
                "orderId" to orderId?.take(120)?.takeIf { it.isNotBlank() },
                "status" to status,
                "note" to note.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("payments").document(txId).set(payload).await()
            if (!orderId.isNullOrBlank() && campaign.id.isNotBlank()) {
                db.collection("promotions").document(campaign.id)
                    .update(
                        mapOf(
                            "razorpayOrderId" to orderId,
                            "status" to "PENDING_PAYMENT",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Google Sign-In via Jetpack Credential Manager ---

    fun attemptAutoSignIn(
        activityContext: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            activityContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activityContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        activityContext: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            activityContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        scope.launch {
            try {
                val activity = activityContext as? Activity
                    ?: throw IllegalStateException("Google Sign-In requires an Activity context")
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    fun signOutBusiness(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onBeforeSignOut: () -> Unit = {},
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                onBeforeSignOut()
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
