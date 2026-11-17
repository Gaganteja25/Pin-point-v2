package com.example.domain

enum class CampaignStatus(val label: String) {
    DRAFT("Draft"),
    PENDING_PAYMENT("Pending Payment"),
    PAYMENT_SUCCESSFUL("Payment Successful"),
    PENDING_VERIFICATION("Pending Verification"),
    ACTIVE("Active"),
    REJECTED("Rejected"),
    EXPIRED("Expired"),
    SUSPENDED("Suspended"),
    REFUND_REQUESTED("Refund Requested"),
    REFUNDED("Refunded")
}

data class PlaceListing(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val addressOrDetails: String,
    val website: String? = null,
    val isSponsored: Boolean = false,
    val campaignId: String? = null
)

data class EligiblePromotionCandidate(
    val campaignId: String,
    val businessName: String,
    val category: String,
    val description: String,
    val website: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int,
    val status: CampaignStatus,
    val expiresAtMs: Long
)

object AdPricingEngine {
    const val RATE_PER_DAY_INR = 3
    const val MIN_DURATION_DAYS = 7
    const val MAX_DURATION_DAYS = 100
    val ALLOWED_RADII_KM = listOf(1, 2, 5, 10, 20)

    fun calculatePriceInr(durationDays: Int): Int {
        val clamped = durationDays.coerceIn(MIN_DURATION_DAYS, MAX_DURATION_DAYS)
        return clamped * RATE_PER_DAY_INR
    }

    fun isValidDuration(durationDays: Int): Boolean {
        return durationDays in MIN_DURATION_DAYS..MAX_DURATION_DAYS
    }

    fun isValidRadiusKm(radiusKm: Int): Boolean {
        return radiusKm in ALLOWED_RADII_KM
    }

    /**
     * Ranks sponsored listings strictly first (sorted by distance) among ACTIVE, non-expired
     * campaigns whose advertising radius covers the crowd's aggregate reference point,
     * followed by organic OpenStreetMap places sorted by distance.
     */
    fun mergeAndRankPlaces(
        referenceLat: Double,
        referenceLng: Double,
        promotions: List<EligiblePromotionCandidate>,
        organicPlaces: List<PlaceListing>,
        selectedCategory: String?,
        nowMs: Long = System.currentTimeMillis()
    ): List<PlaceListing> {
        val eligibleSponsored = promotions
            .filter { promo ->
                promo.status == CampaignStatus.ACTIVE &&
                    promo.expiresAtMs > nowMs &&
                    (selectedCategory == null || selectedCategory == "All" ||
                        promo.category.equals(selectedCategory, ignoreCase = true))
            }
            .mapNotNull { promo ->
                val dist = GeofenceEngine.haversineDistanceMeters(
                    referenceLat, referenceLng, promo.latitude, promo.longitude
                )
                if (dist <= promo.radiusKm * 1000.0) {
                    PlaceListing(
                        id = "sponsored_${promo.campaignId}",
                        name = promo.businessName,
                        category = promo.category,
                        latitude = promo.latitude,
                        longitude = promo.longitude,
                        distanceMeters = dist,
                        addressOrDetails = promo.description,
                        website = promo.website,
                        isSponsored = true,
                        campaignId = promo.campaignId
                    )
                } else {
                    null
                }
            }
            .sortedBy { it.distanceMeters }

        val sortedOrganic = organicPlaces
            .filter { place ->
                selectedCategory == null || selectedCategory == "All" ||
                    place.category.equals(selectedCategory, ignoreCase = true)
            }
            .sortedBy { it.distanceMeters }

        return eligibleSponsored + sortedOrganic
    }
}
