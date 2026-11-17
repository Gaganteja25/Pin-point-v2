package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CrowdDao {
    @Query("SELECT * FROM crowds WHERE crowdId = :crowdId AND isDeleted = 0 LIMIT 1")
    fun observeCrowd(crowdId: String): Flow<CrowdEntity?>

    @Query("SELECT * FROM crowds WHERE crowdId = :crowdId AND isDeleted = 0 LIMIT 1")
    suspend fun getCrowdById(crowdId: String): CrowdEntity?

    @Query("SELECT * FROM crowds WHERE inviteToken = :token AND inviteRevoked = 0 AND isDeleted = 0 LIMIT 1")
    suspend fun getCrowdByInviteToken(token: String): CrowdEntity?

    @Query("SELECT * FROM crowds WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllCrowds(): Flow<List<CrowdEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrowd(crowd: CrowdEntity)

    @Query("UPDATE crowds SET inviteToken = :newToken, inviteUrl = :newUrl, inviteRevoked = 0 WHERE crowdId = :crowdId")
    suspend fun updateInviteToken(crowdId: String, newToken: String, newUrl: String)

    @Query("UPDATE crowds SET inviteRevoked = 1 WHERE crowdId = :crowdId")
    suspend fun revokeInvite(crowdId: String)

    @Query("UPDATE crowds SET geofenceLat = :lat, geofenceLng = :lng, geofenceRadiusMeters = :radiusMeters, geofenceUpdatedAt = :updatedAt WHERE crowdId = :crowdId")
    suspend fun updateGeofence(crowdId: String, lat: Double, lng: Double, radiusMeters: Int, updatedAt: Long)

    @Query("DELETE FROM crowds WHERE crowdId = :crowdId")
    suspend fun deleteCrowdHard(crowdId: String)
}

@Dao
interface CrowdMemberDao {
    @Query("SELECT * FROM crowd_members WHERE crowdId = :crowdId ORDER BY isAdmin DESC, displayName ASC")
    fun observeMembers(crowdId: String): Flow<List<CrowdMemberEntity>>

    @Query("SELECT * FROM crowd_members WHERE crowdId = :crowdId")
    suspend fun getMembersOnce(crowdId: String): List<CrowdMemberEntity>

    @Query("SELECT * FROM crowd_members WHERE crowdId = :crowdId AND sessionId = :sessionId LIMIT 1")
    suspend fun getMember(crowdId: String, sessionId: String): CrowdMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: CrowdMemberEntity)

    @Query(
        "UPDATE crowd_members SET displayName = :name, avatarEmoji = :emoji, avatarColorHex = :colorHex " +
            "WHERE sessionId = :sessionId"
    )
    suspend fun updateProfileAcrossCrowds(sessionId: String, name: String, emoji: String, colorHex: String)

    @Query(
        "UPDATE crowd_members SET isSharingLocation = :sharing, locationStatus = :status, " +
            "latitude = :lat, longitude = :lng, accuracyMeters = :accuracy, lastUpdatedAt = :updatedAt, " +
            "boundaryStatus = :boundaryStatus, distanceFromCenterMeters = :distanceMeters " +
            "WHERE crowdId = :crowdId AND sessionId = :sessionId"
    )
    suspend fun updateLocation(
        crowdId: String,
        sessionId: String,
        sharing: Boolean,
        status: String,
        lat: Double?,
        lng: Double?,
        accuracy: Float?,
        updatedAt: Long,
        boundaryStatus: String,
        distanceMeters: Double?
    )

    @Query("UPDATE crowd_members SET isSharingLocation = 0, locationStatus = 'STOPPED' WHERE crowdId = :crowdId AND sessionId = :sessionId")
    suspend fun stopSharing(crowdId: String, sessionId: String)

    @Query("UPDATE crowd_members SET boundaryAlertsOptIn = :optIn WHERE crowdId = :crowdId AND sessionId = :sessionId")
    suspend fun setBoundaryAlertsOptIn(crowdId: String, sessionId: String, optIn: Boolean)

    @Query("DELETE FROM crowd_members WHERE crowdId = :crowdId AND sessionId = :sessionId")
    suspend fun removeMember(crowdId: String, sessionId: String)

    @Query("DELETE FROM crowd_members WHERE crowdId = :crowdId")
    suspend fun deleteAllForCrowd(crowdId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE crowdId = :crowdId ORDER BY createdAt ASC")
    fun observeMessages(crowdId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET isReported = 1, reportReason = :reason WHERE messageId = :messageId")
    suspend fun markReported(messageId: String, reason: String)

    @Query("DELETE FROM chat_messages WHERE crowdId = :crowdId")
    suspend fun deleteAllForCrowd(crowdId: String)
}

@Dao
interface BoundaryAndNotificationDao {
    @Query("SELECT * FROM boundary_events WHERE crowdId = :crowdId ORDER BY timestamp DESC")
    fun observeBoundaryEvents(crowdId: String): Flow<List<BoundaryEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoundaryEvent(event: BoundaryEventEntity)

    @Query("DELETE FROM boundary_events WHERE crowdId = :crowdId")
    suspend fun deleteBoundaryEventsForCrowd(crowdId: String)

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun observeNotifications(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotificationEntity)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM app_notifications WHERE crowdId = :crowdId")
    suspend fun deleteNotificationsForCrowd(crowdId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbuseReport(report: AbuseReportEntity)

    @Query("DELETE FROM abuse_reports WHERE crowdId = :crowdId")
    suspend fun deleteReportsForCrowd(crowdId: String)
}

@Dao
interface BusinessDao {
    @Query("SELECT * FROM business_profiles ORDER BY createdAt DESC LIMIT 1")
    fun observeMyBusiness(): Flow<BusinessProfileEntity?>

    @Query("SELECT * FROM business_profiles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getMyBusinessOnce(): BusinessProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: BusinessProfileEntity)

    @Query("SELECT * FROM promotion_campaigns ORDER BY createdAt DESC")
    fun observeAllCampaigns(): Flow<List<PromotionCampaignEntity>>

    @Query("SELECT * FROM promotion_campaigns WHERE status = 'ACTIVE'")
    fun observeActiveCampaigns(): Flow<List<PromotionCampaignEntity>>

    @Query("SELECT * FROM promotion_campaigns WHERE campaignId = :campaignId LIMIT 1")
    suspend fun getCampaignById(campaignId: String): PromotionCampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCampaign(campaign: PromotionCampaignEntity)

    @Query("SELECT * FROM payment_transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<PaymentTransactionEntity>>

    @Query("SELECT * FROM payment_transactions WHERE campaignId = :campaignId AND status IN ('VERIFIED', 'PENDING_ORDER') LIMIT 1")
    suspend fun findExistingPaidOrPendingTransaction(campaignId: String): PaymentTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: PaymentTransactionEntity)

    @Query("SELECT * FROM refund_requests ORDER BY createdAt DESC")
    fun observeRefundRequests(): Flow<List<RefundRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRefundRequest(refundRequest: RefundRequestEntity)
}
