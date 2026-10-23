package com.elijah.fitness.ui.log

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.BottomSheetChooseActivityBinding
import com.elijah.fitness.tracking.LocationPermissions
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.navigateSafe
import com.elijah.fitness.ui.base.withKind
import com.elijah.fitness.ui.components.ActivityCardItem
import com.elijah.fitness.ui.components.ActivityCardSpec
import com.elijah.fitness.ui.components.CarouselNavButtons
import com.elijah.fitness.ui.components.SheetScrollGuard
import com.elijah.fitness.util.ActivityLabels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * "Log a workout" bottom sheet (phase 8d, LogSheet.dc.html). Home's
 * "Start workout" and the History FAB both open it.
 *
 * Swipeable activity cards (Running, Cycling, Swimming, Rowing,
 * Workout), prev / next buttons with dots, then either the "Live with
 * GPS" / "Add manually" choice (runs, rides, rows) or a one-line note
 * (swims, workouts), and one lime button whose label says exactly what
 * it will do ("Start ride with GPS", "Add a swim", …). The rules behind
 * the label live in [LogSheetOptions]; the selection in
 * [ChooseActivityViewModel].
 *
 * Stays a `BottomSheetDialogFragment` (not a `BaseFragment`): Android's
 * dialog sheet has to extend Material's class.
 *
 * ### One session at a time
 * If a GPS workout is already running (or finished but not saved), the
 * sheet skips itself and goes straight back to the tracking screen.
 *
 * ### Opens on the last activity logged
 * Read from DataStore through [ChooseActivityViewModel]; Running for a
 * new user. The value is written by the save paths themselves
 * ([com.elijah.fitness.AppContainer.rememberLoggedActivity]), so opening
 * a form and backing out doesn't change it. The sheet opens fully
 * expanded so the button shows without dragging.
 */
class ChooseActivityBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseActivityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChooseActivityViewModel by viewModels {
        ChooseActivityViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container,
        )
    }

    /** The carousel is built once, from the first state the ViewModel emits. */
    private var carouselReady = false

    /** Activity waiting for the location permission answer. */
    private var pendingGpsActivity: ActivityType? = null

    /**
     * True from the moment the location permission dialog is requested
     * until its answer arrives. A second tap in between (a double tap
     * before the system dialog appears) is ignored: launching the
     * request again makes Android cancel it at once with an empty
     * answer, which read as "denied" and showed a false "Location is
     * off" snackbar.
     */
    private var awaitingLocationAnswer = false

    private val locationLauncher = LocationPermissions.registerLocationLauncher(this) { outcome ->
        awaitingLocationAnswer = false
        when (outcome) {
            // "Approximate" alone can't track a route, but the user chose
            // it — start anyway; the screen will just show weak GPS.
            LocationPermissions.Outcome.FineGranted,
            LocationPermissions.Outcome.CoarseOnly -> onTrackWithGpsConfirmed()
            LocationPermissions.Outcome.Denied -> onLocationDenied()
        }
    }

    /** The design dims the page behind this sheet more than the other sheets. */
    override fun getTheme(): Int = R.style.ThemeOverlay_Fitness_BottomSheetDialog_LogSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetChooseActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        carouselReady = false

        val container = (requireContext().applicationContext as FitnessApp).container

        // One session at a time: if a GPS workout is running — or is
        // finished but not saved yet — skip the sheet and jump back into
        // it, so it can't be forgotten or overwritten.
        val liveState = container.trackingSession.state.value
        if (liveState.isActive) {
            findNavController().navigateSafe(
                R.id.action_choose_to_live_tracking,
                bundleOf(ARG_ACTIVITY_TYPE to liveState.activityType.name),
            )
            dismiss()
            return
        }

        // Fully open, and a drag down closes it (no half-way stop). A
        // short drag on the body springs back instead of closing.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        SheetScrollGuard.install(binding.logSheetScroll, (dialog as? BottomSheetDialog)?.behavior)

        ViewCompat.setAccessibilityHeading(binding.logSheetTitle, true)
        binding.logSheetDots.count = LogSheetOptions.activities.size
        CarouselNavButtons.bind(
            previous = binding.logSheetPrevious,
            next = binding.logSheetNext,
            carousel = binding.logSheetCarousel,
        )

        binding.logSheetModeControl.contentDescription = getString(R.string.log_sheet_mode_group)
        binding.logSheetModeControl.setOptions(
            LogSheetOptions.modes.map { mode ->
                getString(
                    when (mode) {
                        LogMode.LIVE -> R.string.log_sheet_mode_live
                        LogMode.MANUAL -> R.string.log_sheet_mode_manual
                    },
                )
            },
        )
        binding.logSheetModeControl.setOnSegmentSelectedListener { index ->
            LogSheetOptions.modes.getOrNull(index)?.let(viewModel::setMode)
        }

        binding.logSheetCta.setOnClickListener { onCtaClicked() }

        // Hidden for the frame or two DataStore takes, so the sheet never
        // shows Running before jumping to the remembered activity.
        binding.logSheetContent.isInvisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> if (state != null) render(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- rendering ----------

    private fun render(state: LogSheetUiState) {
        val b = _binding ?: return
        val count = LogSheetOptions.activities.size
        if (!carouselReady) {
            carouselReady = true
            b.logSheetCarousel.setUp(
                spec = ActivityCardSpec.LOG_SHEET,
                items = LogSheetOptions.activities.map(::cardFor),
                selected = state.selectedIndex,
            ) { position, _ -> viewModel.select(position) }
            b.logSheetDots.setSelectedIndex(state.selectedIndex, animate = false)
            b.logSheetContent.isInvisible = false
        } else {
            // The carousel drives the selection; only follow it here.
            b.logSheetDots.setSelectedIndex(state.selectedIndex)
        }
        CarouselNavButtons.update(
            previous = b.logSheetPrevious,
            next = b.logSheetNext,
            index = state.selectedIndex,
            count = count,
        )

        b.logSheetModeControl.isVisible = state.showsModeChoice
        b.logSheetModeControl.setSelectedIndex(LogSheetOptions.modes.indexOf(state.mode))
        val note = state.noteRes
        b.logSheetNote.isVisible = note != null
        if (note != null) b.logSheetNote.setText(note)

        b.logSheetCta.setText(state.ctaLabelRes)
    }

    private fun cardFor(type: ActivityType) = ActivityCardItem(
        activity = type,
        iconRes = ActivityLabels.cardIconRes(type),
        nameRes = ActivityLabels.nameRes(type),
        descriptionRes = LogSheetOptions.descriptionRes(type),
    )

    // ---------- intent handlers ----------

    private fun onCtaClicked() {
        val state = viewModel.state.value ?: return
        val type = state.activity
        when (state.destination) {
            LogDestination.LIVE_TRACKING -> onTrackWithGpsClicked(type)
            LogDestination.MANUAL_DISTANCE -> navigateTo(type, R.id.action_choose_to_log_distance)
            LogDestination.SWIM -> navigateTo(type, R.id.action_choose_to_log_swim)
            LogDestination.STRENGTH -> navigateTo(type, R.id.action_choose_to_log_strength)
        }
    }

    private fun onTrackWithGpsClicked(type: ActivityType) {
        // The permission dialog is already on its way: one request at a time.
        if (awaitingLocationAnswer) return
        pendingGpsActivity = type
        when (LocationPermissions.outcome(requireContext())) {
            LocationPermissions.Outcome.FineGranted -> onTrackWithGpsConfirmed()
            // Only "approximate" location: GPS points would all be ~1 km
            // off and get filtered out. Ask once more — Android 12+ shows
            // a "use precise location" prompt.
            LocationPermissions.Outcome.CoarseOnly,
            LocationPermissions.Outcome.Denied -> requestLocation()
        }
    }

    private fun requestLocation() {
        awaitingLocationAnswer = true
        try {
            locationLauncher.launch(LocationPermissions.LOCATION_PERMISSIONS)
        } catch (_: IllegalStateException) {
            // The sheet is closing and can't ask any more: let a later
            // tap try again instead of staying blocked.
            awaitingLocationAnswer = false
            pendingGpsActivity = null
        }
    }

    private fun onTrackWithGpsConfirmed() {
        if (_binding == null) return
        // The pending activity is lost if Android recreated the sheet
        // behind the permission dialog; the selection still says it.
        val type = pendingGpsActivity
            ?: viewModel.state.value?.activity?.takeIf(LogSheetOptions::supportsGps)
            ?: return
        pendingGpsActivity = null
        navigateTo(type, R.id.action_choose_to_live_tracking)
    }

    private fun onLocationDenied() {
        pendingGpsActivity = null
        val root = _binding?.root ?: return
        // Keep the sheet open so the user can switch to "Add manually"
        // right here. If Android won't show the permission dialog any
        // more ("Don't allow" twice), the only way back is the app's
        // settings page — offer it.
        val snack = Snackbar.make(
            root,
            R.string.log_sheet_permission_denied_snackbar,
            Snackbar.LENGTH_LONG,
        ).withKind(SnackKind.INFO)
        if (!LocationPermissions.shouldShowRationale(this)) {
            snack.setAction(R.string.auth_location_open_settings) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", requireContext().packageName, null),
                        )
                    )
                }
            }
        }
        snack.show()
    }

    /**
     * Opens the form or tracking screen for [type]. The activity is only
     * remembered for next time once that screen saves a workout.
     */
    private fun navigateTo(type: ActivityType, action: Int) {
        findNavController().navigateSafe(
            action,
            bundleOf(ARG_ACTIVITY_TYPE to type.name),
        )
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"
    }
}
