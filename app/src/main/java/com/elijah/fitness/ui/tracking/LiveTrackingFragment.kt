package com.elijah.fitness.ui.tracking

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
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
import com.elijah.fitness.ui.main.MainActivity
import com.elijah.fitness.util.Formatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

/**
 * Live tracking screen. One fragment, two visual modes:
 *   - **Tracking** — map updates in real time, pause/resume + stop controls.
 *   - **Save** — same stats held static, notes field + save/discard.
 *
 * The user never leaves this screen between stopping and saving — the
 * single-fragment approach keeps the data path simple and makes retrying
 * a failed save trivial (nothing has been navigated-away-from).
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

    private var routeOverlay: Polyline? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLiveTrackingBinding =
        FragmentLiveTrackingBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Phase 8c step 2 — edge-to-edge. The root CoordinatorLayout
        // paints `surface_dark` behind the status bar + gesture bar;
        // this listener pushes the actual content down below the
        // status-bar inset and up above the gesture-bar inset so
        // controls aren't clipped. MainActivity flips the status-bar
        // icon colour via its `DARK_STATUS_DESTINATIONS` set.
        applySystemBarInsets(target = binding.root)

        configureOsmdroid()
        setupMap()

        binding.closeButton.setOnClickListener { onCloseRequested() }
        binding.pauseResumeButton.setOnClickListener { onPauseResumeTapped() }
        binding.stopButton.setOnClickListener { confirmStop() }
        binding.saveButton.setOnClickListener { onSaveTapped() }
        binding.discardButton.setOnClickListener { confirmDiscard() }

        setPaceLabel()
        startTrackingIfIdle()
        requestNotificationPermissionIfNeeded()

        collectOnStart(viewModel.trackingState) { render(it) }
        collectOnStart(viewModel.isSaving) { saving ->
            binding.saveButton.isEnabled = !saving
            binding.discardButton.isEnabled = !saving
        }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                LiveTrackingViewModel.Event.Saved -> onSaved()
                LiveTrackingViewModel.Event.SaveFailed -> onSaveFailed()
                LiveTrackingViewModel.Event.Discarded -> findNavController().popBackStack()
            }
        }

        // Keep the screen awake while tracking.
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
        binding.mapView.onDetach()
        routeOverlay = null
        super.onDestroyView()
    }

    private fun startTrackingIfIdle() {
        val state = viewModel.trackingState.value
        if (state.status == TrackingState.Status.Idle ||
            state.status == TrackingState.Status.Stopped
        ) {
            // Reset wipes a leftover Stopped session before the user re-enters.
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

    private fun render(state: TrackingState) {
        binding.gpsQualityPill.setText(
            when (state.gpsQuality) {
                TrackingState.GpsQuality.Searching -> R.string.live_tracking_gps_searching
                TrackingState.GpsQuality.Strong -> R.string.live_tracking_gps_strong
                TrackingState.GpsQuality.Weak -> R.string.live_tracking_gps_weak
            }
        )

        binding.elapsedText.text = formatElapsed(state.elapsedMs)
        binding.distanceText.text = getString(
            R.string.live_tracking_distance_value, state.distanceMeters / 1000.0,
        )
        binding.paceText.text = formatMetricForActivity(state)
        binding.caloriesText.text = estimateCalories(state).toString()

        updateRoute(state.points)
        updateControlsForStatus(state.status)
    }

    private fun formatMetricForActivity(state: TrackingState): String =
        when (state.activityType) {
            ActivityType.RUNNING -> {
                val pace = state.currentPaceMsPerKm
                if (pace == null) "—" else Formatters.formatPace(pace / 1000)
            }
            ActivityType.CYCLING -> {
                val kmh = state.currentSpeedKmh
                if (kmh == null) "—" else Formatters.formatSpeed(kmh)
            }
            else -> "—"
        }

    private fun estimateCalories(state: TrackingState): Int {
        // Reuse the domain hierarchy's MET-based calorie model by building a
        // temporary workout and asking it. Avoids duplicating the formula.
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

    private fun setPaceLabel() {
        binding.paceLabel.setText(
            when (activityType) {
                ActivityType.CYCLING -> R.string.live_tracking_speed_label
                else -> R.string.live_tracking_pace_label
            }
        )
    }

    private fun updateControlsForStatus(status: TrackingState.Status) {
        val tracking = status == TrackingState.Status.Tracking || status == TrackingState.Status.Paused
        binding.trackingControls.isVisible = tracking
        binding.saveControls.isVisible = status == TrackingState.Status.Stopped
        binding.pauseResumeButton.setText(
            when (status) {
                TrackingState.Status.Paused -> R.string.live_tracking_resume_button
                else -> R.string.live_tracking_pause_button
            }
        )
    }

    private fun onPauseResumeTapped() {
        val status = viewModel.trackingState.value.status
        val intent = when (status) {
            TrackingState.Status.Tracking -> LocationTrackingService.pauseIntent(requireContext())
            TrackingState.Status.Paused -> LocationTrackingService.resumeIntent(requireContext())
            else -> return
        }
        requireContext().startService(intent)
    }

    private fun confirmStop() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.live_tracking_stop_confirm_title)
            .setMessage(R.string.live_tracking_stop_confirm_body)
            .setPositiveButton(R.string.live_tracking_stop_confirm_positive) { _, _ -> doStop() }
            .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
            .show()
    }

    private fun doStop() {
        requireContext().startService(LocationTrackingService.stopIntent(requireContext()))
    }

    private fun onSaveTapped() {
        val state = viewModel.trackingState.value
        if (state.points.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.live_tracking_discard_confirm_title)
                .setMessage(R.string.live_tracking_no_route_warning)
                .setPositiveButton(R.string.live_tracking_save_button) { _, _ -> doSave() }
                .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
                .show()
            return
        }
        doSave()
    }

    private fun doSave() {
        val notes = binding.notesInput.text?.toString().orEmpty()
        viewModel.save(notes)
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.live_tracking_discard_confirm_title)
            .setMessage(R.string.live_tracking_discard_confirm_body)
            .setPositiveButton(R.string.live_tracking_discard_confirm_positive) { _, _ ->
                viewModel.discard()
            }
            .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
            .show()
    }

    private fun onCloseRequested() {
        when (viewModel.trackingState.value.status) {
            TrackingState.Status.Tracking,
            TrackingState.Status.Paused -> confirmStop()
            else -> findNavController().popBackStack()
        }
    }

    private fun onSaved() {
        (requireActivity() as? MainActivity)?.showSnackbar(
            getString(R.string.live_tracking_saved_snackbar)
        )
        findNavController().popBackStack()
    }

    private fun onSaveFailed() {
        Snackbar.make(
            binding.root,
            R.string.live_tracking_save_failed_snackbar,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    // -----------------------------------------------------------------
    // Map
    // -----------------------------------------------------------------

    private fun configureOsmdroid() {
        val ctx = requireContext().applicationContext
        val prefs = ctx.getSharedPreferences("osmdroid", 0)
        Configuration.getInstance().load(ctx, prefs)
        Configuration.getInstance().userAgentValue = ctx.packageName
    }

    private fun setupMap() {
        with(binding.mapView) {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            // London fallback until the first fix lands.
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            setBuiltInZoomControls(false)
        }
    }

    private fun updateRoute(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        val overlay = routeOverlay ?: Polyline(binding.mapView).also { poly ->
            poly.outlinePaint.strokeWidth = 10f
            poly.outlinePaint.color =
                ContextCompat.getColor(requireContext(), R.color.lime_accent)
            binding.mapView.overlays.add(poly)
            routeOverlay = poly
        }
        overlay.setPoints(geoPoints)
        binding.mapView.controller.animateTo(geoPoints.last())
        binding.mapView.invalidate()
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = ms / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"

        @Suppress("UNUSED_PARAMETER")
        fun unused(intent: Intent) {}
    }
}
