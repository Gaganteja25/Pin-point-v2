package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AbuseReportEntity
import com.example.data.local.AppNotificationEntity
import com.example.data.local.BoundaryEventEntity
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.data.local.GuestPreferencesDataStore
import com.example.data.local.GuestSessionState
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.PinpointDatabase
import com.example.data.local.PromotionCampaignEntity
import com.example.data.local.RefundRequestEntity
import com.example.data.remote.GatewayOrderResult
import com.example.data.remote.NearbyCategory
import com.example.data.remote.OverpassResult
import com.example.data.remote.OverpassService
import com.example.data.remote.PinpointBackendClient
import com.example.data.remote.RazorpayOrderRequest
import com.example.data.remote.RemoteLocationPush
import com.example.domain.AdPricingEngine
import com.example.domain.BoundaryEventType
import com.example.domain.BoundaryStatus
import com.example.domain.CampaignStatus
import com.example.domain.EligiblePromotionCandidate
import com.example.domain.GeofenceEngine
import com.example.domain.MemberMentionTarget
import com.example.domain.PlaceListing
import com.example.domain.SmoothedCoordinate
import com.example.domain.TokenAndQrUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

enum class LocationSharingStatus(val label: String) {
    LIVE("Live"),
    CONNECTING("Connecting"),
    UNAVAILABLE("Location unavailable"),
    PERMISSION_DENIED("Permission denied"),
    STOPPED("Sharing stopped")
}

sealed interface ActionResult<out T> {
    data class Success<T>(val data: T) : ActionResult<T>
    data class Error(val message: String) : ActionResult<Nothing>
}

class PinpointRepository(
    private val db: PinpointDatabase,
    private val preferences: GuestPreferencesDataStore,
    private val overpassService: OverpassService = OverpassService(),
    private val backendClient: PinpointBackendClient = PinpointBackendClient()
) {
    private val smoothedCoords = ConcurrentHashMap<String, SmoothedCoordinate>()
    private val lastBoundaryEventTimes = ConcurrentHashMap<String, Long>()

    val guestSession: Flow<GuestSessionState> = preferences.guestSessionFlow
    val notifications: Flow<List<AppNotificationEntity>> =
        db.boundaryAndNotificationDao().observeNotifications()
    val unreadNotificationsCount: Flow<Int> =
        db.boundaryAndNotificationDao().observeUnreadCount()
    val myBusiness: Flow<BusinessProfileEntity?> = db.businessDao().observeMyBusiness()
    val allCampaigns: Flow<List<PromotionCampaignEntity>> = db.businessDao().observeAllCampaigns()
    val activeCampaigns: Flow<List<PromotionCampaignEntity>> = db.businessDao().observeActiveCampaigns()
    val paymentTransactions: Flow<List<PaymentTransactionEntity>> = db.businessDao().observeTransactions()
    val refundRequests: Flow<List<RefundRequestEntity>> = db.businessDao().observeRefundRequests()

    suspend fun ensureSession(): GuestSessionState = preferences.ensureSessionInitialized()

    fun observeCrowd(crowdId: String): Flow<CrowdEntity?> = db.crowdDao().observeCrowd(crowdId)
    fun observeMembers(crowdId: String): Flow<List<CrowdMemberEntity>> = db.memberDao().observeMembers(crowdId)
    fun observeMessages(crowdId: String): Flow<List<ChatMessageEntity>> = db.chatDao().observeMessages(crowdId)
    fun observeBoundaryEvents(crowdId: String): Flow<List<BoundaryEventEntity>> =
        db.boundaryAndNotificationDao().observeBoundaryEvents(crowdId)

    suspend fun updateProfile(displayName: String, emoji: String, colorHex: String) {
        val session = ensureSession()
        val cleanName = displayName.trim().take(32)
        if (cleanName.isEmpty()) return
        preferences.updateProfile(cleanName, emoji, colorHex)
        db.memberDao().updateProfileAcrossCrowds(session.sessionId, cleanName, emoji, colorHex)
    }

    suspend fun createCrowd(
        displayName: String,
        crowdNameInput: String,
        initialLat: Double = 20.5937,
        initialLng: Double = 78.9629
    ): ActionResult<CrowdEntity> {
        val cleanDisplay = displayName.trim()
        if (cleanDisplay.isEmpty()) {
            return ActionResult.Error("Please enter your display name.")
        }
        val session = ensureSession()
        preferences.updateProfile(cleanDisplay, session.avatarEmoji, session.avatarColorHex)

        val crowdId = TokenAndQrUtils.generateCrowdId()
        val inviteToken = TokenAndQrUtils.generateInviteToken()
        val inviteUrl = TokenAndQrUtils.buildInviteUrl(inviteToken)
        val crowdName = crowdNameInput.trim().ifBlank { "$cleanDisplay's Crowd" }
        val now = System.currentTimeMillis()

        val crowd = CrowdEntity(
            crowdId = crowdId,
            name = crowdName,
            inviteToken = inviteToken,
            inviteUrl = inviteUrl,
            adminSessionId = session.sessionId,
            geofenceLat = initialLat,
            geofenceLng = initialLng,
            geofenceRadiusMeters = 500,
            geofenceUpdatedAt = now,
            createdAt = now
        )
        val adminMember = CrowdMemberEntity(
            id = "${crowdId}_${session.sessionId}",
            crowdId = crowdId,
            sessionId = session.sessionId,
            displayName = cleanDisplay,
            avatarEmoji = session.avatarEmoji,
            avatarColorHex = session.avatarColorHex,
            isAdmin = true,
            isSharingLocation = false,
            locationStatus = LocationSharingStatus.STOPPED.name,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            lastUpdatedAt = 0L,
            boundaryStatus = BoundaryStatus.UNKNOWN.name,
            boundaryAlertsOptIn = true
        )

        db.withTransaction {
            db.crowdDao().upsertCrowd(crowd)
            db.memberDao().upsertMember(adminMember)
            db.chatDao().insertMessage(
                ChatMessageEntity(
                    messageId = TokenAndQrUtils.generateUniqueId("msg"),
                    crowdId = crowdId,
                    senderSessionId = "system",
                    senderName = "PINPOINT",
                    senderAvatar = "📍",
                    text = "$cleanDisplay created \"$crowdName\". Share the invite link so everyone can join and share live locations.",
                    createdAt = now
                )
            )
        }
        preferences.setActiveCrowd(crowdId, consentTimestampMs = now)
        return ActionResult.Success(crowd)
    }

    suspend fun lookupInvite(rawInput: String): ActionResult<CrowdEntity> {
        val token = TokenAndQrUtils.parseInviteTokenFromInput(rawInput)
            ?: return ActionResult.Error("Invalid invitation link or code.")
        val crowd = db.crowdDao().getCrowdByInviteToken(token)
            ?: return ActionResult.Error(
                "Invitation not found or revoked on this device. If joining a friend's crowd from another phone, configure your PINPOINT Sync Server URL in Settings."
            )
        return ActionResult.Success(crowd)
    }

    suspend fun joinCrowdByToken(
        rawInput: String,
        displayName: String,
        consentAccepted: Boolean
    ): ActionResult<CrowdEntity> {
        if (!consentAccepted) {
            return ActionResult.Error("Please confirm location-sharing consent before joining.")
        }
        val cleanName = displayName.trim()
        if (cleanName.isEmpty()) {
            return ActionResult.Error("Please enter a display name.")
        }
        val lookup = lookupInvite(rawInput)
        if (lookup is ActionResult.Error) return lookup
        val crowd = (lookup as ActionResult.Success).data

        val session = ensureSession()
        preferences.updateProfile(cleanName, session.avatarEmoji, session.avatarColorHex)

        val now = System.currentTimeMillis()
        val existing = db.memberDao().getMember(crowd.crowdId, session.sessionId)
        val member = existing?.copy(displayName = cleanName) ?: CrowdMemberEntity(
            id = "${crowd.crowdId}_${session.sessionId}",
            crowdId = crowd.crowdId,
            sessionId = session.sessionId,
            displayName = cleanName,
            avatarEmoji = session.avatarEmoji,
            avatarColorHex = session.avatarColorHex,
            isAdmin = crowd.adminSessionId == session.sessionId,
            isSharingLocation = false,
            locationStatus = LocationSharingStatus.STOPPED.name,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            lastUpdatedAt = 0L,
            boundaryStatus = BoundaryStatus.UNKNOWN.name,
            boundaryAlertsOptIn = preferences.guestSessionFlow.first().boundaryAlertsOptIn
        )

        db.withTransaction {
            db.memberDao().upsertMember(member)
            db.chatDao().insertMessage(
                ChatMessageEntity(
                    messageId = TokenAndQrUtils.generateUniqueId("msg"),
                    crowdId = crowd.crowdId,
                    senderSessionId = "system",
                    senderName = "PINPOINT",
                    senderAvatar = "👋",
                    text = "$cleanName joined the crowd.",
                    createdAt = now
                )
            )
        }
        preferences.setActiveCrowd(crowd.crowdId, consentTimestampMs = now)
        return ActionResult.Success(crowd)
    }

    suspend fun regenerateInviteLink(crowdId: String): ActionResult<String> {
        val session = ensureSession()
        val crowd = db.crowdDao().getCrowdById(crowdId)
            ?: return ActionResult.Error("Crowd not found.")
        if (crowd.adminSessionId != session.sessionId) {
            return ActionResult.Error("Only the Crowd Admin can regenerate invitation links.")
        }
        val newToken = TokenAndQrUtils.generateInviteToken()
        val newUrl = TokenAndQrUtils.buildInviteUrl(newToken)
        db.crowdDao().updateInviteToken(crowdId, newToken, newUrl)
        return ActionResult.Success(newUrl)
    }

    suspend fun revokeInviteLink(crowdId: String): ActionResult<Unit> {
        val session = ensureSession()
        val crowd = db.crowdDao().getCrowdById(crowdId)
            ?: return ActionResult.Error("Crowd not found.")
        if (crowd.adminSessionId != session.sessionId) {
            return ActionResult.Error("Only the Crowd Admin can revoke invitation links.")
        }
        db.crowdDao().revokeInvite(crowdId)
        return ActionResult.Success(Unit)
    }

    suspend fun setLocationStatus(status: LocationSharingStatus, sharing: Boolean) {
        val session = ensureSession()
        val crowdId = session.activeCrowdId ?: return
        preferences.setSharingEnabled(sharing)
        val member = db.memberDao().getMember(crowdId, session.sessionId) ?: return
        db.memberDao().updateLocation(
            crowdId = crowdId,
            sessionId = session.sessionId,
            sharing = sharing,
            status = status.name,
            lat = member.latitude,
            lng = member.longitude,
            accuracy = member.accuracyMeters,
            updatedAt = member.lastUpdatedAt,
            boundaryStatus = member.boundaryStatus,
            distanceMeters = member.distanceFromCenterMeters
        )
    }

    suspend fun stopLocationSharingManually() {
        val session = ensureSession()
        preferences.setSharingEnabled(false)
        val crowdId = session.activeCrowdId ?: return
        db.memberDao().stopSharing(crowdId, session.sessionId)
    }

    /**
     * Called by PinpointLocationService on every genuine OS GPS fix.
     * Evaluates geofence transitions with EMA smoothing, accuracy gating, and hysteresis.
     */
    suspend fun processLiveGpsFix(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float
    ): BoundaryEventEntity? {
        val session = ensureSession()
        if (!session.isSharingEnabled) return null
        val crowdId = session.activeCrowdId ?: return null
        val crowd = db.crowdDao().getCrowdById(crowdId) ?: return null
        val member = db.memberDao().getMember(crowdId, session.sessionId) ?: return null

        val now = System.currentTimeMillis()
        val key = "${crowdId}_${session.sessionId}"
        val prevStatus = runCatching { BoundaryStatus.valueOf(member.boundaryStatus) }
            .getOrDefault(BoundaryStatus.UNKNOWN)

        val eval = GeofenceEngine.evaluateTransition(
            rawLat = latitude,
            rawLng = longitude,
            accuracyMeters = accuracyMeters,
            prevSmoothed = smoothedCoords[key],
            centerLat = crowd.geofenceLat,
            centerLng = crowd.geofenceLng,
            radiusMeters = crowd.geofenceRadiusMeters,
            previousStatus = prevStatus,
            lastEventAtMs = lastBoundaryEventTimes[key] ?: 0L,
            nowMs = now
        )

        smoothedCoords[key] = SmoothedCoordinate(eval.smoothedLat, eval.smoothedLng)
        if (eval.triggeredEvent != null) {
            lastBoundaryEventTimes[key] = eval.updatedLastEventAtMs
        }

        val finalLat = if (eval.ignoredForPoorAccuracy && member.latitude != null) member.latitude else eval.smoothedLat
        val finalLng = if (eval.ignoredForPoorAccuracy && member.longitude != null) member.longitude else eval.smoothedLng

        db.memberDao().updateLocation(
            crowdId = crowdId,
            sessionId = session.sessionId,
            sharing = true,
            status = LocationSharingStatus.LIVE.name,
            lat = finalLat,
            lng = finalLng,
            accuracy = accuracyMeters,
            updatedAt = now,
            boundaryStatus = eval.newStatus.name,
            distanceMeters = eval.distanceMeters
        )

        if (session.syncServerUrl.isNotBlank()) {
            backendClient.pushLocationIfConfigured(
                syncServerUrl = session.syncServerUrl,
                sessionId = session.sessionId,
                payload = RemoteLocationPush(
                    crowdId = crowdId,
                    sessionId = session.sessionId,
                    displayName = member.displayName,
                    latitude = finalLat,
                    longitude = finalLng,
                    accuracyMeters = accuracyMeters,
                    isSharing = true,
                    timestampMs = now
                )
            )
        }

        val eventType = eval.triggeredEvent ?: return null
        val event = BoundaryEventEntity(
            eventId = TokenAndQrUtils.generateUniqueId("bnd"),
            crowdId = crowdId,
            memberSessionId = session.sessionId,
            memberName = member.displayName,
            eventType = eventType.name,
            distanceMeters = eval.distanceMeters,
            accuracyMeters = accuracyMeters,
            timestamp = now
        )
        db.boundaryAndNotificationDao().insertBoundaryEvent(event)

        val actionWord = if (eventType == BoundaryEventType.EXITED) "crossed outside" else "returned inside"
        val distText = GeofenceEngine.formatDistance(eval.distanceMeters)
        db.boundaryAndNotificationDao().insertNotification(
            AppNotificationEntity(
                id = TokenAndQrUtils.generateUniqueId("notif"),
                crowdId = crowdId,
                type = "BOUNDARY",
                title = "Geofence Alert: ${member.displayName}",
                body = "${member.displayName} $actionWord the ${GeofenceEngine.formatRadius(crowd.geofenceRadiusMeters)} boundary ($distText from centre).",
                timestamp = now
            )
        )
        return event
    }

    suspend fun updateGeofence(
        crowdId: String,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Int
    ): ActionResult<Unit> {
        val session = ensureSession()
        val crowd = db.crowdDao().getCrowdById(crowdId)
            ?: return ActionResult.Error("Crowd not found.")
        if (crowd.adminSessionId != session.sessionId) {
            return ActionResult.Error("Only the Crowd Admin can modify geofence settings.")
        }
        val validation = GeofenceEngine.validateRadiusInt(radiusMeters)
        if (validation is com.example.domain.RadiusValidationResult.Invalid) {
            return ActionResult.Error(validation.message)
        }
        db.crowdDao().updateGeofence(
            crowdId = crowdId,
            lat = centerLat,
            lng = centerLng,
            radiusMeters = radiusMeters,
            updatedAt = System.currentTimeMillis()
        )
        return ActionResult.Success(Unit)
    }

    suspend fun setBoundaryAlertsOptIn(crowdId: String, optIn: Boolean) {
        val session = ensureSession()
        preferences.setBoundaryAlertsOptIn(optIn)
        db.memberDao().setBoundaryAlertsOptIn(crowdId, session.sessionId, optIn)
    }

    suspend fun sendChatMessage(crowdId: String, rawText: String): ActionResult<ChatMessageEntity> {
        val text = rawText.trim()
        if (text.isEmpty()) return ActionResult.Error("Message cannot be empty.")
        val session = ensureSession()
        val members = db.memberDao().getMembersOnce(crowdId)
        val self = members.find { it.sessionId == session.sessionId }
            ?: return ActionResult.Error("You are no longer a member of this crowd.")

        val targets = members.map { MemberMentionTarget(it.sessionId, it.displayName) }
        val mentions = TokenAndQrUtils.extractValidMentions(text, targets)
        val now = System.currentTimeMillis()

        val msg = ChatMessageEntity(
            messageId = TokenAndQrUtils.generateUniqueId("msg"),
            crowdId = crowdId,
            senderSessionId = session.sessionId,
            senderName = self.displayName,
            senderAvatar = self.avatarEmoji,
            text = text,
            mentionedSessionIdsCsv = mentions.mentionedSessionIds.joinToString(","),
            mentionedNamesCsv = mentions.mentionedDisplayNames.joinToString(","),
            createdAt = now
        )
        db.chatDao().insertMessage(msg)

        if (mentions.mentionedDisplayNames.isNotEmpty()) {
            db.boundaryAndNotificationDao().insertNotification(
                AppNotificationEntity(
                    id = TokenAndQrUtils.generateUniqueId("notif"),
                    crowdId = crowdId,
                    type = "MENTION",
                    title = "${self.displayName} mentioned ${mentions.mentionedDisplayNames.joinToString(", ") { "@$it" }}",
                    body = text,
                    timestamp = now
                )
            )
        }
        return ActionResult.Success(msg)
    }

    suspend fun reportMessage(crowdId: String, messageId: String, targetSessionId: String, reason: String) {
        db.chatDao().markReported(messageId, reason)
        db.boundaryAndNotificationDao().insertAbuseReport(
            AbuseReportEntity(
                reportId = TokenAndQrUtils.generateUniqueId("rep"),
                crowdId = crowdId,
                targetSessionId = targetSessionId,
                messageId = messageId,
                reason = reason
            )
        )
    }

    suspend fun leaveCrowd(crowdId: String) {
        val session = ensureSession()
        preferences.setActiveCrowd(null)
        db.memberDao().removeMember(crowdId, session.sessionId)
    }

    suspend fun deleteCrowdPermanently(crowdId: String): ActionResult<Unit> {
        val session = ensureSession()
        val crowd = db.crowdDao().getCrowdById(crowdId)
            ?: return ActionResult.Error("Crowd not found.")
        if (crowd.adminSessionId != session.sessionId) {
            return ActionResult.Error("Unauthorized: Only the Crowd Admin can permanently delete a crowd.")
        }

        preferences.setActiveCrowd(null)
        smoothedCoords.keys.filter { it.startsWith("${crowdId}_") }.forEach { smoothedCoords.remove(it) }
        lastBoundaryEventTimes.keys.filter { it.startsWith("${crowdId}_") }.forEach { lastBoundaryEventTimes.remove(it) }

        if (session.syncServerUrl.isNotBlank()) {
            backendClient.deleteRemoteCrowdIfConfigured(session.syncServerUrl, session.sessionId, crowdId)
        }

        db.withTransaction {
            db.memberDao().deleteAllForCrowd(crowdId)
            db.chatDao().deleteAllForCrowd(crowdId)
            db.boundaryAndNotificationDao().deleteBoundaryEventsForCrowd(crowdId)
            db.boundaryAndNotificationDao().deleteNotificationsForCrowd(crowdId)
            db.boundaryAndNotificationDao().deleteReportsForCrowd(crowdId)
            db.crowdDao().deleteCrowdHard(crowdId)
            db.boundaryAndNotificationDao().insertNotification(
                AppNotificationEntity(
                    id = TokenAndQrUtils.generateUniqueId("notif"),
                    crowdId = null,
                    type = "CROWD_ENDED",
                    title = "Crowd deleted: ${crowd.name}",
                    body = "All crowd memberships, live locations, chat messages, invitations, and geofence records have been permanently deleted.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return ActionResult.Success(Unit)
    }

    suspend fun markAllNotificationsRead() {
        db.boundaryAndNotificationDao().markAllNotificationsRead()
    }

    suspend fun fetchNearbyWithSponsored(
        referenceLat: Double,
        referenceLng: Double,
        category: NearbyCategory
    ): Pair<List<PlaceListing>, String?> {
        val organicResult = overpassService.fetchNearbyPlaces(referenceLat, referenceLng, category)
        val organic = when (organicResult) {
            is OverpassResult.Success -> organicResult.places
            is OverpassResult.Error -> emptyList()
        }
        val errorNote = (organicResult as? OverpassResult.Error)?.message

        val now = System.currentTimeMillis()
        val candidates = db.businessDao().observeActiveCampaigns().first().map { entity ->
            EligiblePromotionCandidate(
                campaignId = entity.campaignId,
                businessName = entity.businessName,
                category = entity.category,
                description = entity.description,
                website = entity.website.takeIf { it.isNotBlank() },
                latitude = entity.latitude,
                longitude = entity.longitude,
                radiusKm = entity.radiusKm,
                status = runCatching { CampaignStatus.valueOf(entity.status) }.getOrDefault(CampaignStatus.DRAFT),
                expiresAtMs = entity.expiresAt ?: 0L
            )
        }

        val ranked = AdPricingEngine.mergeAndRankPlaces(
            referenceLat = referenceLat,
            referenceLng = referenceLng,
            promotions = candidates,
            organicPlaces = organic,
            selectedCategory = if (category == NearbyCategory.ALL) null else category.label,
            nowMs = now
        )
        return ranked to errorNote
    }

    suspend fun saveBusinessProfile(
        name: String,
        category: String,
        description: String,
        website: String,
        contactPhone: String,
        contactEmail: String,
        addressLabel: String,
        latitude: Double,
        longitude: Double
    ): ActionResult<BusinessProfileEntity> {
        if (name.isBlank() || description.isBlank() || contactEmail.isBlank()) {
            return ActionResult.Error("Business name, description, and contact email are required.")
        }
        val session = ensureSession()
        val existing = db.businessDao().getMyBusinessOnce()
        val entity = BusinessProfileEntity(
            businessId = existing?.businessId ?: TokenAndQrUtils.generateUniqueId("biz"),
            ownerSessionId = session.sessionId,
            name = name.trim(),
            category = category,
            description = description.trim(),
            website = website.trim(),
            contactPhone = contactPhone.trim(),
            contactEmail = contactEmail.trim(),
            addressLabel = addressLabel.trim().ifBlank { "India" },
            latitude = latitude,
            longitude = longitude,
            isVerified = existing?.isVerified ?: false
        )
        db.businessDao().upsertBusiness(entity)
        return ActionResult.Success(entity)
    }

    suspend fun createDraftPromotion(
        business: BusinessProfileEntity,
        radiusKm: Int,
        durationDays: Int
    ): ActionResult<PromotionCampaignEntity> {
        if (!AdPricingEngine.isValidRadiusKm(radiusKm)) {
            return ActionResult.Error("Select a valid advertising radius (1, 2, 5, 10, or 20 km).")
        }
        if (!AdPricingEngine.isValidDuration(durationDays)) {
            return ActionResult.Error("Campaign duration must be between 7 and 100 days.")
        }
        val price = AdPricingEngine.calculatePriceInr(durationDays)
        val campaign = PromotionCampaignEntity(
            campaignId = TokenAndQrUtils.generateUniqueId("cmp"),
            businessId = business.businessId,
            businessName = business.name,
            category = business.category,
            description = business.description,
            website = business.website,
            latitude = business.latitude,
            longitude = business.longitude,
            radiusKm = radiusKm,
            durationDays = durationDays,
            priceInr = price,
            status = CampaignStatus.PENDING_PAYMENT.name
        )
        db.businessDao().upsertCampaign(campaign)
        return ActionResult.Success(campaign)
    }

    suspend fun initiateRazorpayCheckout(campaignId: String): GatewayOrderResult {
        val session = ensureSession()
        val campaign = db.businessDao().getCampaignById(campaignId)
            ?: return GatewayOrderResult.Failed("Campaign not found.")

        // Prevent duplicate active or pending payment orders
        val existingTx = db.businessDao().findExistingPaidOrPendingTransaction(campaignId)
        if (existingTx != null && existingTx.status == "VERIFIED") {
            return GatewayOrderResult.Failed("Duplicate payment prevented: Campaign already has a verified transaction (${existingTx.transactionId}).")
        }

        val orderResult = backendClient.requestRazorpayOrder(
            syncServerUrl = session.syncServerUrl,
            razorpayKeyId = session.razorpayKeyId,
            sessionId = session.sessionId,
            request = RazorpayOrderRequest(
                campaignId = campaign.campaignId,
                businessId = campaign.businessId,
                durationDays = campaign.durationDays,
                amountInr = campaign.priceInr,
                idempotencyKey = "idem_${campaign.campaignId}"
            )
        )

        val now = System.currentTimeMillis()
        when (orderResult) {
            is GatewayOrderResult.Created -> {
                db.businessDao().upsertCampaign(
                    campaign.copy(
                        razorpayOrderId = orderResult.order.orderId,
                        status = CampaignStatus.PENDING_PAYMENT.name
                    )
                )
                db.businessDao().upsertTransaction(
                    PaymentTransactionEntity(
                        transactionId = TokenAndQrUtils.generateUniqueId("tx"),
                        campaignId = campaignId,
                        amountInr = campaign.priceInr,
                        orderId = orderResult.order.orderId,
                        paymentId = null,
                        status = "PENDING_ORDER",
                        note = "Server-side Razorpay order created. Awaiting user payment & webhook verification.",
                        createdAt = now
                    )
                )
            }
            is GatewayOrderResult.AwaitingConfiguration -> {
                db.businessDao().upsertTransaction(
                    PaymentTransactionEntity(
                        transactionId = TokenAndQrUtils.generateUniqueId("tx"),
                        campaignId = campaignId,
                        amountInr = campaign.priceInr,
                        orderId = null,
                        paymentId = null,
                        status = "AWAITING_CONFIG",
                        note = orderResult.reason,
                        createdAt = now
                    )
                )
            }
            is GatewayOrderResult.Failed -> {
                db.businessDao().upsertTransaction(
                    PaymentTransactionEntity(
                        transactionId = TokenAndQrUtils.generateUniqueId("tx"),
                        campaignId = campaignId,
                        amountInr = campaign.priceInr,
                        orderId = null,
                        paymentId = null,
                        status = "FAILED",
                        note = orderResult.error,
                        createdAt = now
                    )
                )
            }
        }
        return orderResult
    }

    suspend fun submitRefundRequest(campaignId: String, transactionId: String, reason: String): ActionResult<Unit> {
        if (reason.isBlank()) return ActionResult.Error("Please describe the refund reason.")
        val campaign = db.businessDao().getCampaignById(campaignId)
            ?: return ActionResult.Error("Campaign not found.")
        db.businessDao().upsertRefundRequest(
            RefundRequestEntity(
                refundId = TokenAndQrUtils.generateUniqueId("ref"),
                campaignId = campaignId,
                transactionId = transactionId,
                reason = reason.trim(),
                amountInr = campaign.priceInr,
                status = "SUBMITTED"
            )
        )
        db.businessDao().upsertCampaign(campaign.copy(status = CampaignStatus.REFUND_REQUESTED.name))
        return ActionResult.Success(Unit)
    }

    suspend fun updateTileProvider(url: String, attribution: String) =
        preferences.updateTileProvider(url, attribution)

    suspend fun updateBackendConfig(syncUrl: String, razorpayKeyId: String) =
        preferences.updateBackendAndPaymentConfig(syncUrl, razorpayKeyId)
}
