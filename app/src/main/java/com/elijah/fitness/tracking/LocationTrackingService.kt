package com.elijah.fitness.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Foreground service that owns the location callback registration for the
 * duration of a workout. Lifecycle in one sentence: `start` → bind the
 * location provider → forward each fix into [TrackingSession] → mirror the
 * state into the ongoing notification → `stopService` tears everything down.
 *
 * Pause / resume / finish are applied to the shared [TrackingSession]
 * directly by the screen; the service only has to keep reading GPS and
 * keep the notification current, so it never needs to be "started" just
 * to flip a flag (which Android forbids while the app is in the
 * background).
 *
 * ### Why foreground, not plain background
 * Tracking must continue with the screen off; a bound-service alone is
 * not enough on modern Android. Declaring `foregroundServiceType="location"`
 * (API 29+) grants the running-while-background-location-access that
 * `ACCESS_BACKGROUND_LOCATION` would otherwise require, and the ongoing
 * notification keeps the user aware that location is being read.
 *
 * ### Process-kill limitation
 * If the OS kills the process mid-workout (e.g. extreme memory pressure)
 * the tracked data is lost — there is no on-disk journal yet. Called out
 * in the dev log because it is better to name the limitation than hide it.
 */
class LocationTrackingService : Service() {

    private lateinit var session: TrackingSession
    private lateinit var tracker: LocationTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    private var notificationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = (applicationContext as FitnessApp).container
        session = container.trackingSession
        tracker = container.locationTracker
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            // A system restart after a process kill (null intent) has no
            // session to resume — the in-memory state died with the process.
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val activityName = intent.getStringExtra(EXTRA_ACTIVITY_TYPE)
            ?: ActivityType.RUNNING.name
        val activityType = runCatching { ActivityType.valueOf(activityName) }
            .getOrDefault(ActivityType.RUNNING)

        // Android 14+ refuses a location foreground service without a
        // location grant (SecurityException). Stopping straight away is
        // also what keeps "did not call startForeground" from firing.
        if (!LocationPermissions.hasAnyLocationPermission(this) || !promoteToForeground(activityType)) {
            stopSelf()
            return
        }

        if (session.state.value.status == TrackingState.Status.Idle) {
            session.start(activityType)
        }

        acquireWakeLock()
        startJobs()
    }

    /**
     * `startForeground` with the location type. Returns `false` (instead of
     * crashing) when the system refuses — e.g. the permission was revoked
     * a moment ago, or Android 12+ decided the app is in the background.
     */
    private fun promoteToForeground(activityType: ActivityType): Boolean {
        val initial = buildNotification(activityType, elapsedMs = 0L, distanceM = 0.0)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, initial,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, initial)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends this.
            false
        }
    }

    private fun startJobs() {
        // Pump location fixes into the session. Fixes older than
        // [MAX_FIX_AGE_MS] are a cached "last known location" — possibly
        // from another street or town — and would make every real fix
        // after it look like a teleport, so they're skipped.
        locationJob?.cancel()
        locationJob = scope.launch {
            tracker.locationUpdates()
                .catch { /* provider failed — keep the session, show "Searching" */ }
                .collect { loc ->
                    // How long ago the position was measured, from the
                    // monotonic clock — `loc.time` (GPS time) can be stale
                    // or skewed, which hid pauses and counted distance
                    // walked while paused.
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
                    if (ageMs > MAX_FIX_AGE_MS) return@collect
                    val now = System.currentTimeMillis()
                    session.onFix(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                        // Stored with the point: acceptance time, on the same
                        // clock as the session's elapsed time, so the pause
                        // detector compares like with like.
                        recordedAt = Instant.ofEpochMilli(now),
                        measuredAt = Instant.ofEpochMilli(now - ageMs.coerceAtLeast(0L)),
                    )
                }
        }

        // Tick the elapsed-time counter between fixes.
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                session.tickElapsed()
                delay(1_000)
            }
        }

        // Refresh the notification whenever elapsed/distance/status change
        // meaningfully.
        notificationJob?.cancel()
        notificationJob = scope.launch {
            session.state
                .map { state ->
                    listOf(
                        state.activityType,
                        state.status,
                        state.elapsedMs / 1_000,          // 1-second grain
                        (state.distanceMeters / 10.0).toLong(), // 10-metre grain
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val s = session.state.value
                    if (s.status == TrackingState.Status.Idle) return@collect
                    val nm = getSystemService<NotificationManager>() ?: return@collect
                    nm.notify(
                        NOTIFICATION_ID,
                        buildNotification(s.activityType, s.elapsedMs, s.distanceMeters, s.status),
                    )
                }
        }
    }

    private fun handleStop() {
        session.stop()
        stopJobs()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopJobs() {
        locationJob?.cancel(); locationJob = null
        tickerJob?.cancel(); tickerJob = null
        notificationJob?.cancel(); notificationJob = null
    }

    override fun onDestroy() {
        stopJobs()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.tracking_notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(
        activity: ActivityType,
        elapsedMs: Long,
        distanceM: Double,
        status: TrackingState.Status = TrackingState.Status.Tracking,
    ): Notification {
        val title = getString(
            when {
                status == TrackingState.Status.Paused -> R.string.tracking_notification_title_paused
                activity == ActivityType.CYCLING -> R.string.tracking_notification_title_cycling
                else -> R.string.tracking_notification_title_running
            }
        )
        val body = getString(
            R.string.tracking_notification_body,
            formatElapsed(elapsedMs),
            formatKm(distanceM),
        )

        // Tapping the notification opens the live tracking screen.
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            MainActivity.openTrackingIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin_24)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    private fun formatKm(meters: Double): String =
        getString(R.string.tracking_notification_distance, meters / 1000.0)

    // -----------------------------------------------------------------
    // Wake lock
    // -----------------------------------------------------------------

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService<PowerManager>() ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "fitness:tracking",
        ).also {
            it.setReferenceCounted(false)
            // 4-hour safety ceiling in case we somehow miss a release.
            it.acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val NOTIFICATION_ID = 2024
        private const val CHANNEL_ID = "tracking"

        /** Ignore cached fixes older than this (ms). */
        private const val MAX_FIX_AGE_MS = 30_000L

        const val ACTION_START = "com.elijah.fitness.tracking.START"
        const val ACTION_STOP = "com.elijah.fitness.tracking.STOP"
        const val EXTRA_ACTIVITY_TYPE = "activityType"

        fun startIntent(context: Context, activityType: ActivityType): Intent =
            Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ACTIVITY_TYPE, activityType.name)

        /** Plain component intent — use with `Context.stopService`. */
        fun serviceIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java)
    }
}
