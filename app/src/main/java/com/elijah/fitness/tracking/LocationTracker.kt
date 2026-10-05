package com.elijah.fitness.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin wrapper around [FusedLocationProviderClient] that exposes location
 * updates as a cold [Flow]. The service collects from this flow and feeds
 * fixes into [TrackingSession]. Keeping the Android dependency isolated
 * here lets [TrackingSession] stay pure Kotlin and unit-testable.
 */
class LocationTracker(context: Context) {

    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val request: LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TrackingConstants.UPDATE_INTERVAL.inWholeMilliseconds,
        )
            .setMinUpdateIntervalMillis(TrackingConstants.FASTEST_INTERVAL.inWholeMilliseconds)
            .setWaitForAccurateLocation(false)
            .build()

    /**
     * Emit every raw [Location] the provider gives us. The caller holds
     * the location permission by contract — the SuppressLint is deliberate.
     * Flow collection cancellation removes the registered callback cleanly.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) trySend(loc)
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
