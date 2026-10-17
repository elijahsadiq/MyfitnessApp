package com.elijah.fitness.ui.log

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.BottomSheetChooseActivityBinding
import com.elijah.fitness.databinding.ViewLogSheetActivityTileBinding
import com.elijah.fitness.tracking.LocationPermissions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.elijah.fitness.ui.base.navigateSafe

/**
 * "Log a workout" bottom sheet (phase 8c step 4). One step, three
 * activity tiles + an option block that re-renders with the picked
 * activity. The three sheet entry points — Home "Start workout", the
 * History FAB, and anywhere else that lands here — share this sheet.
 *
 * ### One session at a time (user's Step 0 addition)
 *
 * Before the sheet renders, [onViewCreated] checks the shared
 * `TrackingSession`. If a session is already running the sheet
 * dismisses itself and navigates straight to the live tracking
 * screen, so the user can't double-start.
 *
 * ### Preselection
 *
 * The last-logged activity is remembered in a DataStore key via
 * [com.elijah.fitness.data.local.LastLoggedActivityStore]. The sheet
 * reads it before the first render — new users default to Running.
 *
 * ### Permission flow
 *
 * "Track with GPS" calls out to the Phase-8a permission flow via
 * [LocationPermissions]. On refusal the sheet shows a Snackbar
 * nudging the user toward "Enter manually" — the manual option is
 * already there, so no extra routing.
 */
class ChooseActivityBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseActivityBinding? = null
    private val binding get() = _binding!!

    private val runTile by lazy {
        ViewLogSheetActivityTileBinding.bind(binding.logSheetActivityRun.root)
    }
    private val rideTile by lazy {
        ViewLogSheetActivityTileBinding.bind(binding.logSheetActivityRide.root)
    }
    private val liftTile by lazy {
        ViewLogSheetActivityTileBinding.bind(binding.logSheetActivityLift.root)
    }

    private var selected: ActivityType = ActivityType.RUNNING

    private val locationLauncher = LocationPermissions.registerLocationLauncher(this) { outcome ->
        when (outcome) {
            // "Approximate" alone can't track a route, but the user chose
            // it — start anyway; the screen will just show weak GPS.
            LocationPermissions.Outcome.FineGranted,
            LocationPermissions.Outcome.CoarseOnly -> onTrackWithGpsConfirmed()
            LocationPermissions.Outcome.Denied -> onLocationDenied()
        }
    }

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

        bindActivityTile(
            tile = runTile, icon = R.drawable.ic_run_24,
            label = R.string.activity_type_running, type = ActivityType.RUNNING,
        )
        bindActivityTile(
            tile = rideTile, icon = R.drawable.ic_bike_24,
            label = R.string.activity_type_cycling, type = ActivityType.CYCLING,
        )
        bindActivityTile(
            tile = liftTile, icon = R.drawable.ic_dumbbell_24,
            label = R.string.activity_type_weightlifting, type = ActivityType.WEIGHTLIFTING,
        )

        binding.logSheetTrackGps.setOnClickListener { onTrackWithGpsClicked() }
        binding.logSheetEnterManual.setOnClickListener { onManualClicked() }
        binding.logSheetLiftOption.setOnClickListener { onLiftOptionClicked() }

        // Preselect from DataStore (fall back to Running).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val remembered = container.lastLoggedActivityStore.lastActivity.first()
                select(remembered ?: ActivityType.RUNNING)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- rendering ----------

    private fun bindActivityTile(
        tile: ViewLogSheetActivityTileBinding,
        icon: Int,
        label: Int,
        type: ActivityType,
    ) {
        tile.logSheetActivityTileIcon.setImageResource(icon)
        tile.logSheetActivityTileLabel.setText(label)
        tile.logSheetActivityTileRoot.setOnClickListener { select(type) }
    }

    private fun select(type: ActivityType) {
        selected = type
        runTile.logSheetActivityTileRoot.isSelected = type == ActivityType.RUNNING
        rideTile.logSheetActivityTileRoot.isSelected = type == ActivityType.CYCLING
        liftTile.logSheetActivityTileRoot.isSelected = type == ActivityType.WEIGHTLIFTING

        val isDistance = type == ActivityType.RUNNING || type == ActivityType.CYCLING
        binding.logSheetDistanceOptions.isVisible = isDistance
        binding.logSheetLiftOption.isVisible = !isDistance

        if (isDistance) {
            binding.logSheetManualHint.setText(
                when (type) {
                    ActivityType.RUNNING -> R.string.log_sheet_manual_hint_run
                    ActivityType.CYCLING -> R.string.log_sheet_manual_hint_ride
                    else -> R.string.log_sheet_manual_hint_run
                }
            )
        }
    }

    // ---------- intent handlers ----------

    private fun onTrackWithGpsClicked() {
        when (LocationPermissions.outcome(requireContext())) {
            LocationPermissions.Outcome.FineGranted -> onTrackWithGpsConfirmed()
            // Only "approximate" location: GPS points would all be ~1 km
            // off and get filtered out. Ask once more — Android 12+ shows
            // a "use precise location" prompt.
            LocationPermissions.Outcome.CoarseOnly,
            LocationPermissions.Outcome.Denied ->
                locationLauncher.launch(LocationPermissions.LOCATION_PERMISSIONS)
        }
    }

    private fun onTrackWithGpsConfirmed() {
        rememberAndNavigate(
            type = selected,
            action = R.id.action_choose_to_live_tracking,
        )
    }

    private fun onManualClicked() {
        rememberAndNavigate(
            type = selected,
            action = R.id.action_choose_to_log_distance,
        )
    }

    private fun onLiftOptionClicked() {
        rememberAndNavigate(
            type = ActivityType.WEIGHTLIFTING,
            action = R.id.action_choose_to_log_strength,
        )
    }

    private fun onLocationDenied() {
        // Keep the sheet open so the user can switch to Enter manually
        // without reopening Home → sheet. If Android won't show the
        // permission dialog any more ("Don't allow" twice), the only way
        // back is the app's settings page — offer it.
        val snack = Snackbar.make(
            binding.root,
            R.string.log_sheet_permission_denied_snackbar,
            Snackbar.LENGTH_LONG,
        )
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

    private fun rememberAndNavigate(type: ActivityType, action: Int) {
        val container = (requireContext().applicationContext as FitnessApp).container
        viewLifecycleOwner.lifecycleScope.launch {
            container.lastLoggedActivityStore.setLastActivity(type)
        }
        findNavController().navigateSafe(
            action,
            bundleOf(ARG_ACTIVITY_TYPE to type.name),
        )
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"
    }
}
