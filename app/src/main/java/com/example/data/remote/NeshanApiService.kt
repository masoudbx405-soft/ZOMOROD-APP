package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Real Retrofit interface for Neshan's REST API (https://platform.neshan.org).
 *
 * NOTE: field names below follow Neshan's publicly documented Direction API
 * (v4) response shape. Since this project can't reach the live network from
 * this environment, double check the exact field names against your Neshan
 * developer panel docs the first time you run a real request, and adjust the
 * @Json(name = ...) annotations if anything differs.
 */
interface NeshanApiService {

    /**
     * Real driving-direction lookup. Distance/duration come straight back
     * from Neshan's routing engine (no simulated delay(), no hardcoded value).
     */
    @GET("v4/direction")
    suspend fun getDirection(
        @Header("Api-Key") apiKey: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("type") type: String = "car"
    ): Response<NeshanDirectionResponse>
}

@JsonClass(generateAdapter = true)
data class NeshanDirectionResponse(
    @Json(name = "routes") val routes: List<NeshanRoute> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NeshanRoute(
    @Json(name = "legs") val legs: List<NeshanLeg> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NeshanLeg(
    @Json(name = "distance") val distance: NeshanValueText? = null,
    @Json(name = "duration") val duration: NeshanValueText? = null
)

@JsonClass(generateAdapter = true)
data class NeshanValueText(
    @Json(name = "value") val value: Long = 0L,
    @Json(name = "text") val text: String = ""
)

/** Simple result model exposed to the rest of the app. */
data class RouteInfo(
    val distanceMeters: Long,
    val distanceText: String,
    val durationSeconds: Long,
    val durationText: String
)
