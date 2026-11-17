package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RazorpayOrderRequest(
    val campaignId: String,
    val businessId: String,
    val durationDays: Int,
    val amountInr: Int,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class RazorpayOrderResponse(
    val orderId: String,
    val amountPaise: Int,
    val currency: String,
    val keyId: String
)

@JsonClass(generateAdapter = true)
data class RazorpayVerifyRequest(
    val campaignId: String,
    val razorpayOrderId: String,
    val razorpayPaymentId: String,
    val razorpaySignature: String
)

@JsonClass(generateAdapter = true)
data class RazorpayVerifyResponse(
    val verified: Boolean,
    val campaignStatus: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class RemoteLocationPush(
    val crowdId: String,
    val sessionId: String,
    val displayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val isSharing: Boolean,
    val timestampMs: Long
)

@JsonClass(generateAdapter = true)
data class RemoteCrowdDto(
    val crowdId: String,
    val name: String,
    val inviteToken: String,
    val adminSessionId: String,
    val geofenceLat: Double,
    val geofenceLng: Double,
    val geofenceRadiusMeters: Int
)

interface PinpointBackendApi {
    @POST("api/v1/crowds")
    suspend fun createCrowd(
        @Header("X-Pinpoint-Session") sessionId: String,
        @Body payload: RemoteCrowdDto
    ): Response<RemoteCrowdDto>

    @GET("api/v1/invitations/{token}")
    suspend fun lookupInvite(@Path("token") token: String): Response<RemoteCrowdDto>

    @POST("api/v1/locations")
    suspend fun pushLocation(
        @Header("X-Pinpoint-Session") sessionId: String,
        @Body payload: RemoteLocationPush
    ): Response<Unit>

    @DELETE("api/v1/crowds/{crowdId}")
    suspend fun deleteCrowd(
        @Header("X-Pinpoint-Session") sessionId: String,
        @Path("crowdId") crowdId: String
    ): Response<Unit>

    @POST("api/v1/payments/razorpay/order")
    suspend fun createRazorpayOrder(
        @Header("X-Pinpoint-Session") sessionId: String,
        @Body request: RazorpayOrderRequest
    ): Response<RazorpayOrderResponse>

    @POST("api/v1/payments/razorpay/verify")
    suspend fun verifyRazorpayPayment(
        @Header("X-Pinpoint-Session") sessionId: String,
        @Body request: RazorpayVerifyRequest
    ): Response<RazorpayVerifyResponse>
}

sealed interface GatewayOrderResult {
    data class Created(val order: RazorpayOrderResponse) : GatewayOrderResult
    data class AwaitingConfiguration(val reason: String) : GatewayOrderResult
    data class Failed(val error: String) : GatewayOrderResult
}

class PinpointBackendClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private fun buildApi(baseUrl: String): PinpointBackendApi? {
        val clean = baseUrl.trim().let {
            if (it.isEmpty()) return null
            if (it.endsWith("/")) it else "$it/"
        }
        return try {
            Retrofit.Builder()
                .baseUrl(clean)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(PinpointBackendApi::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun requestRazorpayOrder(
        syncServerUrl: String,
        razorpayKeyId: String,
        sessionId: String,
        request: RazorpayOrderRequest
    ): GatewayOrderResult = withContext(Dispatchers.IO) {
        if (syncServerUrl.isBlank() || razorpayKeyId.isBlank()) {
            return@withContext GatewayOrderResult.AwaitingConfiguration(
                "Razorpay Key ID and HTTPS backend server URL are required for server-side order creation & webhook signature verification. Payment cannot be fabricated client-side."
            )
        }
        val api = buildApi(syncServerUrl)
            ?: return@withContext GatewayOrderResult.Failed("Invalid HTTPS backend server URL.")
        return@withContext try {
            val res = api.createRazorpayOrder(sessionId, request)
            if (res.isSuccessful && res.body() != null) {
                GatewayOrderResult.Created(res.body()!!)
            } else {
                GatewayOrderResult.Failed("Gateway server returned HTTP ${res.code()}.")
            }
        } catch (e: Exception) {
            GatewayOrderResult.Failed(e.localizedMessage ?: "Failed to connect to payment server.")
        }
    }

    suspend fun pushLocationIfConfigured(
        syncServerUrl: String,
        sessionId: String,
        payload: RemoteLocationPush
    ) = withContext(Dispatchers.IO) {
        val api = buildApi(syncServerUrl) ?: return@withContext
        try {
            api.pushLocation(sessionId, payload)
        } catch (_: Exception) {
            // Best-effort sync; local Room state preserves latest fix for retry on reconnect.
        }
    }

    suspend fun deleteRemoteCrowdIfConfigured(
        syncServerUrl: String,
        sessionId: String,
        crowdId: String
    ) = withContext(Dispatchers.IO) {
        val api = buildApi(syncServerUrl) ?: return@withContext
        try {
            api.deleteCrowd(sessionId, crowdId)
        } catch (_: Exception) {
        }
    }
}
