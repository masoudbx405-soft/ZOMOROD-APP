package com.example.data.repository

import com.example.data.remote.NeshanRetrofitClient
import com.example.data.remote.RouteInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real Neshan integration: no delay(), no fake numbers.
 * Every value returned here comes from an actual network call to Neshan's API.
 */
class NeshanRepository {

    private val api = NeshanRetrofitClient.api

    /**
     * Fetches real driving distance & duration between two points using
     * Neshan's Direction API. Returns null on any network/parsing failure
     * so the UI can fall back gracefully instead of crashing.
     */
    suspend fun fetchRouteInfo(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<RouteInfo> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDirection(
                apiKey = NeshanRetrofitClient.apiKey,
                origin = "$originLat,$originLng",
                destination = "$destLat,$destLng"
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Neshan API error: HTTP ${response.code()}")
                )
            }

            val leg = response.body()?.routes?.firstOrNull()?.legs?.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("مسیری یافت نشد"))

            Result.success(
                RouteInfo(
                    distanceMeters = leg.distance?.value ?: 0L,
                    distanceText = leg.distance?.text ?: "-",
                    durationSeconds = leg.duration?.value ?: 0L,
                    durationText = leg.duration?.text ?: "-"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Real static map tile URL (replaces the old hand-drawn fake city Canvas). */
    fun staticMapUrl(lat: Double, lng: Double, widthPx: Int = 600, heightPx: Int = 300): String =
        NeshanRetrofitClient.buildStaticMapUrl(lat, lng, widthPx, heightPx)
}
