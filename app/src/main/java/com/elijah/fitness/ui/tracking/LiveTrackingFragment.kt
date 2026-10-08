package com.elijah.fitness.ui.tracking

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentLiveTrackingBinding
import com.elijah.fitness.tracking.LocationPermissions
import com.elijah.fitness.tracking.LocationTrackingService
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.tracking.TrackingState
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.elijah.fitness.ui.tracking.map.DarkMapStyler
import com.elijah.fitness.ui.tracking.map.MapCameraController
import com.elijah.fitness.ui.tracking.map.RoutePolylineRenderer
import com.elijah.fitness.util.Formatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * Live tracking screen (phase 8c step 6). Rebuilt from
 * `Tracking.dc.html`:
 *
 * - Map fills the screen behind the status bar and the panel.
 * - Top overlay: GPS pill (left) + minimise chevron (right).
 * - Re-centre button on the right edge, above the panel.
 * - Dark bottom panel with the elapsed time, three stats, and three
 *   controls (Finish, Pause/Resume, Lock).
 * - Lock overlay with "Hold to unlock" — covers taps, blocks the back
 *   gesture, and the map / stats keep updating underneath.
 *
 * The confirm-to-finish flow carries over from Phase 8a. On save
 * success the fragment navigates to the finished-workout screen for
 * the just-saved workout rather than popping back to History.
 */
class LiveTrackingFragment : BaseFragment<FragmentLiveTrackingBinding>() {

    private val activityType: ActivityType by lazy {
        arguments?.getString(ARG_ACTIVITY_TYPE)
            ?.let(ActivityType::valueOf)
            ?: ActivityType.RUNNING
    }

    private val viewModel: LiveTrackingViewModel by viewModels {
        LiveTrackingViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container
        )
    }

    private val notificationLauncher =
        LocationPermissions.registerNotificationLauncher(this)

    private var routeRenderer: RoutePolylineRenderer? = null
    private var mapCamera: MapCameraController? = null

    // Lock-overlay state. View-local — a rotation re-shows the panel
    // with the overlay dismissed; the service carries on either way.
    private var isLocked: Boolean = false
    private var backCallback: OnBackPressedCallback? = null

    // Hold-to-unlock pulse animation.
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdStartMs: Long = 0L
    private val holdTick: Runnable = object : Runnable {
        override fun run() {
            val b = _bindingOrNull ?: return
            val elapsed = System.currentTimeMillis() - holdStartMs
            if (elapsed >= HOLD_TO_UNLOCK_MS) {
                b.lockHoldProgress.progress = HOLD_TO_UNLOCK_MS.toInt()
                exitLock()
            } else {
                b.lockHoldProgress.progress = elapsed.toInt()
                holdHandler.postDelayed(this, 16L)
            }
        }
    }

    /** Nullable binding accessor — background callbacks bail cleanly after `onDestroyView`. */
    private val _bindingOrNull: FragmentLiveTrackingBinding?
        get() = runCatching { binding }.getOrNull()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLiveTrackingBinding =
        FragmentLiveTrackingBinding.inflate(inflater, container, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsetsPerLayer()

        configureOsmdroid()
        setupMap()

        binding.minimiseButton.setOnClickListener { findNavController().popBackStack() }
        binding.pauseResumeButton.setOnClickListener { onPauseResumeTapped() }
        binding.finishButton.setOnClickListener { confirmFinish() }
        binding.lockButton.setOnClickListener { enterLock() }
        binding.recenterButton.setOnClickListener { onRecenterTapped() }

        setActivityLabels()

        binding.lockHoldArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    holdStartMs = System.currentTimeMillis()
                    binding.lockHoldProgress.isVisible = true
                    binding.lockHoldProgress.progress = 0
                    holdHandler.post(holdTick)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelHoldTick()
                    true
                }
                else -> false
            }
        }

        startTrackingIfIdle()
        requestNotificationPermissionIfNeeded()

        collectOnStart(viewModel.trackingState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                is LiveTrackingViewModel.Event.Saved -> onSaved(event.clientUuid)
                LiveTrackingViewModel.Event.SaveFailed -> onSaveFailed()
                LiveTrackingViewModel.Event.Discarded -> findNavController().popBackStack()
            }
        }

        // Swallow the system back gesture while locked.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { /* intentional no-op */ }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cancelHoldTick()
        backCallback?.remove()
        backCallback = null
        binding.mapView.onDetach()
        routeRenderer = null
        mapCamera = null
        super.onDestroyView()
    }

    // ---------- insets ----------

    private fun applyInsetsPerLayer() {
        val overlayTop = binding.trackingTopOverlay.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.trackingTopOverlay) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, overlayTop + bars.top, v.paddingRight, v.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }
        val panelBottom = binding.trackingPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.trackingPanel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, panelBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.trackingTopOverlay)
        ViewCompat.requestApplyInsets(binding.trackingPanel)
    }

    // ---------- tracking start / stop ----------

    private fun startTrackingIfIdle() {
        val state = viewModel.trackingState.value
        if (state.status == TrackingState.Status.Idle ||
            state.status == TrackingState.Status.Stopped
        ) {
            if (state.status == TrackingState.Status.Stopped) {
                (requireContext().applicationContext as FitnessApp).container
                    .trackingSession.reset()
            }
            val intent = LocationTrackingService.startIntent(requireContext(), activityType)
            ContextCompat.startForegroundService(requireContext(), intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (LocationPermissions.isNotificationGranted(requireContext())) return
        notificationLauncher.launch(LocationPermissions.NOTIFICATION_PERMISSION)
    }

    // ---------- render ----------

    private fun render(state: TrackingState) {
        val isPaused = state.status == TrackingState.Status.Paused
        binding.gpsQualityLabel.setText(
            when {
                isPaused -> R.string.tracking_paused_pill
                else -> when (state.gpsQuality) {
                    TrackingState.GpsQuality.Searching -> R.string.live_tracking_gps_searching
                    TrackingState.GpsQuality.Strong -> R.string.live_tracking_gps_strong
                    TrackingState.GpsQuality.Weak -> R.string.live_tracking_gps_weak
                }
            }
        )
        val dotColor = when {
            isPaused -> R.color.text_on_dark_muted
            state.gpsQuality == TrackingState.GpsQuality.Strong -> R.color.lime_accent
            state.gpsQuality == TrackingState.GpsQuality.Weak -> R.color.error_material
            else -> R.color.text_on_dark_muted
        }
        binding.gpsQualityDot.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), dotColor)

        binding.elapsedText.text = formatElapsed(state.elapsedMs)
        binding.distanceText.text = String.format(
            Locale.getDefault(), "%.2f", state.distanceMeters / 1000.0,
        )
        binding.paceText.text = formatPrimaryMetric(state)
        binding.caloriesText.text = estimateCalories(state).toString()

        updateRoute(state.points)
        updatePauseResumeIcon(state.status)
    }

    private fun formatPrimaryMetric(state: TrackingState): String =
        when (state.activityType) {
            ActivityType.CYCLING -> {
                val kmh = state.currentSpeedKmh
                if (kmh == null) "—" else Formatters.formatSpeed(kmh)
            }
            else -> {
                val pace = state.currentPaceMsPerKm
                if (pace == null) "—" else Formatters.formatPace(pace / 1000)
            }
        }

    private fun estimateCalories(state: TrackingState): Int {
        val kg = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value
        val meters = state.distanceMeters.coerceAtLeast(1.0)
        val duration = java.time.Duration.ofMillis(state.elapsedMs.coerceAtLeast(1_000L))
        val workout = when (state.activityType) {
            ActivityType.RUNNING -> com.elijah.fitness.data.model.RunningWorkout(
                id = "live", startTime = java.time.Instant.now(),
                duration = duration, distanceMeters = meters, notes = null,
            )
            ActivityType.CYCLING -> com.elijah.fitness.data.model.CyclingWorkout(
                id = "live", startTime = java.time.Instant.now(),
                duration = duration, distanceMeters = meters, notes = null,
            )
            else -> return 0
        }
        return workout.caloriesBurned(kg).toInt()
    }

    private fun setActivityLabels() {
        binding.trackingElapsedEyebrow.setText(
            when (activityType) {
                ActivityType.CYCLING -> R.string.tracking_elapsed_eyebrow_ride
                else -> R.string.tracking_elapsed_eyebrow_run
            }
        )
        binding.paceLabel.setText(
            when (activityType) {
                ActivityType.CYCLING -> R.string.tracking_stat_speed
                else -> R.string.tracking_stat_pace
            }
        )
        binding.paceUnit.setText(
            when (activityType) {
                ActivityType.CYCLING -> R.string.tracking_unit_kmh
                else -> R.string.tracking_unit_pace_slash_km
            }
        )
    }

    private fun updatePauseResumeIcon(status: TrackingState.Status) {
        val paused = status == TrackingState.Status.Paused
        binding.pauseResumeButton.setImageResource(
            if (paused) R.drawable.ic_play_triangle_24 else R.drawable.ic_pause_24
        )
        binding.pauseResumeButton.contentDescription = getString(
            if (paused) R.string.tracking_control_resume else R.string.tracking_control_pause
        )
        binding.pauseResumeLabel.setText(
            if (paused) R.string.tracking_control_resume else R.string.tracking_control_pause
        )
    }

    // ---------- controls ----------

    private fun onPauseResumeTapped() {
        val status = viewModel.trackingState.value.status
        val intent = when (status) {
            TrackingState.Status.Tracking -> LocationTrackingService.pauseIntent(requireContext())
            TrackingState.Status.Paused -> LocationTrackingService.resumeIntent(requireContext())
            else -> return
        }
        requireContext().startService(intent)
    }

    private fun confirmFinish() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.live_tracking_stop_confirm_title)
            .setMessage(R.string.live_tracking_stop_confirm_body)
            .setPositiveButton(R.string.live_tracking_stop_confirm_positive) { _, _ -> doFinish() }
            .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
            .show()
    }

    private fun doFinish() {
        // Stop the service first so the final elapsed / points snapshot
        // is on the session, then save straight away.
        requireContext().startService(LocationTrackingService.stopIntent(requireContext()))
        viewModel.save()
    }

    private fun onRecenterTapped() {
        mapCamera?.followCurrent(true)
        val last = viewModel.trackingState.value.points.lastOrNull() ?: return
        mapCamera?.followCurrentPosition(GeoPoint(last.latitude, last.longitude))
    }

    // ---------- lock ----------

    private fun enterLock() {
        if (isLocked) return
        isLocked = true
        binding.lockOverlay.isVisible = true
        backCallback?.isEnabled = true
    }

    private fun exitLock() {
        if (!isLocked) return
        isLocked = false
        binding.lockOverlay.isVisible = false
        binding.lockHoldProgress.isVisible = false
        binding.lockHoldProgress.progress = 0
        backCallback?.isEnabled = false
    }

    private fun cancelHoldTick() {
        holdHandler.removeCallbacks(holdTick)
        _bindingOrNull?.lockHoldProgress?.let {
            it.isVisible = false
            it.progress = 0
        }
    }

    // ---------- save / events ----------

    private fun onSaved(clientUuid: String) {
        findNavController().navigate(
            R.id.action_live_tracking_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    private fun onSaveFailed() {
        Snackbar.make(
            binding.root,
            R.string.live_tracking_save_failed_snackbar,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    // ---------- map ----------

    private fun configureOsmdroid() {
        DarkMapStyler.apply(binding.mapView, requireContext())
    }

    private fun setupMap() {
        binding.mapView.controller.setZoom(16.0)
        binding.mapView.controller.setCenter(GeoPoint(51.5074, -0.1278))
        mapCamera = MapCameraController(binding.mapView)
    }

    private fun updateRoute(points: List<TrackPoint>) {
        val renderer = routeRenderer ?: RoutePolylineRenderer(binding.mapView).also {
            routeRenderer = it
        }
        renderer.render(points)
        val last = points.lastOrNull() ?: return
        mapCamera?.followCurrentPosition(GeoPoint(last.latitude, last.longitude))
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = ms / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"
        private const val HOLD_TO_UNLOCK_MS = 1_000L
    }
}
