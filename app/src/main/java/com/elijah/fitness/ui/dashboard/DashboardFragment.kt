package com.elijah.fitness.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentDashboardBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.util.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Weekly dashboard: four Material cards (workouts, active minutes, total
 * distance, estimated calories) plus a per-activity breakdown card. Numbers
 * come from the shared [DashboardViewModel] which pipes the repository's
 * workout flow through [WeeklySummaryCalculator], so a freshly-logged
 * workout is reflected instantly.
 */
class DashboardFragment : BaseFragment<FragmentDashboardBinding>() {

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDashboardBinding =
        FragmentDashboardBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.weekRange.text = formatWeekRange()

        collectOnStart(viewModel.summary) { render(it) }
    }

    private fun formatWeekRange(): String {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
        return getString(R.string.dashboard_week_range, fmt.format(monday))
    }

    private fun render(summary: WeeklySummary) {
        val ctx = requireContext()

        binding.workoutCountValue.text = summary.workoutCount.toString()
        binding.activeMinutesValue.text = ctx.getString(
            R.string.dashboard_minutes_value, summary.activeMinutes
        )
        binding.totalDistanceValue.text = Formatters.formatDistanceKm(summary.totalDistanceMeters)
        binding.estimatedCaloriesValue.text = ctx.getString(
            R.string.dashboard_calories_value, summary.estimatedCalories
        )

        val running = summary.perActivity[ActivityType.RUNNING]
            ?: WeeklySummary.ActivityBreakdown.EMPTY
        val cycling = summary.perActivity[ActivityType.CYCLING]
            ?: WeeklySummary.ActivityBreakdown.EMPTY
        val strength = summary.perActivity[ActivityType.WEIGHTLIFTING]
            ?: WeeklySummary.ActivityBreakdown.EMPTY

        binding.runningCount.text = formatCount(running.count)
        binding.runningStat.text = Formatters.formatDistanceKm(running.distanceMeters)

        binding.cyclingCount.text = formatCount(cycling.count)
        binding.cyclingStat.text = Formatters.formatDistanceKm(cycling.distanceMeters)

        binding.weightliftingCount.text = formatCount(strength.count)
        binding.weightliftingStat.text = getString(
            R.string.dashboard_volume_value, strength.totalVolumeKg
        )
    }

    private fun formatCount(count: Int): String =
        resources.getQuantityString(R.plurals.dashboard_workout_count, count, count)
}
