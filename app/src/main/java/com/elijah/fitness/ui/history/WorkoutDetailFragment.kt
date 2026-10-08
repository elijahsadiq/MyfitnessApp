package com.elijah.fitness.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.databinding.FragmentWorkoutDetailBinding
import com.elijah.fitness.databinding.ViewDetailStatCardBinding
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.tracking.map.DarkMapStyler
import com.elijah.fitness.ui.tracking.map.MapCameraController
import com.elijah.fitness.ui.tracking.map.RoutePolylineRenderer
import com.elijah.fitness.util.Formatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finished workout screen (phase 8c step 7). Rebuilt from
 * `Detail.dc.html`:
 *
 * - A self-drawn header (back + time-of-day title + "Today, Wed 7 Oct ·
 *   06:30" date line + share + overflow). The shared toolbar is hidden
 *   on this destination — see [com.elijah.fitness.ui.main.MainActivity].
 * - A 300dp map card with the Step-3 dark route renderer for distance
 *   workouts that carry a GPS trace. The expand button opens
 *   [FullScreenMapFragment] for the same workout.
 * - A 2x2 stat grid: Distance, Duration, the highlighted pace/speed
 *   card (lime value on dark surface), and Calories.
 * - A Notes card with an Edit button and an "Add a note" placeholder
 *   when the notes field is blank.
 *
 * Strength workouts and manual (routeless) distance workouts hide the
 * map card and adjust the stat grid: Strength uses Volume/Sets in the
 * first two slots and leaves the highlighted card empty.
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

    private var routeRenderer: RoutePolylineRenderer? = null
    private var mapCamera: MapCameraController? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentWorkoutDetailBinding =
        FragmentWorkoutDetailBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Shared toolbar is hidden here — the fragment root absorbs both
        // the status-bar inset (on top) and the gesture-bar inset (on
        // bottom) directly.
        applySystemBarInsets(binding.root)

        DarkMapStyler.apply(binding.mapView, requireContext())
        binding.mapView.controller.setZoom(14.0)
        // Non-interactive on the detail screen — tap the expand button
        // to open a full-screen interactive map instead.
        binding.mapView.setMultiTouchControls(false)
        binding.mapView.isClickable = false

        binding.detailBackButton.setOnClickListener { findNavController().navigateUp() }
        binding.detailShareButton.setOnClickListener { onShareClicked() }
        binding.detailOverflowButton.setOnClickListener { showOverflowMenu() }
        binding.mapExpandButton.setOnClickListener { onExpandClicked() }
        binding.detailNotesEditButton.setOnClickListener { showNotesDialog() }

        collectOnStart(viewModel.state) { render(it) }
        collectOnStart(viewModel.events) { onEvent(it) }
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
        routeRenderer = null
        mapCamera = null
        super.onDestroyView()
    }

    // ---------- state rendering ----------

    private var loadedDetail: WorkoutDetail? = null

    private fun render(state: WorkoutDetailViewModel.State) {
        binding.detailLoadingBar.isVisible = state is WorkoutDetailViewModel.State.Loading
        when (state) {
            WorkoutDetailViewModel.State.Loading -> Unit
            WorkoutDetailViewModel.State.Error -> {
                Snackbar.make(binding.root, R.string.detail_load_error, Snackbar.LENGTH_LONG)
                    .setAction(R.string.goals_retry) { viewModel.retry() }
                    .show()
            }
            is WorkoutDetailViewModel.State.Loaded -> {
                loadedDetail = state.detail
                bindDetail(state.detail)
            }
        }
    }

    private fun onEvent(event: WorkoutDetailViewModel.Event) {
        when (event) {
            WorkoutDetailViewModel.Event.NotesUpdated ->
                Snackbar.make(binding.root, R.string.detail_notes_updated_snackbar, Snackbar.LENGTH_SHORT).show()
            WorkoutDetailViewModel.Event.UpdateNotesFailed ->
                Snackbar.make(binding.root, R.string.detail_delete_failed_snackbar, Snackbar.LENGTH_LONG).show()
            WorkoutDetailViewModel.Event.Deleted -> findNavController().navigateUp()
            WorkoutDetailViewModel.Event.DeleteFailed ->
                Snackbar.make(binding.root, R.string.detail_delete_failed_snackbar, Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------- header ----------

    private fun bindDetail(detail: WorkoutDetail) {
        val workout = detail.workout
        bindHeader(workout)
        bindMap(workout, detail.routePoints)
        bindStats(workout)
        bindNotes(workout.notes)
    }

    private fun bindHeader(workout: Workout) {
        binding.detailTitle.text = getString(timeOfDayTitleRes(workout))
        binding.detailSubtitle.text = formatSubtitle(workout)
    }

    @StringRes
    private fun timeOfDayTitleRes(workout: Workout): Int {
        if (workout is StrengthWorkout) return R.string.detail_time_strength
        val hour = workout.startTime.atZone(ZoneId.systemDefault()).hour
        val isRun = workout.type == ActivityType.RUNNING
        return when (hour) {
            in 5..11 -> if (isRun) R.string.detail_time_morning_run else R.string.detail_time_morning_ride
            in 12..16 -> if (isRun) R.string.detail_time_afternoon_run else R.string.detail_time_afternoon_ride
            in 17..20 -> if (isRun) R.string.detail_time_evening_run else R.string.detail_time_evening_ride
            else -> if (isRun) R.string.detail_time_night_run else R.string.detail_time_night_ride
        }
    }

    private fun formatSubtitle(workout: Workout): String {
        val zone = ZoneId.systemDefault()
        val localDate = workout.startTime.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val dayFmt = subtitleDayFormatter
        val timeFmt = subtitleTimeFormatter
        val dayText = dayFmt.format(localDate)
        val timeText = timeFmt.format(workout.startTime.atZone(zone))
        return when (localDate) {
            today -> getString(R.string.detail_subtitle_today, dayText, timeText)
            today.minusDays(1) -> getString(R.string.detail_subtitle_yesterday, dayText, timeText)
            else -> getString(R.string.detail_subtitle_default, dayText, timeText)
        }
    }

    // ---------- map ----------

    private fun bindMap(workout: Workout, points: List<TrackPoint>) {
        val hasRoute = workout is DistanceWorkout && points.isNotEmpty()
        binding.mapCard.isVisible = hasRoute
        if (!hasRoute) return

        binding.mapActivityPillLabel.setText(
            when (workout.type) {
                ActivityType.CYCLING -> R.string.detail_map_pill_ride
                else -> R.string.detail_map_pill_run
            }
        )
        val distanceKm = (workout as DistanceWorkout).distanceKm()
        binding.mapDistancePill.text = String.format(
            Locale.getDefault(), "%.2f km", distanceKm,
        )

        val renderer = routeRenderer ?: RoutePolylineRenderer(binding.mapView).also {
            routeRenderer = it
        }
        val camera = mapCamera ?: MapCameraController(binding.mapView).also {
            mapCamera = it
        }
        renderer.render(points)
        camera.fitRouteToView(points, padFraction = 0.15)
    }

    // ---------- stats ----------

    private fun bindStats(workout: Workout) {
        val cards = listOf(
            binding.detailStatCard0,
            binding.detailStatCard1,
            binding.detailStatCard2,
            binding.detailStatCard3,
        )
        when (workout) {
            is RunningWorkout -> {
                setStatCard(cards[0], R.string.detail_stat_distance, formatDistanceValue(workout.distanceKm()), getString(R.string.log_distance_unit), highlighted = false)
                setStatCard(cards[1], R.string.detail_stat_duration, formatDurationValue(workout.duration.seconds), null, highlighted = false)
                setStatCard(cards[2], R.string.detail_stat_avg_pace, Formatters.formatPace(workout.paceSecondsPerKm()), getString(R.string.detail_stat_pace_unit), highlighted = true)
                setStatCard(cards[3], R.string.detail_stat_calories, caloriesText(workout), getString(R.string.detail_stat_calories_unit), highlighted = false)
            }
            is CyclingWorkout -> {
                setStatCard(cards[0], R.string.detail_stat_distance, formatDistanceValue(workout.distanceKm()), getString(R.string.log_distance_unit), highlighted = false)
                setStatCard(cards[1], R.string.detail_stat_duration, formatDurationValue(workout.duration.seconds), null, highlighted = false)
                setStatCard(cards[2], R.string.detail_stat_avg_speed, String.format(Locale.getDefault(), "%.1f", workout.averageSpeedKmh()), getString(R.string.detail_stat_speed_unit), highlighted = true)
                setStatCard(cards[3], R.string.detail_stat_calories, caloriesText(workout), getString(R.string.detail_stat_calories_unit), highlighted = false)
            }
            is StrengthWorkout -> {
                setStatCard(cards[0], R.string.detail_stat_volume, formatVolumeValue(workout.totalVolumeKg()), getString(R.string.detail_stat_volume_unit), highlighted = false)
                setStatCard(cards[1], R.string.detail_stat_duration, formatDurationValue(workout.duration.seconds), null, highlighted = false)
                setStatCard(cards[2], R.string.detail_stat_sets, workout.sets.size.toString(), getString(R.string.detail_stat_sets_unit), highlighted = true)
                setStatCard(cards[3], R.string.detail_stat_calories, caloriesText(workout), getString(R.string.detail_stat_calories_unit), highlighted = false)
            }
        }
    }

    private fun setStatCard(
        card: ViewDetailStatCardBinding,
        @StringRes labelRes: Int,
        value: String,
        unit: String?,
        highlighted: Boolean,
    ) {
        card.detailStatCardLabel.setText(labelRes)
        card.detailStatCardValue.text = value
        card.detailStatCardUnit.isVisible = unit != null
        card.detailStatCardUnit.text = unit.orEmpty()
        if (highlighted) {
            card.detailStatCardRoot.setBackgroundResource(R.drawable.bg_detail_stat_card_dark)
            card.detailStatCardLabel.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_on_dark)
            )
            card.detailStatCardValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.lime_accent)
            )
            card.detailStatCardUnit.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_on_dark)
            )
        } else {
            card.detailStatCardRoot.setBackgroundResource(R.drawable.bg_detail_stat_card_light)
            card.detailStatCardLabel.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
            card.detailStatCardValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
            card.detailStatCardUnit.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        }
    }

    private fun formatDistanceValue(km: Double): String =
        String.format(Locale.getDefault(), "%.2f", km)

    private fun formatVolumeValue(kg: Double): String =
        String.format(Locale.getDefault(), "%.0f", kg)

    private fun formatDurationValue(totalSeconds: Long): String {
        val h = totalSeconds / 3_600
        val m = (totalSeconds % 3_600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%dh %02dm", h, m)
        } else {
            String.format(Locale.getDefault(), "%dm %02ds", m, s)
        }
    }

    private fun caloriesText(workout: Workout): String {
        val weight = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value
        return workout.caloriesBurned(weight).toInt().toString()
    }

    // ---------- notes ----------

    private fun bindNotes(notes: String?) {
        val cleaned = notes?.trim().orEmpty()
        if (cleaned.isEmpty()) {
            binding.detailNotesText.text = getString(R.string.detail_notes_add)
            binding.detailNotesText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        } else {
            binding.detailNotesText.text = cleaned
            binding.detailNotesText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
        }
    }

    private fun showNotesDialog() {
        val current = loadedDetail?.workout?.notes.orEmpty()
        val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
        val textLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.detail_notes_placeholder)
            isHintEnabled = true
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPaddingRelative(padding, 0, padding, 0)
        }
        val editText = TextInputEditText(textLayout.context).apply {
            setText(current)
            setSelection(current.length)
            minLines = 3
            maxLines = 8
            isSingleLine = false
        }
        textLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.detail_notes_dialog_title)
            .setView(textLayout)
            .setPositiveButton(R.string.detail_notes_dialog_save) { _, _ ->
                viewModel.updateNotes(editText.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // ---------- share / overflow ----------

    private fun onShareClicked() {
        val detail = loadedDetail ?: return
        val body = shareBodyFor(detail.workout)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.detail_share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.detail_share_subject)))
    }

    private fun shareBodyFor(workout: Workout): String {
        val title = getString(timeOfDayTitleRes(workout))
        return when (workout) {
            is RunningWorkout -> getString(
                R.string.detail_share_body_distance,
                title,
                workout.distanceKm(),
                Formatters.formatDuration(workout.duration),
                Formatters.formatPace(workout.paceSecondsPerKm()),
                getString(R.string.detail_stat_pace_unit),
            )
            is CyclingWorkout -> getString(
                R.string.detail_share_body_distance,
                title,
                workout.distanceKm(),
                Formatters.formatDuration(workout.duration),
                String.format(Locale.getDefault(), "%.1f", workout.averageSpeedKmh()),
                getString(R.string.detail_stat_speed_unit),
            )
            is StrengthWorkout -> getString(
                R.string.detail_share_body_strength,
                title,
                workout.sets.size,
                Formatters.formatDuration(workout.duration),
            )
            else -> title
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), binding.detailOverflowButton)
        popup.menu.add(0, MENU_EDIT_NOTES, 0, R.string.detail_overflow_edit_notes)
        popup.menu.add(0, MENU_DELETE, 1, R.string.detail_overflow_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT_NOTES -> { showNotesDialog(); true }
                MENU_DELETE -> { confirmDelete(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.detail_delete_confirm_title)
            .setMessage(R.string.detail_delete_confirm_body)
            .setPositiveButton(R.string.detail_delete_confirm_positive) { _, _ ->
                viewModel.deleteWorkout()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // ---------- expand ----------

    private fun onExpandClicked() {
        findNavController().navigate(
            R.id.action_detail_to_full_map,
            bundleOf(FullScreenMapFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    companion object {
        const val ARG_CLIENT_UUID = "clientUuid"

        private const val MENU_EDIT_NOTES = 1
        private const val MENU_DELETE = 2

        private val subtitleDayFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        private val subtitleTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }
}
