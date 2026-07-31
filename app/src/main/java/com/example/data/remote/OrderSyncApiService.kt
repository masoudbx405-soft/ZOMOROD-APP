package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Real Retrofit interface matching the web panel's /api/v1/driver/* and
 * /api/v1/orders/* endpoints exactly (see server.ts). Every request needs
 * the shared x-driver-api-key header — added automatically by
 * OrderSyncRetrofitClient's interceptor, never passed manually here.
 */
interface OrderSyncApiService {

    @GET("api/v1/driver/routes/collection")
    suspend fun getCollectionRoute(@Query("driverId") driverId: String): Response<RouteResponse>

    @GET("api/v1/driver/routes/delivery")
    suspend fun getDeliveryRoute(@Query("driverId") driverId: String): Response<RouteResponse>

    @POST("api/v1/orders/{id}/items")
    suspend fun submitItems(@Path("id") orderId: String, @Body body: SubmitItemsRequest): Response<GenericSyncResponse>

    @PUT("api/v1/orders/{id}/status")
    suspend fun updateStatus(@Path("id") orderId: String, @Body body: UpdateStatusRequest): Response<GenericSyncResponse>

    @POST("api/v1/orders/{id}/return-to-warehouse")
    suspend fun returnToWarehouse(@Path("id") orderId: String, @Body body: ReturnToWarehouseRequest): Response<GenericSyncResponse>

    @POST("api/v1/orders/{id}/settle")
    suspend fun settleOrder(@Path("id") orderId: String, @Body body: SettleRequest): Response<GenericSyncResponse>

    @POST("api/v1/driver/office-settlement")
    suspend fun officeSettlement(@Body body: OfficeSettlementRequest): Response<GenericSyncResponse>

    @POST("api/v1/driver/location")
    suspend fun pushLiveLocation(@Body body: LiveLocationRequest): Response<GenericSyncResponse>

    @POST("api/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestBody): Response<OtpRequestResponse>

    @POST("api/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyBody): Response<OtpVerifyResponse>
}

data class RouteResponse(
    val success: Boolean,
    val driverId: String,
    val routeType: String,
    val ordersCount: Int,
    val orders: List<DriverOrderDto>
)

data class GenericSyncResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

data class SubmitItemsRequest(
    val items: List<DriverCarpetItemDto>,
    val driverId: String,
    val prepaidAmount: Long
)

data class UpdateStatusRequest(
    val status: String,
    val rackCode: String? = null,
    val notes: String? = null
)

data class ReturnToWarehouseRequest(
    val cleanRackCode: String,
    val returnReason: String,
    val driverId: String
)

data class SettleRequest(
    val paymentType: String,
    val paidAmount: Long,
    val remainingAmount: Long,
    val verifiedBarcodes: List<String> = emptyList()
)

data class OfficeSettlementRequest(
    val driverId: String,
    val totalCash: Long,
    val totalPos: Long,
    val totalCardToCard: Long,
    val totalOnline: Long,
    val settledOrderIds: List<String>
)

data class LiveLocationRequest(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float,
    val timestamp: Long
)

data class OtpRequestBody(val mobile: String)

data class OtpRequestResponse(
    val success: Boolean,
    val isLive: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class OtpVerifyBody(val mobile: String, val code: String)

data class OtpVerifyResponse(
    val success: Boolean,
    val driverId: String? = null,
    val error: String? = null
)
