package com.elijah.fitness.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.ui.launcher.LauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Foreground service that owns the location callback registration for the
 * duration of a workout. Lifecycle in one sentence: `start` → bind the
 * fused provider → forward each fix into [TrackingSession] → mirror the
 * state into the ongoing notification → `stop` tears everything down.
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
 * the tracked data is lost — there is no on-disk journal in phase 8a.
 * Future work in a phase-9 "offline sync" would persist to Room on every
 * accepted fix. Called out in the dev log because it is better to name
 * the limitation than hide it.
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

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> session.pause()
            ACTION_RESUME -> session.resume()
            ACTION_STOP -> handleStop()
            else -> if (session.state.value.status == TrackingState.Status.Idle) {
                // Protect against OS-restarts after a kill — nothing to resume.
                stopSelf(startId)
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun handleStart(intent: Intent) {
        val activityName = intent.getStringExtra(EXTRA_ACTIVITY_TYPE)
            ?: ActivityType.RUNNING.name
        val activityType = runCatching { ActivityType.valueOf(activityName) }
            .getOrDefault(ActivityType.RUNNING)

        if (session.state.value.status == TrackingState.Status.Idle) {
            session.start(activityType)
        }

        // Promote to foreground. On API 34+ the location type MUST be
        // declared with the typed call or startForeground throws.
        val initial = buildNotification(activityType, elapsedMs = 0L, distanceM = 0.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }

        acquireWakeLock()

        // Pump location fixes into the session.
        locationJob?.cancel()
        locationJob = scope.launch {
            tracker.locationUpdates().collect { loc ->
                session.onFix(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                    recordedAt = Instant.ofEpochMilli(loc.time),
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

        // Refresh the notification whenever elapsed/distance change meaningfully.
        notificationJob?.cancel()
        notificationJob = scope.launch {
            session.state
                .map { state ->
                    Triple(
                        state.activityType,
                        (state.elapsedMs / 1_000).toInt(),      // 1-second grain
                        (state.distanceMeters / 10.0).toInt(),  // 10-metre grain
                    )
                }
                .distinctUntilChanged()
                .collect { _ ->
                    val s = session.state.value
                    val nm = getSystemService<NotificationManager>() ?: return@collect
                    nm.notify(
                        NOTIFICATION_ID,
                        buildNotification(s.activityType, s.elapsedMs, s.distanceMeters),
                    )
                }
        }
    }

    private fun handleStop() {
        session.stop()
        locationJob?.cancel(); locationJob = null
        tickerJob?.cancel(); tickerJob = null
        notificationJob?.cancel(); notificationJob = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationJob?.cancel(); locationJob = null
        tickerJob?.cancel(); tickerJob = null
        notificationJob?.cancel(); notificationJob = null
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
    ): android.app.Notification {
        val title = getString(
            when (activity) {
                ActivityType.RUNNING -> R.string.tracking_notification_title_running
                ActivityType.CYCLING -> R.string.tracking_notification_title_cycling
                else -> R.string.tracking_notification_title_running
            }
        )
        val body = getString(
            R.string.tracking_notification_body,
            formatElapsed(elapsedMs),
            formatKm(distanceM),
        )

        // Tapping the notification bounces back into the app. The actual
        // live-tracking fragment destination is wired in Step 5 — until
        // then, Launcher routes to Main which picks up the state flow.
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin_24)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
        "%.2f km".format(meters / 1000.0)

    // -----------------------------------------------------------------
    // Wake lock
    // -----------------------------------------------------------------

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

        const val ACTION_START = "com.elijah.fitness.tracking.START"
        const val ACTION_PAUSE = "com.elijah.fitness.tracking.PAUSE"
        const val ACTION_RESUME = "com.elijah.fitness.tracking.RESUME"
        const val ACTION_STOP = "com.elijah.fitness.tracking.STOP"
        const val EXTRA_ACTIVITY_TYPE = "activityType"

        fun startIntent(context: Context, activityType: ActivityType): Intent =
            Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ACTIVITY_TYPE, activityType.name)

        fun pauseIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).setAction(ACTION_PAUSE)

        fun resumeIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).setAction(ACTION_RESUME)

        fun stopIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
    }
}
