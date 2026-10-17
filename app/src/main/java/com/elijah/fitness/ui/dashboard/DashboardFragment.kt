package com.elijah.fitness.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentDashboardBinding
import com.elijah.fitness.tracking.TrackingState
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.util.Fonts
import com.elijah.fitness.util.MetricText
import java.util.Locale
import com.elijah.fitness.ui.base.navigateSafe

/**
 * Home screen.
 *
 * Renders (top to bottom):
 *  - "Your week" headline + streak pill + avatar (the avatar opens
 *    Profile)
 *  - Week strip (7 day cells with a dot on active days + dark pill on
 *    today)
 *  - Dark "This week" hero card with 2x2 metrics + progress ring +
 *    "Start workout" CTA (reads "Back to your workout" while a GPS
 *    session is running in the background)
 *  - Insight card
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
        // The bottom nav sits below this screen, so only the status bar
        // (and side bars in landscape) need clearing.
        applySystemBarInsets(applyBottom = false)

        binding.avatar.setOnClickListener {
            findNavController().navigateSafe(R.id.action_home_to_profile)
        }
        binding.startWorkoutButton.setOnClickListener {
            // The sheet itself jumps straight back into a running session.
            findNavController().navigateSafe(R.id.action_home_to_choose_activity)
        }

        val container = (requireContext().applicationContext as FitnessApp).container
        collectOnStart(container.trackingSession.state) { renderStartButton(it) }
        collectOnStart(viewModel.summary) { renderSummary(it) }
        collectOnStart(viewModel.homeUi) { renderWeekStrip(it) }
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
            snackbar(messageRes)
                .setAction(R.string.workouts_retry) { viewModel.refresh() }
                .show()
        }
    }

    private fun renderSummary(summary: WeeklySummary) {
        val muted = ContextCompat.getColor(requireContext(), R.color.text_on_dark_muted)
        val unitFont = Fonts.medium(requireContext())

        binding.metricSessions.text = MetricText.of(
            summary.workoutCount.toString(),
            resources.getQuantityString(R.plurals.home_metric_sessions_unit, summary.workoutCount),
            muted, UNIT_SCALE, unitFont,
        )
        binding.metricMinutes.text = MetricText.of(
            summary.activeMinutes.toString(),
            getString(R.string.home_metric_minutes_unit),
            muted, UNIT_SCALE, unitFont,
        )
        binding.metricDistance.text = MetricText.of(
            String.format(Locale.getDefault(), "%.1f", summary.totalDistanceMeters / 1000.0),
            getString(R.string.home_metric_distance_unit),
            muted, UNIT_SCALE, unitFont,
        )
        binding.metricCalories.text = MetricText.of(
            summary.estimatedCalories.toInt().toString(),
            getString(R.string.home_metric_calories_unit),
            muted, UNIT_SCALE, unitFont,
        )

        // Weekly target of 4 workouts. Past the target the subtitle
        // celebrates instead of reading "5 of 4".
        binding.heroSubtitle.text =
            if (summary.workoutCount >= HERO_WEEKLY_TARGET) {
                getString(R.string.home_hero_subtitle_done)
            } else {
                getString(R.string.home_hero_subtitle, summary.workoutCount, HERO_WEEKLY_TARGET)
            }
        val ringProgress = (summary.workoutCount.toFloat() / HERO_WEEKLY_TARGET)
            .coerceIn(0f, 1f)
        binding.progressRing.progress = ringProgress
        binding.progressRingLabel.text = getString(
            R.string.home_progress_ring_label,
            (ringProgress * 100).toInt(),
        )

        // Streak comes from the same summary, so it's never out of step
        // with the numbers above.
        val streak = summary.streakDays
        binding.streakPill.isVisible = streak >= 2
        if (streak >= 2) {
            binding.streakPill.text = getString(R.string.home_streak_pill, streak)
            // The title matters more than the pill: on a narrow phone with
            // a large font, drop the pill rather than cut "Your week" short.
            binding.homeTitle.post {
                val b = bindingOrNull ?: return@post
                val ellipsized = (b.homeTitle.layout?.getEllipsisCount(0) ?: 0) > 0
                if (ellipsized) b.streakPill.isVisible = false
            }
        }
    }

    private fun renderWeekStrip(ui: DashboardViewModel.HomeUi) {
        if (ui == DashboardViewModel.HomeUi.EMPTY) return
        binding.weekStrip.setData(ui.monday, ui.today, ui.daysWithWorkouts)
    }

    private fun renderStartButton(state: TrackingState) {
        binding.startWorkoutButton.setText(
            if (state.isActive) R.string.home_resume_workout else R.string.home_start_workout
        )
    }

    private companion object {
        // Hero ring tracks a fixed "4 workouts this week" target — it's the
        // "week momentum" signal and stays meaningful with no goals set.
        const val HERO_WEEKLY_TARGET = 4
        const val UNIT_SCALE = 0.42f
    }
}
