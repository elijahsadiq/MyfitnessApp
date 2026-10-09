package com.elijah.fitness.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
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
 *
 * ### Works without Google Play services
 * Phones that ship without Google Play services (Huawei, some Chinese
 * ROMs, Fire tablets) fail the fused request. When that happens the
 * flow falls back to the platform [LocationManager]'s GPS provider, so
 * tracking still works — just without the fused provider's smoothing.
 *
 * ### Never crashes on a missing permission
 * If location permission was revoked between the check and the request,
 * the flow completes quietly instead of throwing into the service.
 */
class LocationTracker(context: Context) {

    private val appContext: Context = context.applicationContext

    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val request: LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TrackingConstants.UPDATE_INTERVAL.inWholeMilliseconds,
        )
            .setMinUpdateIntervalMillis(TrackingConstants.FASTEST_INTERVAL.inWholeMilliseconds)
            .setWaitForAccurateLocation(false)
            .build()

    /**
     * Emit every raw [Location] the provider gives us. The caller checks
     * the location permission first — the SuppressLint is deliberate and
     * the `SecurityException` catch is the safety net.
     * Flow collection cancellation removes the registered callbacks.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val locationManager = appContext.getSystemService<LocationManager>()
        var platformListener: LocationListenerCompat? = null
        // Set when collection stops; both callbacks run on the main thread.
        var closed = false

        fun startPlatformUpdates() {
            if (closed || platformListener != null) return
            val lm = locationManager ?: return
            val listener = LocationListenerCompat { loc -> trySend(loc) }
            val platformRequest = LocationRequestCompat.Builder(
                TrackingConstants.UPDATE_INTERVAL.inWholeMilliseconds,
            )
                .setMinUpdateIntervalMillis(TrackingConstants.FASTEST_INTERVAL.inWholeMilliseconds)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .build()
            try {
                LocationManagerCompat.requestLocationUpdates(
                    lm, LocationManager.GPS_PROVIDER, platformRequest, listener,
                    Looper.getMainLooper(),
                )
                platformListener = listener
            } catch (_: SecurityException) {
                close()
            } catch (_: IllegalArgumentException) {
                // No GPS hardware on this device — nothing to listen to.
                close()
            }
        }

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) trySend(loc)
            }
        }

        try {
            fused.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnFailureListener { startPlatformUpdates() }
        } catch (_: SecurityException) {
            close()
        }

        awaitClose {
            closed = true
            fused.removeLocationUpdates(fusedCallback)
            val listener = platformListener
            if (listener != null && locationManager != null) {
                LocationManagerCompat.removeUpdates(locationManager, listener)
            }
        }
    }

    /**
     * The phone's last known position (may be minutes old), used only to
     * centre the map before the first live fix. `null` when unknown or
     * when permission is missing. Delivered on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(onResult: (Location?) -> Unit) {
        val platformFallback = {
            val lm = appContext.getSystemService<LocationManager>()
            val loc = try {
                lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            onResult(loc)
        }
        try {
            fused.lastLocation
                .addOnSuccessListener { loc -> if (loc != null) onResult(loc) else platformFallback() }
                .addOnFailureListener { platformFallback() }
        } catch (_: SecurityException) {
            onResult(null)
        }
    }

    /** `true` when the device-wide location switch is on. */
    fun isLocationEnabled(): Boolean {
        val lm = appContext.getSystemService<LocationManager>() ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }
}
