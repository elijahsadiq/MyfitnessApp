package com.elijah.fitness.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.FragmentDashboardBinding
import com.elijah.fitness.databinding.ItemHomeGoalRowBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.goals.NewGoalBottomSheet
import com.elijah.fitness.util.Formatters
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

/**
 * Home screen — the Step 9 restyle of what used to be a plain "Dashboard".
 *
 * Renders (top to bottom):
 *  - Greeting caption + "Your week" headline + streak pill + avatar
 *    (the avatar navigates to Profile)
 *  - Week strip (7 day cells with lime dots on active days + dark
 *    pill on today)
 *  - Dark "This week" hero card with 2x2 metrics + progress ring +
 *    "Start workout" CTA
 *  - Insight card (hardcoded copy for phase 2)
 *  - Goals section with two progress bars (hardcoded targets — real
 *    goals arrive in phase 5)
 */
class DashboardFragment : BaseFragment<FragmentDashboardBinding>() {

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentDashboardBinding =
        FragmentDashboardBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.avatar.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_profile)
        }
        binding.startWorkoutButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_choose_activity)
        }
        binding.goalsSeeAll.setOnClickListener {
            findNavController().navigate(R.id.goalsFragment)
        }
        binding.homeGoalEmpty.setOnClickListener {
            NewGoalBottomSheet.forCreate().show(parentFragmentManager, "new_goal_from_home")
        }

        collectOnStart(viewModel.summary) { renderSummary(it) }
        collectOnStart(viewModel.homeUi) { renderHomeChrome(it) }
        collectOnStart(viewModel.homeGoals) { renderHomeGoals(it) }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.loadingBar.isVisible = loading
        }
        collectOnStart(viewModel.errorEvents) { event ->
            val messageRes = when (event) {
                is DashboardViewModel.ErrorEvent.Network -> event.messageRes
                is DashboardViewModel.ErrorEvent.Generic -> event.messageRes
            }
            Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
                .setAction(R.string.workouts_retry) { viewModel.refresh() }
                .show()
        }
    }

    private fun renderSummary(summary: WeeklySummary) {
        val ctx = requireContext()

        binding.metricSessions.text = summary.workoutCount.toString()
        binding.metricMinutes.text = summary.activeMinutes.toString()
        binding.metricDistance.text = String.format(
            Locale.getDefault(),
            "%.1f",
            summary.totalDistanceMeters / 1000.0,
        )
        renderBmi(summary)

        // Hero subtitle uses the hardcoded weekly target of 4 workouts.
        binding.heroSubtitle.text = ctx.getString(
            R.string.home_hero_subtitle,
            summary.workoutCount,
            HERO_WEEKLY_TARGET,
        )
        val ringProgress = (summary.workoutCount.toFloat() / HERO_WEEKLY_TARGET)
            .coerceIn(0f, 1f)
        binding.progressRing.progress = ringProgress
        binding.progressRingLabel.text = ctx.getString(
            R.string.home_progress_ring_label,
            (ringProgress * 100).toInt(),
        )

    }

    /**
     * Bind up to two goal rows into the Home Goals card, or show the
     * empty prompt. Progress recomputes automatically because
     * `viewModel.homeGoals` is derived from `GoalProgressCalculator`.
     */
    private fun renderHomeGoals(goals: List<GoalWithProgress>) {
        binding.homeGoalEmpty.isVisible = goals.isEmpty()
        binding.homeGoalSlot1.root.isVisible = goals.isNotEmpty()
        binding.homeGoalSlot2.root.isVisible = goals.size >= 2
        binding.homeGoalDivider.isVisible = goals.size >= 2

        goals.getOrNull(0)?.let { bindHomeGoalRow(binding.homeGoalSlot1, it) }
        goals.getOrNull(1)?.let { bindHomeGoalRow(binding.homeGoalSlot2, it) }
    }

    private fun bindHomeGoalRow(row: ItemHomeGoalRowBinding, gwp: GoalWithProgress) {
        val ctx = requireContext()
        val goal = gwp.goal
        row.homeGoalTitle.text = goal.title
        row.homeGoalValue.text = ctx.getString(
            R.string.goal_value_over_target_decimal,
            formatGoalValue(gwp.currentValue, goal.goalType()),
            formatGoalValue(goal.targetValue, goal.goalType()),
            ctx.getString(unitLabelRes(goal.goalType())),
        )
        row.homeGoalBar.progress = (gwp.progressFraction * 100).toInt().coerceIn(0, 100)
        // The whole row is tappable — jumps to the Goals tab so the user
        // sees the full featured card + can edit.
        row.homeGoalRow.setOnClickListener {
            findNavController().navigate(R.id.goalsFragment)
        }
    }

    private fun formatGoalValue(value: Double, type: GoalType): String = when (type) {
        GoalType.COUNT, GoalType.DURATION -> value.toLong().toString()
        GoalType.DISTANCE, GoalType.VOLUME ->
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun unitLabelRes(type: GoalType): Int = when (type) {
        GoalType.DISTANCE -> R.string.goal_unit_km
        GoalType.COUNT -> R.string.goal_unit_workouts
        GoalType.DURATION -> R.string.goal_unit_min
        GoalType.VOLUME -> R.string.goal_unit_kg
    }

    private fun renderBmi(summary: WeeklySummary) {
        val ctx = requireContext()
        val bmi = summary.bmi
        if (bmi == null) {
            binding.metricBmi.text = ctx.getString(R.string.home_metric_bmi_no_data)
            binding.metricBmiCategory.text = ctx.getString(R.string.home_metric_bmi_missing)
            return
        }
        binding.metricBmi.text = String.format(Locale.getDefault(), "%.1f", bmi)
        val categoryRes = when (summary.bmiCategory) {
            com.elijah.fitness.util.BmiCategory.UNDERWEIGHT ->
                R.string.home_metric_bmi_category_underweight
            com.elijah.fitness.util.BmiCategory.NORMAL ->
                R.string.home_metric_bmi_category_normal
            com.elijah.fitness.util.BmiCategory.OVERWEIGHT ->
                R.string.home_metric_bmi_category_overweight
            com.elijah.fitness.util.BmiCategory.OBESE ->
                R.string.home_metric_bmi_category_obese
            null -> R.string.home_metric_bmi_missing
        }
        binding.metricBmiCategory.text = ctx.getString(categoryRes)
    }

    private fun renderHomeChrome(ui: DashboardViewModel.HomeUi) {
        if (ui == DashboardViewModel.HomeUi.EMPTY) return
        binding.weekStrip.setData(ui.monday, ui.today, ui.daysWithWorkouts)
        binding.streakPill.text = getString(
            R.string.home_streak_pill,
            ui.daysWithWorkouts.size,
        )
    }

    private companion object {
        // Hero ring still tracks a hardcoded "4 workouts this week" target
        // in phase 7 — future goals could drive this too, but the ring is
        // the "week momentum" signal and stays visually consistent when
        // the user has no goals.
        const val HERO_WEEKLY_TARGET = 4
    }
}
