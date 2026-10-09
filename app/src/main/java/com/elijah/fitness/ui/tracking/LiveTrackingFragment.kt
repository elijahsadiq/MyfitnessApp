package com.elijah.fitness.ui.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.databinding.FragmentLiveTrackingBinding
import com.elijah.fitness.tracking.LocationPermissions
import com.elijah.fitness.tracking.LocationTrackingService
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.tracking.TrackingState
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.elijah.fitness.ui.main.MainActivity
import com.elijah.fitness.ui.tracking.map.DarkMapStyler
import com.elijah.fitness.ui.tracking.map.MapCameraController
import com.elijah.fitness.ui.tracking.map.RoutePolylineRenderer
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.Fonts
import com.elijah.fitness.util.MetricText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.util.GeoPoint
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Live tracking screen (phase 8c step 6, audit rework).
 *
 * - Map fills the screen behind the status bar and the panel.
 * - Top overlay: GPS pill (left) + minimise chevron (right).
 * - Re-centre button + OSM credit ride just above the panel.
 * - Dark bottom panel with the elapsed time, three stats, and three
 *   controls. While tracking: Finish · Pause/Resume · Lock. Once the run
 *   is finished but not yet saved (a save failed): Discard · Save.
 * - Lock overlay with "Hold to unlock" — covers taps, blocks the back
 *   gesture, and the map / stats keep updating underneath.
 *
 * A session only ever starts here when none exists. Minimising, rotating
 * or coming back from the notification re-attaches to the running one.
 */
class LiveTrackingFragment : BaseFragment<FragmentLiveTrackingBinding>() {

    private val requestedActivityType: ActivityType by lazy {
        arguments?.getString(ARG_ACTIVITY_TYPE)
            ?.let { runCatching { ActivityType.valueOf(it) }.getOrNull() }
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

    /** Last route drawn — redraw only when a new point arrives, not every tick. */
    private var renderedPointCount = -1
    private var renderedLastPoint: TrackPoint? = null

    private var locationOffSnackbar: Snackbar? = null

    // Lock-overlay state. View-local — a rotation re-shows the panel
    // with the overlay dismissed; the service carries on either way.
    private var isLocked: Boolean = false
    private var backCallback: OnBackPressedCallback? = null

    // Hold-to-unlock pulse animation.
    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdStartMs: Long = 0L
    private val holdTick: Runnable = object : Runnable {
        override fun run() {
            val b = bindingOrNull ?: return
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

    /** Checks the service actually started; see [startTrackingIfIdle]. */
    private val startWatchdog = Runnable {
        if (bindingOrNull != null &&
            viewModel.trackingState.value.status == TrackingState.Status.Idle
        ) {
            onTrackingCouldNotStart()
        }
    }

    /** Lighter weight for the small unit next to a big number ("4.12 km"). */
    private val unitFont: android.graphics.Typeface
        get() = Fonts.medium(requireContext())

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLiveTrackingBinding =
        FragmentLiveTrackingBinding.inflate(inflater, container, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsetsPerLayer()
        setupMap()

        binding.minimiseButton.setOnClickListener { findNavController().navigateUp() }
        binding.pauseResumeButton.setOnClickListener { onPrimaryTapped() }
        binding.finishButton.setOnClickListener { onSecondaryTapped() }
        binding.lockButton.setOnClickListener { enterLock() }
        binding.recenterButton.setOnClickListener { onRecenterTapped() }

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

        collectOnStart(viewModel.trackingState) { render(it) }
        collectOnStart(viewModel.isSaving) { renderControls(viewModel.trackingState.value) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                is LiveTrackingViewModel.Event.Saved -> onSaved(event.clientUuid)
                LiveTrackingViewModel.Event.SaveFailed -> onSaveFailed()
                LiveTrackingViewModel.Event.Discarded -> findNavController().navigateUp()
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
        checkLocationServices()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        holdHandler.removeCallbacks(startWatchdog)
        cancelHoldTick()
        locationOffSnackbar = null
        backCallback?.remove()
        backCallback = null
        binding.mapView.onDetach()
        routeRenderer = null
        mapCamera = null
        renderedPointCount = -1
        renderedLastPoint = null
        super.onDestroyView()
    }

    // ---------- insets ----------

    private fun applyInsetsPerLayer() {
        val overlayTop = binding.trackingTopOverlay.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.trackingTopOverlay) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = overlayTop + bars.top)
            insets
        }
        val panelBottom = binding.trackingPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.trackingPanel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = panelBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    // ---------- tracking start ----------

    private fun startTrackingIfIdle() {
        if (viewModel.trackingState.value.status != TrackingState.Status.Idle) return
        val ctx = requireContext()
        if (!LocationPermissions.hasAnyLocationPermission(ctx)) {
            onTrackingCouldNotStart()
            return
        }
        try {
            ContextCompat.startForegroundService(
                ctx, LocationTrackingService.startIntent(ctx, requestedActivityType),
            )
        } catch (_: IllegalStateException) {
            // Android 12+ refuses foreground-service starts from the
            // background; only reachable if the screen is being restored
            // while the app isn't visible.
            onTrackingCouldNotStart()
            return
        }
        holdHandler.postDelayed(startWatchdog, START_TIMEOUT_MS)
        requestNotificationPermissionIfNeeded()
    }

    private fun onTrackingCouldNotStart() {
        val activity = activity ?: return
        (activity as? MainActivity)?.showSnackbar(getString(R.string.tracking_could_not_start))
        // Posted: this can run from onViewCreated, mid fragment transaction.
        view?.post { if (isAdded) findNavController().navigateUp() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (LocationPermissions.isNotificationGranted(requireContext())) return
        notificationLauncher.launch(LocationPermissions.NOTIFICATION_PERMISSION)
    }

    /**
     * GPS can't work with the phone's location switch off. Rather than
     * sit on "GPS searching" forever, say so and offer the setting.
     */
    private fun checkLocationServices() {
        val container = (requireContext().applicationContext as FitnessApp).container
        val enabled = container.locationTracker.isLocationEnabled()
        if (enabled) {
            locationOffSnackbar?.dismiss()
            locationOffSnackbar = null
            return
        }
        if (locationOffSnackbar?.isShownOrQueued == true) return
        locationOffSnackbar = Snackbar.make(
            binding.root, R.string.tracking_location_off, Snackbar.LENGTH_INDEFINITE,
        ).setAction(R.string.tracking_location_turn_on) {
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }.setAnchorView(binding.trackingPanel).also { it.show() }
    }

    // ---------- render ----------

    private fun render(state: TrackingState) {
        val activityType =
            if (state.status == TrackingState.Status.Idle) requestedActivityType else state.activityType
        renderLabels(activityType)
        renderGpsPill(state)

        binding.elapsedText.text = formatElapsed(state.elapsedMs)
        val muted = ContextCompat.getColor(requireContext(), R.color.goal_main_muted)
        binding.distanceText.text = MetricText.of(
            String.format(Locale.getDefault(), "%.2f", state.distanceMeters / 1000.0),
            getString(R.string.tracking_unit_km), muted,
            unitTypeface = unitFont,
        )
        binding.paceText.text = formatPrimaryMetric(state, activityType, muted)
        binding.caloriesText.text = MetricText.of(
            estimateCalories(state, activityType).toString(),
            getString(R.string.tracking_unit_kcal), muted,
            unitTypeface = unitFont,
        )

        updateRoute(state.points)
        renderControls(state)
    }

    private fun renderGpsPill(state: TrackingState) {
        binding.gpsQualityLabel.setText(
            when (state.status) {
                TrackingState.Status.Paused -> R.string.tracking_paused_pill
                TrackingState.Status.Stopped -> R.string.tracking_finished_pill
                else -> when (state.gpsQuality) {
                    TrackingState.GpsQuality.Searching -> R.string.live_tracking_gps_searching
                    TrackingState.GpsQuality.Strong -> R.string.live_tracking_gps_strong
                    TrackingState.GpsQuality.Weak -> R.string.live_tracking_gps_weak
                }
            }
        )
        val dotColor = when {
            state.status == TrackingState.Status.Paused ||
                state.status == TrackingState.Status.Stopped -> R.color.goal_main_muted
            state.gpsQuality == TrackingState.GpsQuality.Strong -> R.color.lime_accent
            state.gpsQuality == TrackingState.GpsQuality.Weak -> R.color.error_red
            else -> R.color.goal_main_muted
        }
        binding.gpsQualityDot.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), dotColor)
    }

    private fun renderLabels(activityType: ActivityType) {
        binding.trackingElapsedEyebrow.setText(
            if (activityType == ActivityType.CYCLING) R.string.activity_type_cycling
            else R.string.activity_type_running
        )
        binding.paceLabel.setText(
            if (activityType == ActivityType.CYCLING) R.string.tracking_stat_speed
            else R.string.tracking_stat_pace
        )
    }

    /** Buttons follow the session: tracking → paused → finished (unsaved). */
    private fun renderControls(state: TrackingState) {
        val b = bindingOrNull ?: return
        val saving = viewModel.isSaving.value
        val finished = state.status == TrackingState.Status.Stopped

        // Centre (primary): Pause / Resume / Save.
        val (primaryIcon, primaryLabel) = when {
            finished -> R.drawable.ic_check_24 to
                if (saving) R.string.tracking_control_saving else R.string.tracking_control_save
            state.status == TrackingState.Status.Paused ->
                R.drawable.ic_play_triangle_24 to R.string.tracking_control_resume
            else -> R.drawable.ic_pause_24 to R.string.tracking_control_pause
        }
        b.pauseResumeButton.setImageResource(primaryIcon)
        b.pauseResumeButton.imageAlpha = if (saving) 0 else 255
        b.saveProgress.isVisible = saving
        b.pauseResumeLabel.setText(primaryLabel)
        b.pauseResumeButton.contentDescription = getString(primaryLabel)
        b.pauseResumeButton.isEnabled = !saving && state.status != TrackingState.Status.Idle

        // Left (secondary): Finish while running, Discard once finished.
        b.finishButton.setImageResource(if (finished) R.drawable.ic_delete_24 else R.drawable.ic_stop_square_24)
        b.finishLabel.setText(if (finished) R.string.tracking_control_discard else R.string.tracking_control_finish)
        b.finishButton.contentDescription = getString(
            if (finished) R.string.tracking_discard_content_description
            else R.string.tracking_finish_content_description
        )
        b.finishButton.isEnabled = !saving && state.status != TrackingState.Status.Idle
        b.finishButton.alpha = if (b.finishButton.isEnabled) 1f else 0.4f

        // Right: Lock only makes sense while the run is live.
        val lockVisible = !finished
        b.lockButton.visibility = if (lockVisible) View.VISIBLE else View.INVISIBLE
        b.lockLabel.visibility = if (lockVisible) View.VISIBLE else View.INVISIBLE
    }

    private fun formatPrimaryMetric(
        state: TrackingState,
        activityType: ActivityType,
        unitColor: Int,
    ): CharSequence = when (activityType) {
        ActivityType.CYCLING -> {
            val kmh = state.currentSpeedKmh
            MetricText.of(
                if (kmh == null) DASH else String.format(Locale.getDefault(), "%.1f", kmh),
                getString(R.string.tracking_unit_kmh), unitColor,
                unitTypeface = unitFont,
            )
        }
        else -> {
            val pace = state.currentPaceMsPerKm
            MetricText.of(
                if (pace == null) DASH else Formatters.formatPaceValue(pace / 1000),
                getString(R.string.tracking_unit_pace_slash_km), unitColor,
                unitTypeface = unitFont,
            )
        }
    }

    private fun estimateCalories(state: TrackingState, activityType: ActivityType): Int {
        if (state.distanceMeters <= 0.0) return 0
        val kg = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value
        val duration = Duration.ofMillis(state.elapsedMs.coerceAtLeast(1_000L))
        val workout = when (activityType) {
            ActivityType.CYCLING -> CyclingWorkout(
                id = "live", startTime = Instant.EPOCH,
                duration = duration, distanceMeters = state.distanceMeters,
            )
            else -> RunningWorkout(
                id = "live", startTime = Instant.EPOCH,
                duration = duration, distanceMeters = state.distanceMeters,
            )
        }
        return workout.caloriesBurned(kg).toInt()
    }

    // ---------- controls ----------

    private fun onPrimaryTapped() {
        when (viewModel.trackingState.value.status) {
            TrackingState.Status.Tracking -> viewModel.pause()
            TrackingState.Status.Paused -> viewModel.resume()
            TrackingState.Status.Stopped -> viewModel.save()
            TrackingState.Status.Idle -> Unit
        }
    }

    private fun onSecondaryTapped() {
        when (viewModel.trackingState.value.status) {
            TrackingState.Status.Tracking,
            TrackingState.Status.Paused -> confirmFinish()
            TrackingState.Status.Stopped -> confirmDiscard()
            TrackingState.Status.Idle -> Unit
        }
    }

    private fun confirmFinish() {
        if (!viewModel.hasEnoughDistance()) {
            // Nothing worth saving yet — a "0.00 km" run only clutters
            // History (and gives an absurd pace). Offer Discard instead.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tracking_no_distance_title)
                .setMessage(R.string.tracking_no_distance_body)
                .setPositiveButton(R.string.tracking_control_discard) { _, _ -> discard() }
                .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.live_tracking_stop_confirm_title)
            .setMessage(R.string.live_tracking_stop_confirm_body)
            .setPositiveButton(R.string.live_tracking_stop_confirm_positive) { _, _ -> finish() }
            .setNegativeButton(R.string.live_tracking_stop_confirm_negative, null)
            .show()
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.live_tracking_discard_confirm_title)
            .setMessage(R.string.live_tracking_discard_confirm_body)
            .setPositiveButton(R.string.live_tracking_discard_confirm_positive) { _, _ -> discard() }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun finish() {
        // Freeze the session first so the saved numbers are final, then
        // stop GPS + the notification.
        viewModel.finish()
        stopTrackingService()
    }

    private fun discard() {
        stopTrackingService()
        viewModel.discard()
    }

    private fun stopTrackingService() {
        val ctx = context ?: return
        ctx.stopService(LocationTrackingService.serviceIntent(ctx))
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
        val b = bindingOrNull ?: return
        b.lockOverlay.isVisible = false
        b.lockHoldProgress.isVisible = false
        b.lockHoldProgress.progress = 0
        backCallback?.isEnabled = false
    }

    private fun cancelHoldTick() {
        holdHandler.removeCallbacks(holdTick)
        bindingOrNull?.lockHoldProgress?.let {
            it.visibility = View.INVISIBLE
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
        ).setAction(R.string.log_error_save_retry) { viewModel.save() }
            .setAnchorView(binding.trackingPanel)
            .show()
    }

    // ---------- map ----------

    private fun setupMap() {
        DarkMapStyler.apply(binding.mapView, requireContext())
        mapCamera = MapCameraController(binding.mapView)

        // The panel covers the bottom of the map, so centre the followed
        // position in the part you can actually see (between the top
        // pills and the panel), not the middle of the whole screen.
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val b = bindingOrNull ?: return@addOnLayoutChangeListener
            val offsetY = (b.trackingTopOverlay.bottom - b.trackingBottomStack.height) / 2
            if (b.mapView.mapCenterOffsetY != offsetY) b.mapView.setMapCenterOffset(0, offsetY)
        }

        val existing = viewModel.trackingState.value.points.lastOrNull()
        if (existing != null) {
            binding.mapView.controller.setZoom(FOLLOW_ZOOM)
            binding.mapView.controller.setCenter(GeoPoint(existing.latitude, existing.longitude))
            return
        }
        // No fix yet: start on the phone's last known position instead of
        // a hard-coded city; the camera follows once real fixes arrive.
        binding.mapView.controller.setZoom(WORLD_ZOOM)
        binding.mapView.controller.setCenter(GeoPoint(WORLD_CENTER_LAT, WORLD_CENTER_LON))
        val container = (requireContext().applicationContext as FitnessApp).container
        container.locationTracker.lastKnownLocation { loc ->
            val b = bindingOrNull ?: return@lastKnownLocation
            if (loc == null || viewModel.trackingState.value.points.isNotEmpty()) return@lastKnownLocation
            b.mapView.controller.setZoom(FOLLOW_ZOOM)
            b.mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
        }
    }

    private fun updateRoute(points: List<TrackPoint>) {
        val last = points.lastOrNull()
        if (points.size == renderedPointCount && last == renderedLastPoint) return
        renderedPointCount = points.size
        renderedLastPoint = last

        val renderer = routeRenderer ?: RoutePolylineRenderer(binding.mapView).also {
            routeRenderer = it
        }
        renderer.render(points)
        if (last == null) return
        if (binding.mapView.zoomLevelDouble < FOLLOW_ZOOM - 4) {
            binding.mapView.controller.setZoom(FOLLOW_ZOOM)
        }
        mapCamera?.followCurrentPosition(GeoPoint(last.latitude, last.longitude))
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = ms / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"
        private const val HOLD_TO_UNLOCK_MS = 1_000L
        private const val START_TIMEOUT_MS = 4_000L
        private const val FOLLOW_ZOOM = 17.0
        private const val WORLD_ZOOM = 3.0
        private const val WORLD_CENTER_LAT = 20.0
        private const val WORLD_CENTER_LON = 0.0
        private const val DASH = "—"
    }
}
