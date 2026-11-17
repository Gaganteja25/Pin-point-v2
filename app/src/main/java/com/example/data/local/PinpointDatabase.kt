package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CrowdEntity::class,
        CrowdMemberEntity::class,
        ChatMessageEntity::class,
        BoundaryEventEntity::class,
        AppNotificationEntity::class,
        BusinessProfileEntity::class,
        PromotionCampaignEntity::class,
        PaymentTransactionEntity::class,
        RefundRequestEntity::class,
        AbuseReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PinpointDatabase : RoomDatabase() {
    abstract fun crowdDao(): CrowdDao
    abstract fun memberDao(): CrowdMemberDao
    abstract fun chatDao(): ChatDao
    abstract fun boundaryAndNotificationDao(): BoundaryAndNotificationDao
    abstract fun businessDao(): BusinessDao

    companion object {
        @Volatile
        private var INSTANCE: PinpointDatabase? = null

        fun getInstance(context: Context): PinpointDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PinpointDatabase::class.java,
                    "pinpoint_secure.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
