package com.example

import com.example.domain.AdPricingEngine
import com.example.domain.BoundaryEventType
import com.example.domain.BoundaryStatus
import com.example.domain.CampaignStatus
import com.example.domain.EligiblePromotionCandidate
import com.example.domain.GeofenceEngine
import com.example.domain.MemberMentionTarget
import com.example.domain.PlaceListing
import com.example.domain.RadiusValidationResult
import com.example.domain.SmoothedCoordinate
import com.example.domain.TokenAndQrUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinpointCoreLogicTest {

    @Test
    fun `geofence radius validation accepts 10m to 100km and rejects invalid values`() {
        val validInputs = listOf("10", "100", "1000", "10000", "50000", "100000")
        validInputs.forEach { input ->
            assertTrue("Expected $input to be valid", GeofenceEngine.validateRadius(input) is RadiusValidationResult.Valid)
        }

        val invalidInputs = listOf("9", "0", "-50", "100001", "10.5", "1,000", "abc", "")
        invalidInputs.forEach { input ->
            assertTrue("Expected $input to be invalid", GeofenceEngine.validateRadius(input) is RadiusValidationResult.Invalid)
        }

        assertEquals("10 m", GeofenceEngine.formatRadius(10))
        assertEquals("750 m", GeofenceEngine.formatRadius(750))
        assertEquals("1 km", GeofenceEngine.formatRadius(1000))
        assertEquals("2.5 km", GeofenceEngine.formatRadius(2500))
        assertEquals("100 km", GeofenceEngine.formatRadius(100_000))
    }

    @Test
    fun `geofence hysteresis and jitter filter prevent false alerts`() {
        val centerLat = 17.3850
        val centerLng = 78.4867
        val radius = 100 // 100m, hysteresis = 5m (exit > 105m, re-enter <= 95m)

        // 1. Inside fix establishes INSIDE state
        val step1 = GeofenceEngine.evaluateTransition(
            rawLat = centerLat,
            rawLng = centerLng,
            accuracyMeters = 12f,
            prevSmoothed = null,
            centerLat = centerLat,
            centerLng = centerLng,
            radiusMeters = radius,
            previousStatus = BoundaryStatus.UNKNOWN,
            lastEventAtMs = 0L,
            nowMs = 1_000L
        )
        assertEquals(BoundaryStatus.INSIDE, step1.newStatus)
        assertNull(step1.triggeredEvent)

        // 2. Noisy GPS jump (accuracy = 250m) should be ignored and NOT trigger exit
        val noisyStep = GeofenceEngine.evaluateTransition(
            rawLat = 17.3950,
            rawLng = 78.4967,
            accuracyMeters = 250f,
            prevSmoothed = SmoothedCoordinate(step1.smoothedLat, step1.smoothedLng),
            centerLat = centerLat,
            centerLng = centerLng,
            radiusMeters = radius,
            previousStatus = BoundaryStatus.INSIDE,
            lastEventAtMs = 0L,
            nowMs = 10_000L
        )
        assertTrue(noisyStep.ignoredForPoorAccuracy)
        assertEquals(BoundaryStatus.INSIDE, noisyStep.newStatus)
        assertNull(noisyStep.triggeredEvent)

        // 3. Real exit well outside 100m + hysteresis (~220m away) after smoothing
        var currentLat = centerLat
        var smoothed = SmoothedCoordinate(step1.smoothedLat, step1.smoothedLng)
        var exitTriggered = false
        for (i in 1..4) {
            currentLat = 17.3872 // ~244m north
            val res = GeofenceEngine.evaluateTransition(
                rawLat = currentLat,
                rawLng = centerLng,
                accuracyMeters = 10f,
                prevSmoothed = smoothed,
                centerLat = centerLat,
                centerLng = centerLng,
                radiusMeters = radius,
                previousStatus = BoundaryStatus.INSIDE,
                lastEventAtMs = 0L,
                nowMs = 40_000L + i * 5_000L
            )
            smoothed = SmoothedCoordinate(res.smoothedLat, res.smoothedLng)
            if (res.triggeredEvent == BoundaryEventType.EXITED) {
                exitTriggered = true
                break
            }
        }
        assertTrue("Expected real exit to trigger boundary event", exitTriggered)
    }

    @Test
    fun `mention extraction validates strictly against current crowd members`() {
        val members = listOf(
            MemberMentionTarget("s1", "Rahul"),
            MemberMentionTarget("s2", "Gagan Teja"),
            MemberMentionTarget("s3", "Arjun")
        )
        val result = TokenAndQrUtils.extractValidMentions(
            "@Gagan Teja @Rahul please meet near the gate! @Stranger ignore",
            members
        )
        assertEquals(listOf("s2", "s1"), result.mentionedSessionIds)
        assertEquals(listOf("Gagan Teja", "Rahul"), result.mentionedDisplayNames)
        assertFalse(result.mentionedDisplayNames.contains("Stranger"))
    }

    @Test
    fun `advertising pricing and sponsored ranking work accurately`() {
        assertEquals(21, AdPricingEngine.calculatePriceInr(7))
        assertEquals(45, AdPricingEngine.calculatePriceInr(15))
        assertEquals(90, AdPricingEngine.calculatePriceInr(30))
        assertEquals(150, AdPricingEngine.calculatePriceInr(50))
        assertEquals(225, AdPricingEngine.calculatePriceInr(75))
        assertEquals(300, AdPricingEngine.calculatePriceInr(100))

        val now = 1_000_000L
        val promos = listOf(
            EligiblePromotionCandidate(
                campaignId = "c_active_far",
                businessName = "Far Cafe",
                category = "Cafes",
                description = "Coffee",
                website = null,
                latitude = 17.4000,
                longitude = 78.4867,
                radiusKm = 5,
                status = CampaignStatus.ACTIVE,
                expiresAtMs = now + 86_400_000L
            ),
            EligiblePromotionCandidate(
                campaignId = "c_active_near",
                businessName = "Near Cafe",
                category = "Cafes",
                description = "Espresso",
                website = null,
                latitude = 17.3860,
                longitude = 78.4867,
                radiusKm = 5,
                status = CampaignStatus.ACTIVE,
                expiresAtMs = now + 86_400_000L
            ),
            EligiblePromotionCandidate(
                campaignId = "c_expired",
                businessName = "Expired Cafe",
                category = "Cafes",
                description = "Old",
                website = null,
                latitude = 17.3851,
                longitude = 78.4867,
                radiusKm = 5,
                status = CampaignStatus.EXPIRED,
                expiresAtMs = now + 86_400_000L
            )
        )
        val organic = listOf(
            PlaceListing("osm_1", "Organic Bistro", "Cafes", 17.3852, 78.4867, 25.0, "OSM")
        )
        val ranked = AdPricingEngine.mergeAndRankPlaces(
            referenceLat = 17.3850,
            referenceLng = 78.4867,
            promotions = promos,
            organicPlaces = organic,
            selectedCategory = "Cafes",
            nowMs = now
        )

        assertEquals(3, ranked.size)
        assertTrue(ranked[0].isSponsored)
        assertEquals("Near Cafe", ranked[0].name)
        assertTrue(ranked[1].isSponsored)
        assertEquals("Far Cafe", ranked[1].name)
        assertFalse(ranked[2].isSponsored)
        assertEquals("Organic Bistro", ranked[2].name)
    }

    @Test
    fun `invitation token generation and parsing roundtrip`() {
        val token = TokenAndQrUtils.generateInviteToken()
        val url = TokenAndQrUtils.buildInviteUrl(token)
        assertEquals(token, TokenAndQrUtils.parseInviteTokenFromInput(url))
        assertEquals(token, TokenAndQrUtils.parseInviteTokenFromInput("pinpoint://join/$token"))
        assertNull(TokenAndQrUtils.parseInviteTokenFromInput("ab"))
    }
}
