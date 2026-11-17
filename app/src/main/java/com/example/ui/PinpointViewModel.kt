package com.example.ui

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PinpointApplication
import com.example.data.local.AppNotificationEntity
import com.example.data.local.BoundaryEventEntity
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.data.local.GuestSessionState
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.PromotionCampaignEntity
import com.example.data.local.RefundRequestEntity
import com.example.data.remote.FirestoreBusinessAccount
import com.example.data.remote.FirestorePaymentRecord
import com.example.data.remote.FirestorePromotionCampaign
import com.example.data.remote.GatewayOrderResult
import com.example.data.remote.NearbyCategory
import com.example.data.remote.RuntimeAppConfig
import com.example.data.repository.ActionResult
import com.example.data.repository.LocationSharingStatus
import com.example.data.repository.PinpointRepository
import com.example.domain.PlaceListing
import com.example.service.PinpointLocationService
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BusinessUiState<out T> {
    data object Loading : BusinessUiState<Nothing>
    data class Success<T>(val data: T) : BusinessUiState<T>
    data class Error(val message: String) : BusinessUiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class PinpointViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PinpointRepository = (application as PinpointApplication).repository
    private val firebaseRemote = repository.firebaseRemote

    val appConfig: StateFlow<RuntimeAppConfig> = repository.appConfig
        .onEach { cfg -> repository.updateCachedAppConfig(cfg) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RuntimeAppConfig.FALLBACK)

    val guestSession: StateFlow<GuestSessionState?> = repository.guestSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeCrowd: StateFlow<CrowdEntity?> = repository.guestSession
        .flatMapLatest { session ->
            val crowdId = session.activeCrowdId
            if (crowdId == null) flowOf(null) else repository.observeCrowd(crowdId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<CrowdMemberEntity>> = repository.guestSession
        .flatMapLatest { session ->
            val crowdId = session.activeCrowdId
            if (crowdId == null) flowOf(emptyList()) else repository.observeMembers(crowdId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ChatMessageEntity>> = repository.guestSession
        .flatMapLatest { session ->
            val crowdId = session.activeCrowdId
            if (crowdId == null) flowOf(emptyList()) else repository.observeMessages(crowdId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val boundaryEvents: StateFlow<List<BoundaryEventEntity>> = repository.guestSession
        .flatMapLatest { session ->
            val crowdId = session.activeCrowdId
            if (crowdId == null) flowOf(emptyList()) else repository.observeBoundaryEvents(crowdId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AppNotificationEntity>> = repository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Firebase Auth user (scoped strictly to persistent Business Accounts)
    val businessAuthUser: StateFlow<FirebaseUser?> = (firebaseRemote?.observeAuthUser() ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), firebaseRemote?.currentUser())

    val remoteBusinessState: StateFlow<BusinessUiState<FirestoreBusinessAccount?>> = businessAuthUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrBlank()) {
                flowOf<BusinessUiState<FirestoreBusinessAccount?>>(BusinessUiState.Success(null))
            } else {
                repository.observeBusinessAccount(uid)
                    .map<FirestoreBusinessAccount?, BusinessUiState<FirestoreBusinessAccount?>> {
                        BusinessUiState.Success(it)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        emit(BusinessUiState.Error(rootError.message ?: "Failed to load business profile"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BusinessUiState.Loading)

    val remotePromotions: StateFlow<List<FirestorePromotionCampaign>> = businessAuthUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeBusinessPromotions(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        _uiMessage.value = (firestoreError ?: error).localizedMessage ?: "Failed to load promotions."
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remotePayments: StateFlow<List<FirestorePaymentRecord>> = businessAuthUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeBusinessPayments(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myBusiness: StateFlow<BusinessProfileEntity?> = repository.myBusiness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val campaigns: StateFlow<List<PromotionCampaignEntity>> = repository.allCampaigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<PaymentTransactionEntity>> = repository.paymentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val refundRequests: StateFlow<List<RefundRequestEntity>> = repository.refundRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _nearbyPlaces = MutableStateFlow<List<PlaceListing>>(emptyList())
    val nearbyPlaces: StateFlow<List<PlaceListing>> = _nearbyPlaces.asStateFlow()

    private val _selectedCategory = MutableStateFlow(NearbyCategory.ALL)
    val selectedCategory: StateFlow<NearbyCategory> = _selectedCategory.asStateFlow()

    private val _isLoadingNearby = MutableStateFlow(false)
    val isLoadingNearby: StateFlow<Boolean> = _isLoadingNearby.asStateFlow()

    private val _nearbyError = MutableStateFlow<String?>(null)
    val nearbyError: StateFlow<String?> = _nearbyError.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _pendingInviteToken = MutableStateFlow<String?>(null)
    val pendingInviteToken: StateFlow<String?> = _pendingInviteToken.asStateFlow()

    private val _isPickingGeofenceCenter = MutableStateFlow(false)
    val isPickingGeofenceCenter: StateFlow<Boolean> = _isPickingGeofenceCenter.asStateFlow()

    private val _latestPaymentResult = MutableStateFlow<GatewayOrderResult?>(null)
    val latestPaymentResult: StateFlow<GatewayOrderResult?> = _latestPaymentResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSession()
            repository.refreshAppConfigOnStartup()
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    fun setPendingInviteToken(token: String?) {
        _pendingInviteToken.value = token
    }

    fun setPickingGeofenceCenter(enabled: Boolean) {
        _isPickingGeofenceCenter.value = enabled
    }

    fun createCrowd(
        displayName: String,
        crowdName: String,
        onCreated: (CrowdEntity) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.createCrowd(displayName, crowdName)) {
                is ActionResult.Success -> onCreated(res.data)
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun lookupInvite(
        rawInput: String,
        onFound: (CrowdEntity) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.lookupInvite(rawInput)) {
                is ActionResult.Success -> onFound(res.data)
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun joinCrowd(
        rawInput: String,
        displayName: String,
        consentAccepted: Boolean,
        onJoined: (CrowdEntity) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.joinCrowdByToken(rawInput, displayName, consentAccepted)) {
                is ActionResult.Success -> {
                    _pendingInviteToken.value = null
                    onJoined(res.data)
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun updateProfile(displayName: String, emoji: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateProfile(displayName, emoji, colorHex)
            _uiMessage.value = "Profile updated."
        }
    }

    fun startLocationSharing(context: Context) {
        viewModelScope.launch {
            repository.setLocationStatus(LocationSharingStatus.CONNECTING, sharing = true)
            PinpointLocationService.start(context)
        }
    }

    fun stopLocationSharing(context: Context) {
        viewModelScope.launch {
            repository.stopLocationSharingManually()
            PinpointLocationService.stop(context)
            _uiMessage.value = "Location sharing stopped. Chat remains active."
        }
    }

    fun onPermissionDenied() {
        viewModelScope.launch {
            repository.setLocationStatus(LocationSharingStatus.PERMISSION_DENIED, sharing = false)
            _uiMessage.value = "Location permission denied. Enable permission in Settings to share live GPS."
        }
    }

    fun sendMessage(crowdId: String, text: String) {
        viewModelScope.launch {
            when (val res = repository.sendChatMessage(crowdId, text)) {
                is ActionResult.Error -> _uiMessage.value = res.message
                is ActionResult.Success -> Unit
            }
        }
    }

    fun reportMessage(crowdId: String, messageId: String, targetSessionId: String, reason: String) {
        viewModelScope.launch {
            repository.reportMessage(crowdId, messageId, targetSessionId, reason)
            _uiMessage.value = "Message reported for safety review."
        }
    }

    fun submitUserFeedback(category: String, message: String, contactEmail: String, onSubmitted: () -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.submitUserFeedback(category, message, contactEmail)) {
                is ActionResult.Success -> {
                    _uiMessage.value = "Feedback sent to PINPOINT team. Thank you!"
                    onSubmitted()
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun updateGeofence(crowdId: String, lat: Double, lng: Double, radiusMeters: Int) {
        viewModelScope.launch {
            when (val res = repository.updateGeofence(crowdId, lat, lng, radiusMeters)) {
                is ActionResult.Success -> {
                    _isPickingGeofenceCenter.value = false
                    _uiMessage.value = "Geofence updated."
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun setBoundaryAlertsOptIn(crowdId: String, optIn: Boolean) {
        viewModelScope.launch {
            repository.setBoundaryAlertsOptIn(crowdId, optIn)
        }
    }

    fun regenerateInviteLink(crowdId: String) {
        viewModelScope.launch {
            when (val res = repository.regenerateInviteLink(crowdId)) {
                is ActionResult.Success -> _uiMessage.value = "New invitation link generated. Previous link is now invalid."
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun revokeInviteLink(crowdId: String) {
        viewModelScope.launch {
            when (val res = repository.revokeInviteLink(crowdId)) {
                is ActionResult.Success -> _uiMessage.value = "Invitation link revoked."
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun leaveCrowd(crowdId: String, context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            PinpointLocationService.stop(context)
            repository.leaveCrowd(crowdId)
            onDone()
        }
    }

    fun deleteCrowd(crowdId: String, context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            PinpointLocationService.stop(context)
            when (val res = repository.deleteCrowdPermanently(crowdId)) {
                is ActionResult.Success -> {
                    _uiMessage.value = "Crowd permanently deleted and all crowd data removed."
                    onDone()
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    fun refreshNearbyPlaces(lat: Double, lng: Double, category: NearbyCategory = _selectedCategory.value) {
        _selectedCategory.value = category
        viewModelScope.launch {
            _isLoadingNearby.value = true
            _nearbyError.value = null
            val (places, err) = repository.fetchNearbyWithSponsored(lat, lng, category)
            _nearbyPlaces.value = places
            _nearbyError.value = err
            _isLoadingNearby.value = false
        }
    }

    // --- Business Auth & Persistent Firestore Operations ---

    fun attemptBusinessAutoSignIn(activityContext: Context) {
        val remote = firebaseRemote ?: return
        val cm = CredentialManager.create(activityContext)
        remote.attemptAutoSignIn(
            activityContext = activityContext,
            credentialManager = cm,
            onAuthSuccess = { },
            onUnauthenticated = { },
            scope = viewModelScope
        )
    }

    fun onBusinessGoogleSignIn(
        activityContext: Context,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val remote = firebaseRemote
        if (remote == null) {
            onError("Firebase unavailable.")
            return
        }
        val cm = CredentialManager.create(activityContext)
        remote.onGoogleSignInClicked(
            activityContext = activityContext,
            credentialManager = cm,
            onAuthSuccess = {
                _uiMessage.value = "Signed in to PINPOINT Business Portal."
                onComplete()
            },
            onAuthError = { err ->
                _uiMessage.value = err
                onError(err)
            },
            scope = viewModelScope,
            onAuthCancelled = { onComplete() }
        )
    }

    fun signOutBusiness(context: Context) {
        val remote = firebaseRemote ?: return
        val cm = CredentialManager.create(context)
        remote.signOutBusiness(
            credentialManager = cm,
            scope = viewModelScope,
            onBeforeSignOut = { _latestPaymentResult.value = null },
            onSignOutComplete = { _uiMessage.value = "Signed out of Business Portal. Your saved business data remains safe." }
        )
    }

    fun saveRemoteBusinessProfile(
        name: String,
        category: String,
        description: String,
        website: String,
        phone: String,
        email: String,
        address: String,
        lat: Double,
        lng: Double,
        onSaved: (FirestoreBusinessAccount) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val res = repository.saveFirebaseBusinessAccount(
                    name, category, description, website, phone, email, address, lat, lng
                )
            ) {
                is ActionResult.Success -> {
                    _uiMessage.value = if (res.data.approvalStatus == "APPROVED") {
                        "Business profile updated."
                    } else {
                        "Business registered! Status is PENDING owner review."
                    }
                    onSaved(res.data)
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun createRemotePromotion(
        business: FirestoreBusinessAccount,
        radiusKm: Int,
        durationDays: Int,
        onCreated: (FirestorePromotionCampaign) -> Unit
    ) {
        val dailyPrice = appConfig.value.dailyAdPriceInr
        viewModelScope.launch {
            when (val res = repository.createFirebasePromotion(business, radiusKm, durationDays, dailyPrice)) {
                is ActionResult.Success -> onCreated(res.data)
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun startRemoteCheckout(
        business: FirestoreBusinessAccount,
        campaign: FirestorePromotionCampaign
    ) {
        val config = appConfig.value
        viewModelScope.launch {
            val result = repository.initiateFirebaseCheckout(business, campaign, config)
            _latestPaymentResult.value = result
        }
    }

    fun submitRefundRequest(campaignId: String, transactionId: String, reason: String) {
        viewModelScope.launch {
            when (val res = repository.submitRefundRequest(campaignId, transactionId, reason)) {
                is ActionResult.Success -> _uiMessage.value = "Refund request submitted."
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }
}
