package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.TokenAndQrUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.guestDataStore: DataStore<Preferences> by preferencesDataStore(name = "pinpoint_guest_session")

data class GuestSessionState(
    val sessionId: String,
    val displayName: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val activeCrowdId: String?,
    val isSharingEnabled: Boolean,
    val boundaryAlertsOptIn: Boolean,
    val tileUrl: String,
    val tileAttribution: String,
    val syncServerUrl: String,
    val razorpayKeyId: String,
    val consentAcceptedAt: Long
)

class GuestPreferencesDataStore(private val context: Context) {

    private object Keys {
        val SESSION_ID = stringPreferencesKey("session_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_EMOJI = stringPreferencesKey("avatar_emoji")
        val AVATAR_COLOR = stringPreferencesKey("avatar_color")
        val ACTIVE_CROWD_ID = stringPreferencesKey("active_crowd_id")
        val SHARING_ENABLED = booleanPreferencesKey("sharing_enabled")
        val BOUNDARY_ALERTS_OPT_IN = booleanPreferencesKey("boundary_alerts_opt_in")
        val TILE_URL = stringPreferencesKey("tile_url")
        val TILE_ATTRIBUTION = stringPreferencesKey("tile_attribution")
        val SYNC_SERVER_URL = stringPreferencesKey("sync_server_url")
        val RAZORPAY_KEY_ID = stringPreferencesKey("razorpay_key_id")
        val CONSENT_ACCEPTED_AT = longPreferencesKey("consent_accepted_at")
    }

    val guestSessionFlow: Flow<GuestSessionState> = context.guestDataStore.data.map { prefs ->
        GuestSessionState(
            sessionId = prefs[Keys.SESSION_ID] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            avatarEmoji = prefs[Keys.AVATAR_EMOJI] ?: "🧭",
            avatarColorHex = prefs[Keys.AVATAR_COLOR] ?: "#1D63FF",
            activeCrowdId = prefs[Keys.ACTIVE_CROWD_ID]?.takeIf { it.isNotBlank() },
            isSharingEnabled = prefs[Keys.SHARING_ENABLED] ?: false,
            boundaryAlertsOptIn = prefs[Keys.BOUNDARY_ALERTS_OPT_IN] ?: true,
            tileUrl = prefs[Keys.TILE_URL] ?: DEFAULT_OSM_TILE_URL,
            tileAttribution = prefs[Keys.TILE_ATTRIBUTION] ?: DEFAULT_OSM_ATTRIBUTION,
            syncServerUrl = prefs[Keys.SYNC_SERVER_URL] ?: "",
            razorpayKeyId = prefs[Keys.RAZORPAY_KEY_ID] ?: "",
            consentAcceptedAt = prefs[Keys.CONSENT_ACCEPTED_AT] ?: 0L
        )
    }

    suspend fun ensureSessionInitialized(): GuestSessionState {
        val current = guestSessionFlow.first()
        if (current.sessionId.isNotBlank()) return current
        val newId = TokenAndQrUtils.generateSecureSessionId()
        context.guestDataStore.edit { prefs ->
            prefs[Keys.SESSION_ID] = newId
            if (prefs[Keys.AVATAR_EMOJI].isNullOrBlank()) prefs[Keys.AVATAR_EMOJI] = "🧭"
            if (prefs[Keys.AVATAR_COLOR].isNullOrBlank()) prefs[Keys.AVATAR_COLOR] = "#1D63FF"
        }
        return guestSessionFlow.first()
    }

    suspend fun updateProfile(displayName: String, avatarEmoji: String, avatarColorHex: String) {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = displayName.trim()
            prefs[Keys.AVATAR_EMOJI] = avatarEmoji
            prefs[Keys.AVATAR_COLOR] = avatarColorHex
        }
    }

    suspend fun setActiveCrowd(crowdId: String?, consentTimestampMs: Long? = null) {
        context.guestDataStore.edit { prefs ->
            if (crowdId == null) {
                prefs.remove(Keys.ACTIVE_CROWD_ID)
                prefs[Keys.SHARING_ENABLED] = false
            } else {
                prefs[Keys.ACTIVE_CROWD_ID] = crowdId
            }
            if (consentTimestampMs != null) {
                prefs[Keys.CONSENT_ACCEPTED_AT] = consentTimestampMs
            }
        }
    }

    suspend fun setSharingEnabled(enabled: Boolean) {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.SHARING_ENABLED] = enabled
        }
    }

    suspend fun setBoundaryAlertsOptIn(optIn: Boolean) {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.BOUNDARY_ALERTS_OPT_IN] = optIn
        }
    }

    suspend fun updateTileProvider(tileUrl: String, attribution: String) {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.TILE_URL] = tileUrl.trim().ifEmpty { DEFAULT_OSM_TILE_URL }
            prefs[Keys.TILE_ATTRIBUTION] = attribution.trim().ifEmpty { DEFAULT_OSM_ATTRIBUTION }
        }
    }

    suspend fun updateBackendAndPaymentConfig(syncServerUrl: String, razorpayKeyId: String) {
        context.guestDataStore.edit { prefs ->
            prefs[Keys.SYNC_SERVER_URL] = syncServerUrl.trim()
            prefs[Keys.RAZORPAY_KEY_ID] = razorpayKeyId.trim()
        }
    }

    companion object {
        const val DEFAULT_OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val DEFAULT_OSM_ATTRIBUTION = "&copy; OpenStreetMap contributors"
    }
}
