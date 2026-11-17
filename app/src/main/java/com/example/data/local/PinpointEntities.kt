package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crowds")
data class CrowdEntity(
    @PrimaryKey val crowdId: String,
    val name: String,
    val inviteToken: String,
    val inviteUrl: String,
    val inviteRevoked: Boolean = false,
    val adminSessionId: String,
    val geofenceLat: Double,
    val geofenceLng: Double,
    val geofenceRadiusMeters: Int = 500,
    val geofenceUpdatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "crowd_members")
data class CrowdMemberEntity(
    @PrimaryKey val id: String, // "${crowdId}_${sessionId}"
    val crowdId: String,
    val sessionId: String,
    val displayName: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val isAdmin: Boolean,
    val isSharingLocation: Boolean,
    val locationStatus: String, // LIVE, CONNECTING, UNAVAILABLE, PERMISSION_DENIED, STOPPED
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val lastUpdatedAt: Long,
    val boundaryStatus: String, // INSIDE, OUTSIDE, UNKNOWN
    val boundaryAlertsOptIn: Boolean = true,
    val distanceFromCenterMeters: Double? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val crowdId: String,
    val senderSessionId: String,
    val senderName: String,
    val senderAvatar: String,
    val text: String,
    val mentionedSessionIdsCsv: String = "",
    val mentionedNamesCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isReported: Boolean = false,
    val reportReason: String? = null
)

@Entity(tableName = "boundary_events")
data class BoundaryEventEntity(
    @PrimaryKey val eventId: String,
    val crowdId: String,
    val memberSessionId: String,
    val memberName: String,
    val eventType: String, // ENTERED, EXITED
    val distanceMeters: Double,
    val accuracyMeters: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "app_notifications")
data class AppNotificationEntity(
    @PrimaryKey val id: String,
    val crowdId: String?,
    val type: String, // MENTION, BOUNDARY, CROWD_ENDED, SYSTEM
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey val businessId: String,
    val ownerSessionId: String,
    val name: String,
    val category: String,
    val description: String,
    val website: String,
    val contactPhone: String,
    val contactEmail: String,
    val addressLabel: String,
    val latitude: Double,
    val longitude: Double,
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "promotion_campaigns")
data class PromotionCampaignEntity(
    @PrimaryKey val campaignId: String,
    val businessId: String,
    val businessName: String,
    val category: String,
    val description: String,
    val website: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int,
    val durationDays: Int,
    val priceInr: Int,
    val status: String, // CampaignStatus.name
    val razorpayOrderId: String? = null,
    val razorpayPaymentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val activatedAt: Long? = null,
    val expiresAt: Long? = null
)

@Entity(tableName = "payment_transactions")
data class PaymentTransactionEntity(
    @PrimaryKey val transactionId: String,
    val campaignId: String,
    val amountInr: Int,
    val gateway: String = "RAZORPAY",
    val orderId: String?,
    val paymentId: String?,
    val status: String, // AWAITING_CONFIG, PENDING_ORDER, VERIFIED, FAILED, REFUND_REQUESTED, REFUNDED
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "refund_requests")
data class RefundRequestEntity(
    @PrimaryKey val refundId: String,
    val campaignId: String,
    val transactionId: String,
    val reason: String,
    val amountInr: Int,
    val status: String, // SUBMITTED, UNDER_REVIEW, APPROVED, REFUNDED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "abuse_reports")
data class AbuseReportEntity(
    @PrimaryKey val reportId: String,
    val crowdId: String,
    val targetSessionId: String,
    val messageId: String?,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)
