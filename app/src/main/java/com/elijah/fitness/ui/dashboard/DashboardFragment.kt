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
import com.elijah.fitness.databinding.FragmentDashboardBinding
import com.elijah.fitness.ui.base.BaseFragment
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
        applySystemBarInsets(applyBottom = false)

        binding.avatar.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_profile)
        }
        binding.startWorkoutButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_choose_activity)
        }

        collectOnStart(viewModel.summary) { renderSummary(it) }
        collectOnStart(viewModel.homeUi) { renderHomeChrome(it) }
        collectOnStart(viewModel.topInsight) { insight ->
            binding.insightBody.text = insight?.text
                ?: getString(R.string.home_insight_body)
        }
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
        binding.metricCalories.text = summary.estimatedCalories.toInt().toString()

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

    private fun renderHomeChrome(ui: DashboardViewModel.HomeUi) {
        if (ui == DashboardViewModel.HomeUi.EMPTY) return
        binding.weekStrip.setData(ui.monday, ui.today, ui.daysWithWorkouts)
        // Streak comes from the summary flow, not the week-strip UI, so
        // we read it off the collected summary rather than threading it
        // through HomeUi.
        val streak = viewModel.summary.value.streakDays
        if (streak >= 2) {
            binding.streakPill.text = getString(R.string.home_streak_pill, streak)
            binding.streakPill.isVisible = true
        } else {
            binding.streakPill.isVisible = false
        }
    }

    private companion object {
        // Hero ring still tracks a hardcoded "4 workouts this week" target
        // in phase 7 — future goals could drive this too, but the ring is
        // the "week momentum" signal and stays visually consistent when
        // the user has no goals.
        const val HERO_WEEKLY_TARGET = 4
    }
}
