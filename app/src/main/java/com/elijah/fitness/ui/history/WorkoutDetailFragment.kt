package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.databinding.FragmentWorkoutDetailBinding
import com.elijah.fitness.databinding.ItemRouteSplitBinding
import com.elijah.fitness.tracking.RouteSplit
import com.elijah.fitness.tracking.RouteSplits
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.tracking.map.DarkMapStyler
import com.elijah.fitness.ui.tracking.map.MapCameraController
import com.elijah.fitness.ui.tracking.map.RoutePolylineRenderer
import com.elijah.fitness.util.Formatters
import com.google.android.material.snackbar.Snackbar
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Detail screen for a saved workout. Shows the activity-type stats, a
 * route map + per-km splits when GPS points exist, and notes when set.
 *
 * Reached by tapping a history row; the row passes the workout's
 * `clientUuid` as an argument. The screen fetches detail (which includes
 * route points) through `WorkoutRepository.getWorkoutDetail`.
 */
class WorkoutDetailFragment : BaseFragment<FragmentWorkoutDetailBinding>() {

    private val clientUuid: String by lazy {
        arguments?.getString(ARG_CLIENT_UUID) ?: error("WorkoutDetailFragment missing clientUuid")
    }

    private val viewModel: WorkoutDetailViewModel by viewModels {
        WorkoutDetailViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container,
            clientUuid,
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentWorkoutDetailBinding =
        FragmentWorkoutDetailBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Phase 8c step 2 — edge-to-edge. Shared toolbar handles the
        // top inset; this clears the gesture bar at the bottom.
        applyBottomNavInset(binding.root)
        configureOsmdroid()
        setupMap()
        collectOnStart(viewModel.state) { render(it) }
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
        binding.mapView.onDetach()
        super.onDestroyView()
    }

    private fun render(state: WorkoutDetailViewModel.State) {
        binding.detailLoadingBar.isVisible = state is WorkoutDetailViewModel.State.Loading
        when (state) {
            WorkoutDetailViewModel.State.Loading -> Unit
            WorkoutDetailViewModel.State.Error -> {
                Snackbar.make(binding.root, R.string.detail_load_error, Snackbar.LENGTH_LONG)
                    .setAction(R.string.goals_retry) { viewModel.retry() }
                    .show()
            }
            is WorkoutDetailViewModel.State.Loaded -> bindDetail(state.detail)
        }
    }

    private fun bindDetail(detail: WorkoutDetail) {
        val workout = detail.workout
        binding.detailTitle.setText(titleResFor(workout))
        binding.detailSubtitle.text = getString(
            R.string.detail_subtitle,
            Formatters.formatDate(workout.startTime),
            timeFormatter.format(workout.startTime.atZone(ZoneId.systemDefault())),
        )

        bindStats(workout)
        bindMap(detail.routePoints)
        bindSplits(detail.routePoints)
        bindNotes(workout.notes)
    }

    private fun bindStats(workout: Workout) {
        val kg = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value
        val calories = workout.caloriesBurned(kg).toInt()
        binding.detailDuration.text = Formatters.formatDuration(workout.duration)
        binding.detailCalories.text = getString(R.string.detail_calories_value, calories)

        when (workout) {
            is RunningWorkout -> {
                binding.detailDistance.text = Formatters.formatDistanceKm(workout.distanceMeters)
                binding.detailPaceLabel.setText(R.string.detail_pace_label)
                binding.detailPace.text = Formatters.formatPace(workout.paceSecondsPerKm())
            }
            is CyclingWorkout -> {
                binding.detailDistance.text = Formatters.formatDistanceKm(workout.distanceMeters)
                binding.detailPaceLabel.setText(R.string.detail_speed_label)
                binding.detailPace.text = Formatters.formatSpeed(workout.averageSpeedKmh())
            }
            is StrengthWorkout -> {
                // No distance for strength — show total volume in that slot.
                binding.detailDistance.text = getString(
                    R.string.history_row_total_volume, workout.totalVolumeKg(),
                )
                binding.detailPaceLabel.setText(R.string.history_row_total_volume_label)
                binding.detailPace.text = workout.sets.size.toString()
            }
        }
    }

    private var routeRenderer: RoutePolylineRenderer? = null
    private var mapCamera: MapCameraController? = null

    private fun bindMap(points: List<TrackPoint>) {
        if (points.isEmpty()) {
            binding.mapCard.isVisible = false
            return
        }
        binding.mapCard.isVisible = true
        val renderer = routeRenderer ?: RoutePolylineRenderer(binding.mapView).also {
            routeRenderer = it
        }
        val camera = mapCamera ?: MapCameraController(binding.mapView).also {
            mapCamera = it
        }
        renderer.render(points)
        camera.fitRouteToView(points, padFraction = 0.15)
    }

    private fun bindSplits(points: List<TrackPoint>) {
        val splits = RouteSplits.compute(points)
        if (splits.isEmpty()) {
            binding.splitsHeader.isVisible = false
            binding.splitsContainer.isVisible = false
            return
        }
        binding.splitsHeader.isVisible = true
        binding.splitsContainer.isVisible = true
        binding.splitsContainer.removeAllViews()

        val maxPace = splits.maxOf { it.paceMsPerKm.coerceAtLeast(1) }
        val inflater = LayoutInflater.from(requireContext())
        splits.forEach { split -> addSplitRow(inflater, split, maxPace) }
    }

    private fun addSplitRow(
        inflater: LayoutInflater,
        split: RouteSplit,
        maxPace: Long,
    ) {
        val row = ItemRouteSplitBinding.inflate(inflater, binding.splitsContainer, false)
        row.splitKm.text = getString(
            if (split.isPartial) R.string.detail_split_km_partial_label
            else R.string.detail_split_km_label,
            split.km,
        )
        row.splitPace.text = Formatters.formatPace(split.paceMsPerKm / 1000)
        // Bar width is proportional to pace vs the slowest split.
        val fraction = (split.paceMsPerKm.toDouble() / maxPace).coerceIn(0.1, 1.0)
        row.splitBar.layoutParams.width = (resources.displayMetrics.density * 200 * fraction).toInt()
        binding.splitsContainer.addView(row.root)
    }

    private fun bindNotes(notes: String?) {
        val hasNotes = !notes.isNullOrBlank()
        binding.notesHeader.isVisible = hasNotes
        binding.notesText.isVisible = hasNotes
        if (hasNotes) binding.notesText.text = notes
    }

    private fun titleResFor(workout: Workout): Int = when (workout) {
        is RunningWorkout -> R.string.detail_title_running
        is CyclingWorkout -> R.string.detail_title_cycling
        is StrengthWorkout -> R.string.detail_title_strength
        else -> R.string.detail_title_running
    }

    private fun configureOsmdroid() {
        // Phase 8c step 3 — one call to the shared styler replaces the
        // per-fragment osmdroid wiring; it also installs the OSM
        // attribution overlay and darkens the tiles.
        DarkMapStyler.apply(binding.mapView, requireContext())
    }

    private fun setupMap() {
        binding.mapView.controller.setZoom(14.0)
    }

    companion object {
        const val ARG_CLIENT_UUID = "clientUuid"

        private val timeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm")
    }
}
