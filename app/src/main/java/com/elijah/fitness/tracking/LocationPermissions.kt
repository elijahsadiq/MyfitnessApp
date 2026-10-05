package com.elijah.fitness.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Thin wrapper around the runtime location-permission dance. Shared by the
 * onboarding priming screen and the live-tracking entry point so neither
 * has to re-derive the "fine vs coarse vs denied" matrix.
 *
 * ### Why no background permission
 * Phase 8a deliberately asks only for the foreground pair
 * ([Manifest.permission.ACCESS_FINE_LOCATION] +
 * [Manifest.permission.ACCESS_COARSE_LOCATION]). The tracking service runs
 * in the foreground with a persistent notification, which lifts the
 * background-location requirement and avoids a far stricter Play Store
 * review path. If a future phase ever tracks with the screen off AND the
 * app process backgrounded, that's when `ACCESS_BACKGROUND_LOCATION` gets
 * added — not before.
 */
object LocationPermissions {

    /** Request both; the user may grant one, both, or neither. */
    val LOCATION_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** API 33+ only. On older versions the notification posts without a dialog. */
    val NOTIFICATION_PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    enum class Outcome {
        /** Fine-grained location granted — track with full accuracy. */
        FineGranted,
        /** Coarse only (API 31+ "Approximate" option). Tracking is possible but noisy. */
        CoarseOnly,
        /** User refused this time but may be asked again next launch. */
        Denied,
    }

    fun isFineGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun isCoarseGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun outcome(context: Context): Outcome = when {
        isFineGranted(context) -> Outcome.FineGranted
        isCoarseGranted(context) -> Outcome.CoarseOnly
        else -> Outcome.Denied
    }

    /**
     * `true` once the user has refused once but the system will still show
     * the dialog next time. On the first-ever ask this returns `false`.
     *
     * A `false` return *after* a refusal means we're in the "don't ask
     * again" (API 23+) or "permanently denied" state; the only recovery
     * path is the app-settings deep link.
     */
    fun shouldShowRationale(fragment: Fragment): Boolean =
        fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Register an [ActivityResultLauncher] that asks for the location pair.
     * The callback receives the derived [Outcome] rather than the raw
     * grantResults map so callers can `when { ... }` straight through.
     *
     * Must be called before the host enters STARTED (the standard
     * `registerForActivityResult` constraint).
     */
    fun registerLocationLauncher(
        caller: ActivityResultCaller,
        onResult: (Outcome) -> Unit,
    ): ActivityResultLauncher<Array<String>> =
        caller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            onResult(
                when {
                    fine -> Outcome.FineGranted
                    coarse -> Outcome.CoarseOnly
                    else -> Outcome.Denied
                }
            )
        }

    /** Launcher for the API 33+ notification permission — no-op callback by default. */
    fun registerNotificationLauncher(
        caller: ActivityResultCaller,
        onResult: (granted: Boolean) -> Unit = {},
    ): ActivityResultLauncher<String> =
        caller.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { onResult(it) }

    /**
     * `true` on API < 33 (no runtime request needed) or when the user has
     * already granted the permission.
     */
    fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
