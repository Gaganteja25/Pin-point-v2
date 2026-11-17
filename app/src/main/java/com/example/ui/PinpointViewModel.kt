package com.example.ui

import android.app.Application
import android.content.Context
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
import com.example.data.remote.GatewayOrderResult
import com.example.data.remote.NearbyCategory
import com.example.data.repository.ActionResult
import com.example.data.repository.LocationSharingStatus
import com.example.data.repository.PinpointRepository
import com.example.domain.PlaceListing
import com.example.service.PinpointLocationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PinpointViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PinpointRepository = (application as PinpointApplication).repository

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

    fun saveBusinessProfile(
        name: String,
        category: String,
        description: String,
        website: String,
        phone: String,
        email: String,
        address: String,
        lat: Double,
        lng: Double,
        onSaved: (BusinessProfileEntity) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.saveBusinessProfile(
                name, category, description, website, phone, email, address, lat, lng
            )) {
                is ActionResult.Success -> {
                    _uiMessage.value = "Business profile saved."
                    onSaved(res.data)
                }
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun createPromotion(
        business: BusinessProfileEntity,
        radiusKm: Int,
        durationDays: Int,
        onCreated: (PromotionCampaignEntity) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.createDraftPromotion(business, radiusKm, durationDays)) {
                is ActionResult.Success -> onCreated(res.data)
                is ActionResult.Error -> _uiMessage.value = res.message
            }
        }
    }

    fun startRazorpayCheckout(campaignId: String, onCompleted: (GatewayOrderResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.initiateRazorpayCheckout(campaignId)
            _latestPaymentResult.value = result
            onCompleted(result)
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

    fun updateTileProvider(url: String, attribution: String) {
        viewModelScope.launch {
            repository.updateTileProvider(url, attribution)
            _uiMessage.value = "Map tile provider updated."
        }
    }

    fun updateBackendConfig(syncUrl: String, razorpayKeyId: String) {
        viewModelScope.launch {
            repository.updateBackendConfig(syncUrl, razorpayKeyId)
            _uiMessage.value = "Server & payment configuration saved."
        }
    }
}
