package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real GPS location provider (replaces the fixed 35.779/51.405 reference
 * point previously hardcoded in DriverViewModel). Uses Google's
 * FusedLocationProviderClient — the standard, battery-efficient way to get
 * a device's real position on Android.
 *
 * Every function here checks the runtime permission itself and simply
 * returns null / an empty flow if it isn't granted, instead of crashing —
 * the caller (DriverViewModel) is responsible for prompting the user to
 * grant the permission (see MainDriverScreen.kt).
 */
class LocationTrackingManager(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val appContext = context.applicationContext

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** One-shot real location fetch (e.g. for the first Neshan route calculation). */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationOnce(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(Pair(location.latitude, location.longitude))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /**
     * Continuous real location updates while GPS tracking is toggled on.
     * Replaces the old single fake `repository.logGpsLocation(35.779, 51.405, 45.0f)` call.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMillis: Long = 15_000L): Flow<LocationPoint> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        LocationPoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            speedMetersPerSecond = location.speed
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, null)

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float
)
