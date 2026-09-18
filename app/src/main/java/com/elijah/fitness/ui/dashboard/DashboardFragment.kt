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
            // Reuse the History FAB flow: navigate to workout history so the
            // choose-activity bottom sheet is one tap away. (Phase 3 will
            // wire this to a live-tracking GPS session.)
            findNavController().navigate(R.id.workoutHistoryFragment)
        }

        collectOnStart(viewModel.summary) { renderSummary(it) }
        collectOnStart(viewModel.homeUi) { renderHomeChrome(it) }
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
        binding.metricEnergy.text = String.format(
            Locale.getDefault(),
            "%,.0f",
            summary.estimatedCalories,
        )

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

        // Goals bars (hardcoded targets for phase 2 — TODO(phase 5): drive
        // from a real Goal repository).
        val distanceKm = (summary.totalDistanceMeters / 1000.0).toInt()
        binding.goalDistanceValue.text = ctx.getString(
            R.string.home_goal_distance_value,
            distanceKm,
            GOAL_DISTANCE_KM,
        )
        binding.goalDistanceBar.progress = ((distanceKm.toFloat() / GOAL_DISTANCE_KM) * 100)
            .toInt()
            .coerceIn(0, 100)

        val minutes = summary.activeMinutes.toInt()
        binding.goalMinutesValue.text = ctx.getString(
            R.string.home_goal_minutes_value,
            minutes,
            GOAL_MINUTES,
        )
        binding.goalMinutesBar.progress = ((minutes.toFloat() / GOAL_MINUTES) * 100)
            .toInt()
            .coerceIn(0, 100)
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
        // Weekly targets — hardcoded in phase 2 per the brief.
        // TODO(phase 5): drive from a real Goal repository.
        const val HERO_WEEKLY_TARGET = 4
        const val GOAL_DISTANCE_KM = 50
        const val GOAL_MINUTES = 180
    }
}
