package com.elijah.fitness.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.analytics.ActivityFilter
import com.elijah.fitness.analytics.AnalyticsPeriod
import com.elijah.fitness.analytics.PersonalRecords
import com.elijah.fitness.analytics.ProgressStats
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentProgressBinding
import com.elijah.fitness.databinding.ItemProgressActivityCardBinding
import com.elijah.fitness.databinding.ItemProgressStatRowBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.util.Formatters
import com.google.android.material.snackbar.Snackbar

/**
 * Progress tab — period/activity filter, bar chart of active minutes
 * per bucket, trend chart (pace / speed / volume) for the selected
 * activity, per-activity statistics cards, and lifetime personal
 * records. All computations live in [com.elijah.fitness.analytics].
 */
class ProgressFragment : BaseFragment<FragmentProgressBinding>() {

    private val viewModel: ProgressViewModel by viewModels {
        ProgressViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProgressBinding =
        FragmentProgressBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(applyBottom = false)
        applyBottomNavInset(binding.progressScroll)

        binding.periodWeek.setOnClickListener { viewModel.setPeriod(AnalyticsPeriod.WEEK) }
        binding.periodMonth.setOnClickListener { viewModel.setPeriod(AnalyticsPeriod.MONTH) }
        binding.periodYear.setOnClickListener { viewModel.setPeriod(AnalyticsPeriod.YEAR) }

        binding.filterChips.setOnCheckedStateChangeListener { _, checked ->
            val f = when (checked.firstOrNull()) {
                binding.filterRunning.id -> ActivityFilter.RUNNING
                binding.filterCycling.id -> ActivityFilter.CYCLING
                binding.filterWeightlifting.id -> ActivityFilter.WEIGHTLIFTING
                else -> ActivityFilter.ALL
            }
            viewModel.setFilter(f)
        }

        collectOnStart(viewModel.period) { syncPeriodButtons(it) }
        collectOnStart(viewModel.state) { render(it) }
        collectOnStart(viewModel.isLoading) { binding.progressLoadingBar.isVisible = it }
        collectOnStart(viewModel.loadError) { err ->
            if (err) {
                Snackbar.make(binding.root, R.string.workouts_error_load_failed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.workouts_retry) { viewModel.refresh() }
                    .show()
            }
        }
    }

    private fun syncPeriodButtons(period: AnalyticsPeriod) {
        binding.periodGroup.check(
            when (period) {
                AnalyticsPeriod.WEEK -> R.id.periodWeek
                AnalyticsPeriod.MONTH -> R.id.periodMonth
                AnalyticsPeriod.YEAR -> R.id.periodYear
            }
        )
    }

    private fun render(state: ProgressViewModel.State) {
        binding.progressEmpty.isVisible = state.isEmpty
        binding.progressContent.isVisible = !state.isEmpty
        if (state.isEmpty) return

        binding.periodChart.setData(state.periodBars)

        bindActivityCards(state.perActivity)
        bindPersonalRecords(state.personalRecords)
    }

    private fun bindActivityCards(perActivity: Map<ActivityType, ProgressStats>) {
        binding.perActivityContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        ActivityType.entries.forEach { type ->
            val stats = perActivity[type] ?: return@forEach
            if (stats.workoutCount == 0) return@forEach
            val card = ItemProgressActivityCardBinding.inflate(
                inflater, binding.perActivityContainer, false
            )
            card.activityTitle.setText(activityNameRes(type))
            fillStatRows(card.statRowsContainer, type, stats, inflater)
            binding.perActivityContainer.addView(card.root)
        }
    }

    private fun activityNameRes(type: ActivityType): Int = when (type) {
        ActivityType.RUNNING -> R.string.activity_type_running
        ActivityType.CYCLING -> R.string.activity_type_cycling
        ActivityType.WEIGHTLIFTING -> R.string.activity_type_weightlifting
    }

    private fun fillStatRows(
        container: ViewGroup,
        type: ActivityType,
        stats: ProgressStats,
        inflater: LayoutInflater,
    ) {
        when (type) {
            ActivityType.RUNNING -> {
                addRow(container, inflater, R.string.progress_stat_total_distance,
                    stats.totalDistanceMeters?.let { formatDistance(it) })
                addRow(container, inflater, R.string.progress_stat_total_time,
                    stats.totalDurationMinutes?.let { getString(R.string.progress_stat_value_duration_minutes, it.toInt()) })
                addRow(container, inflater, R.string.progress_stat_average_pace,
                    stats.averagePaceSecondsPerKm?.let { Formatters.formatPace(it) })
                addRow(container, inflater, R.string.progress_stat_best_pace,
                    stats.bestPaceSecondsPerKm?.let { Formatters.formatPace(it) })
                addRow(container, inflater, R.string.progress_stat_longest_run,
                    stats.longestDistanceMeters?.let { formatDistance(it) })
            }
            ActivityType.CYCLING -> {
                addRow(container, inflater, R.string.progress_stat_total_distance,
                    stats.totalDistanceMeters?.let { formatDistance(it) })
                addRow(container, inflater, R.string.progress_stat_total_time,
                    stats.totalDurationMinutes?.let { getString(R.string.progress_stat_value_duration_minutes, it.toInt()) })
                addRow(container, inflater, R.string.progress_stat_average_speed,
                    stats.averageSpeedKmh?.let { Formatters.formatSpeed(it) })
                addRow(container, inflater, R.string.progress_stat_max_speed,
                    stats.maxSpeedKmh?.let { Formatters.formatSpeed(it) })
                addRow(container, inflater, R.string.progress_stat_longest_ride,
                    stats.longestDistanceMeters?.let { formatDistance(it) })
            }
            ActivityType.WEIGHTLIFTING -> {
                addRow(container, inflater, R.string.progress_stat_total_sets,
                    stats.totalSets?.toString())
                addRow(container, inflater, R.string.progress_stat_total_volume,
                    stats.totalVolumeKg?.let { getString(R.string.progress_stat_value_volume, it) })
                addRow(container, inflater, R.string.progress_stat_total_time,
                    stats.totalDurationMinutes?.let { getString(R.string.progress_stat_value_duration_minutes, it.toInt()) })
                addRow(container, inflater, R.string.progress_stat_most_frequent,
                    stats.mostFrequentExercise)
            }
        }
    }

    private fun addRow(
        container: ViewGroup,
        inflater: LayoutInflater,
        labelRes: Int,
        value: String?,
    ) {
        if (value == null) return
        val row = ItemProgressStatRowBinding.inflate(inflater, container, false)
        row.statLabel.setText(labelRes)
        row.statValue.text = value
        container.addView(row.root)
    }

    private fun bindPersonalRecords(records: PersonalRecords) {
        val container = binding.personalRecordsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        val anyRecord = records.fastest5kSecondsPerKm != null ||
            records.longestRunMeters != null ||
            records.longestRideMeters != null ||
            records.bestOneRepMaxes.isNotEmpty()

        if (!anyRecord) {
            addRow(container, inflater, R.string.progress_pr_none, " ")
            return
        }

        records.fastest5kSecondsPerKm?.let {
            addRow(container, inflater, R.string.progress_pr_fastest_5k, Formatters.formatPace(it))
        }
        records.longestRunMeters?.let {
            addRow(container, inflater, R.string.progress_pr_longest_run, formatDistance(it))
        }
        records.longestRideMeters?.let {
            addRow(container, inflater, R.string.progress_pr_longest_ride, formatDistance(it))
        }
        records.bestOneRepMaxes.entries
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (exercise, kg) ->
                val row = ItemProgressStatRowBinding.inflate(inflater, container, false)
                row.statLabel.text = getString(R.string.progress_pr_one_rep_max, exercise)
                row.statValue.text = getString(R.string.progress_pr_one_rep_max_value, kg)
                container.addView(row.root)
            }
    }

    private fun formatDistance(meters: Double): String =
        getString(R.string.progress_stat_value_distance, meters / 1000.0)
}
