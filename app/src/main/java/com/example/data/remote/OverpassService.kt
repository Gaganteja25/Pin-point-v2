package com.example.data.remote

import com.example.domain.GeofenceEngine
import com.example.domain.PlaceListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class NearbyCategory(val label: String, val osmFilter: String) {
    ALL("All", "[\"amenity\"~\"restaurant|cafe|hospital|pharmacy|fuel|atm|parking\"]"),
    RESTAURANTS("Restaurants", "[\"amenity\"=\"restaurant\"]"),
    HOTELS("Hotels", "[\"tourism\"~\"hotel|guest_house|hostel\"]"),
    CAFES("Cafes", "[\"amenity\"=\"cafe\"]"),
    HOSPITALS("Hospitals", "[\"amenity\"~\"hospital|clinic\"]"),
    PHARMACIES("Pharmacies", "[\"amenity\"=\"pharmacy\"]"),
    PETROL("Petrol stations", "[\"amenity\"=\"fuel\"]"),
    ATMS("ATMs", "[\"amenity\"~\"atm|bank\"]"),
    SHOPPING("Shopping centres", "[\"shop\"~\"mall|department_store\"]"),
    SUPERMARKETS("Supermarkets", "[\"shop\"=\"supermarket\"]"),
    PARKING("Parking", "[\"amenity\"=\"parking\"]"),
    TOURIST("Tourist attractions", "[\"tourism\"~\"attraction|museum|viewpoint|monument\"]"),
    ENTERTAINMENT("Entertainment", "[\"amenity\"~\"cinema|theatre|arts_centre|nightclub\"]"),
    GYMS("Gyms", "[\"leisure\"~\"fitness_centre|sports_centre\"]")
}

sealed interface OverpassResult {
    data class Success(val places: List<PlaceListing>, val fromCache: Boolean) : OverpassResult
    data class Error(val message: String) : OverpassResult
}

class OverpassService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(val timestampMs: Long, val places: List<PlaceListing>)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTtlMs = 120_000L // 2-minute cache to respect public Overpass rate limits

    suspend fun fetchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        category: NearbyCategory,
        radiusMeters: Int = 2500
    ): OverpassResult = withContext(Dispatchers.IO) {
        val gridLat = (latitude * 200).roundToInt() / 200.0
        val gridLng = (longitude * 200).roundToInt() / 200.0
        val cacheKey = "${gridLat}_${gridLng}_${category.name}_$radiusMeters"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (now - cached.timestampMs < cacheTtlMs) {
                return@withContext OverpassResult.Success(cached.places, fromCache = true)
            }
        }

        val query = """
            [out:json][timeout:12];
            (
              node${category.osmFilter}(around:$radiusMeters,$latitude,$longitude);
              way${category.osmFilter}(around:$radiusMeters,$latitude,$longitude);
            );
            out center 25;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        var lastError = "Unable to reach OpenStreetMap Overpass API."
        for (endpoint in endpoints) {
            try {
                val body = FormBody.Builder().add("data", query).build()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "PinpointAndroid/1.0 (breakfreeentertainments0@gmail.com)")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "OpenStreetMap Overpass returned HTTP ${response.code}."
                        return@use
                    }
                    val raw = response.body?.string() ?: ""
                    val parsed = parseOverpassJson(raw, latitude, longitude, category)
                    cache[cacheKey] = CacheEntry(now, parsed)
                    return@withContext OverpassResult.Success(parsed, fromCache = false)
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error querying OpenStreetMap."
            }
        }
        OverpassResult.Error(lastError)
    }

    private fun parseOverpassJson(
        jsonString: String,
        originLat: Double,
        originLng: Double,
        requestedCategory: NearbyCategory
    ): List<PlaceListing> {
        val root = JSONObject(jsonString)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val list = mutableListOf<PlaceListing>()

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name").takeIf { it.isNotBlank() } ?: continue

            val center = el.optJSONObject("center")
            val lat = if (el.has("lat")) el.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lng = if (el.has("lon")) el.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lng.isNaN()) continue

            val amenity = tags.optString("amenity").takeIf { it.isNotBlank() }
                ?: tags.optString("shop").takeIf { it.isNotBlank() }
                ?: tags.optString("tourism").takeIf { it.isNotBlank() }
                ?: tags.optString("leisure").takeIf { it.isNotBlank() }
                ?: requestedCategory.label

            val categoryLabel = mapTagToCategoryLabel(amenity, requestedCategory)
            val street = listOfNotNull(
                tags.optString("addr:street").takeIf { it.isNotBlank() },
                tags.optString("addr:city").takeIf { it.isNotBlank() }
            ).joinToString(", ")

            val openingHours = tags.optString("opening_hours").takeIf { it.isNotBlank() }
            val details = listOfNotNull(
                street.takeIf { it.isNotBlank() },
                openingHours?.let { "Hours: $it" }
            ).joinToString(" • ").ifBlank { "OpenStreetMap place" }

            val website = tags.optString("website").takeIf { it.isNotBlank() }
            val dist = GeofenceEngine.haversineDistanceMeters(originLat, originLng, lat, lng)

            list.add(
                PlaceListing(
                    id = "osm_${el.optLong("id")}",
                    name = name,
                    category = categoryLabel,
                    latitude = lat,
                    longitude = lng,
                    distanceMeters = dist,
                    addressOrDetails = details,
                    website = website,
                    isSponsored = false
                )
            )
        }
        return list.sortedBy { it.distanceMeters }
    }

    private fun mapTagToCategoryLabel(tag: String, fallback: NearbyCategory): String {
        return when (tag.lowercase()) {
            "restaurant" -> "Restaurants"
            "hotel", "guest_house", "hostel" -> "Hotels"
            "cafe" -> "Cafes"
            "hospital", "clinic" -> "Hospitals"
            "pharmacy" -> "Pharmacies"
            "fuel" -> "Petrol stations"
            "atm", "bank" -> "ATMs"
            "mall", "department_store" -> "Shopping centres"
            "supermarket" -> "Supermarkets"
            "parking" -> "Parking"
            "attraction", "museum", "viewpoint", "monument" -> "Tourist attractions"
            "cinema", "theatre", "arts_centre", "nightclub" -> "Entertainment"
            "fitness_centre", "sports_centre" -> "Gyms"
            else -> if (fallback == NearbyCategory.ALL) tag.replace('_', ' ').replaceFirstChar { it.uppercase() } else fallback.label
        }
    }
}
