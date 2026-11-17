package com.example.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class BoundaryStatus {
    INSIDE,
    OUTSIDE,
    UNKNOWN
}

enum class BoundaryEventType {
    ENTERED,
    EXITED
}

sealed interface RadiusValidationResult {
    data class Valid(val radiusMeters: Int) : RadiusValidationResult
    data class Invalid(val message: String) : RadiusValidationResult
}

data class SmoothedCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class GeofenceEvaluationResult(
    val newStatus: BoundaryStatus,
    val smoothedLat: Double,
    val smoothedLng: Double,
    val distanceMeters: Double,
    val triggeredEvent: BoundaryEventType?,
    val updatedLastEventAtMs: Long,
    val ignoredForPoorAccuracy: Boolean
)

object GeofenceEngine {
    const val MIN_RADIUS_METERS = 10
    const val MAX_RADIUS_METERS = 100_000
    const val STALE_THRESHOLD_MS = 120_000L // 2 minutes
    const val DEFAULT_COOLDOWN_MS = 30_000L // 30s anti-jitter event deduplication
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun validateRadius(rawInput: String): RadiusValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return RadiusValidationResult.Invalid("Enter a whole number between 10 m and 100,000 m.")
        }
        if (trimmed.contains('.') || trimmed.contains(',')) {
            return RadiusValidationResult.Invalid("Radius must be a whole number in metres (no decimals).")
        }
        val parsed = trimmed.toIntOrNull()
            ?: return RadiusValidationResult.Invalid("Enter a valid whole-number radius in metres.")
        return validateRadiusInt(parsed)
    }

    fun validateRadiusInt(radiusMeters: Int): RadiusValidationResult {
        return when {
            radiusMeters < MIN_RADIUS_METERS ->
                RadiusValidationResult.Invalid("Minimum geofence radius is 10 metres.")
            radiusMeters > MAX_RADIUS_METERS ->
                RadiusValidationResult.Invalid("Maximum geofence radius is 100,000 metres (100 km).")
            else -> RadiusValidationResult.Valid(radiusMeters)
        }
    }

    fun formatDistance(meters: Double): String {
        val rounded = meters.roundToInt()
        return if (rounded < 1000) {
            "$rounded m"
        } else {
            val km = meters / 1000.0
            if (km % 1.0 < 0.05 || km >= 10.0) {
                "${km.roundToInt()} km"
            } else {
                String.format(java.util.Locale.US, "%.1f km", km)
            }
        }
    }

    fun formatRadius(radiusMeters: Int): String {
        return if (radiusMeters < 1000) {
            "$radiusMeters m"
        } else {
            val km = radiusMeters / 1000.0
            if (radiusMeters % 1000 == 0) {
                "${radiusMeters / 1000} km"
            } else {
                String.format(java.util.Locale.US, "%.1f km", km)
            }
        }
    }

    fun isStaleLocation(lastUpdatedMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastUpdatedMs <= 0L) return true
        return (nowMs - lastUpdatedMs) > STALE_THRESHOLD_MS
    }

    fun formatRelativeFreshness(lastUpdatedMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (lastUpdatedMs <= 0L) return "No fix yet"
        val diffSec = ((nowMs - lastUpdatedMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 10 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> {
                val mins = diffSec / 60
                if (diffSec * 1000L > STALE_THRESHOLD_MS) "Stale (${mins}m ago)" else "${mins}m ago"
            }
            else -> "Stale (${diffSec / 3600}h ago)"
        }
    }

    /**
     * Evaluates boundary transitions with:
     * 1. GPS accuracy gating (rejects noisy fixes when accuracy exceeds acceptable threshold)
     * 2. Exponential moving average smoothing (alpha = 0.45)
     * 3. Hysteresis buffer (3m to 25m based on radius)
     * 4. Cooldown deduplication window (default 30s)
     */
    fun evaluateTransition(
        rawLat: Double,
        rawLng: Double,
        accuracyMeters: Float,
        prevSmoothed: SmoothedCoordinate?,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Int,
        previousStatus: BoundaryStatus,
        lastEventAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): GeofenceEvaluationResult {
        val maxAcceptableAccuracy = max(60.0, radiusMeters * 0.65)
        if (accuracyMeters > maxAcceptableAccuracy && prevSmoothed != null) {
            val prevDist = haversineDistanceMeters(
                prevSmoothed.latitude, prevSmoothed.longitude, centerLat, centerLng
            )
            return GeofenceEvaluationResult(
                newStatus = previousStatus,
                smoothedLat = prevSmoothed.latitude,
                smoothedLng = prevSmoothed.longitude,
                distanceMeters = prevDist,
                triggeredEvent = null,
                updatedLastEventAtMs = lastEventAtMs,
                ignoredForPoorAccuracy = true
            )
        }

        val alpha = 0.45
        val smoothedLat = if (prevSmoothed == null) rawLat else alpha * rawLat + (1 - alpha) * prevSmoothed.latitude
        val smoothedLng = if (prevSmoothed == null) rawLng else alpha * rawLng + (1 - alpha) * prevSmoothed.longitude
        val distance = haversineDistanceMeters(smoothedLat, smoothedLng, centerLat, centerLng)

        val hysteresis = min(25.0, max(3.0, radiusMeters * 0.05))
        val cooldownPassed = (nowMs - lastEventAtMs) >= cooldownMs

        return when (previousStatus) {
            BoundaryStatus.UNKNOWN -> {
                val initial = if (distance <= radiusMeters) BoundaryStatus.INSIDE else BoundaryStatus.OUTSIDE
                GeofenceEvaluationResult(
                    newStatus = initial,
                    smoothedLat = smoothedLat,
                    smoothedLng = smoothedLng,
                    distanceMeters = distance,
                    triggeredEvent = null,
                    updatedLastEventAtMs = lastEventAtMs,
                    ignoredForPoorAccuracy = false
                )
            }
            BoundaryStatus.INSIDE -> {
                if (distance > radiusMeters + hysteresis && cooldownPassed) {
                    GeofenceEvaluationResult(
                        newStatus = BoundaryStatus.OUTSIDE,
                        smoothedLat = smoothedLat,
                        smoothedLng = smoothedLng,
                        distanceMeters = distance,
                        triggeredEvent = BoundaryEventType.EXITED,
                        updatedLastEventAtMs = nowMs,
                        ignoredForPoorAccuracy = false
                    )
                } else {
                    GeofenceEvaluationResult(
                        newStatus = BoundaryStatus.INSIDE,
                        smoothedLat = smoothedLat,
                        smoothedLng = smoothedLng,
                        distanceMeters = distance,
                        triggeredEvent = null,
                        updatedLastEventAtMs = lastEventAtMs,
                        ignoredForPoorAccuracy = false
                    )
                }
            }
            BoundaryStatus.OUTSIDE -> {
                if (distance <= max(0.0, radiusMeters - hysteresis) && cooldownPassed) {
                    GeofenceEvaluationResult(
                        newStatus = BoundaryStatus.INSIDE,
                        smoothedLat = smoothedLat,
                        smoothedLng = smoothedLng,
                        distanceMeters = distance,
                        triggeredEvent = BoundaryEventType.ENTERED,
                        updatedLastEventAtMs = nowMs,
                        ignoredForPoorAccuracy = false
                    )
                } else {
                    GeofenceEvaluationResult(
                        newStatus = BoundaryStatus.OUTSIDE,
                        smoothedLat = smoothedLat,
                        smoothedLng = smoothedLng,
                        distanceMeters = distance,
                        triggeredEvent = null,
                        updatedLastEventAtMs = lastEventAtMs,
                        ignoredForPoorAccuracy = false
                    )
                }
            }
        }
    }
}
