package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.BottomSheetGoalActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Manage goal bottom sheet (phase 8b step 7). Rebuilt from
 * `Manage.dc.html`: a progress snapshot and four actions — Edit, Make
 * main, Add 7 more days, Delete.
 *
 * The sheet dispatches intents back to the parent `GoalsFragment` via
 * `setFragmentResult`; the parent decides how to execute each one so the
 * delete-with-undo coordinator stays in `GoalsViewModel` and we don't
 * duplicate repository calls across view models.
 */
class GoalActionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGoalActionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ManageGoalViewModel by viewModels {
        val goalId = requireArguments().getString(ARG_GOAL_ID)
            ?: error("Missing goal id")
        ManageGoalViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container,
            goalId = goalId,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetGoalActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val goalId = requireArguments().getString(ARG_GOAL_ID)
            ?: error("Missing goal id")

        binding.manageGoalCloseButton.setOnClickListener { dismiss() }

        binding.manageGoalActionEdit.setOnClickListener {
            dispatch(goalId, ACTION_EDIT)
        }
        binding.manageGoalActionMakeMain.setOnClickListener {
            dispatch(goalId, ACTION_MAKE_MAIN)
        }
        binding.manageGoalActionExtend.setOnClickListener {
            dispatch(goalId, ACTION_EXTEND)
        }
        binding.manageGoalActionDelete.setOnClickListener {
            dispatch(goalId, ACTION_DELETE)
        }

        collectOnStart(viewModel.goal) { snapshot ->
            if (snapshot == null) return@collectOnStart
            render(snapshot)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun dispatch(goalId: String, action: String) {
        setFragmentResult(FRAGMENT_RESULT_KEY, Bundle().apply {
            putString(ACTION_KEY, action)
            putString(GOAL_ID_KEY, goalId)
        })
        dismiss()
    }

    // ---------- rendering ----------

    private fun render(snapshot: GoalWithProgress) {
        val ctx = requireContext()
        val goal = snapshot.goal
        val type = goal.goalType()

        // Header: icon · title · subtitle.
        binding.manageGoalIcon.setImageResource(iconFor(goal.activityType, type))
        binding.manageGoalTitle.text = goal.displayTitle()
        binding.manageGoalSubtitle.text = subtitleFor(goal, type)

        // Progress row.
        binding.manageGoalCurrentValue.text = formatValue(snapshot.currentValue, type)
        binding.manageGoalOfTarget.text = ctx.getString(
            R.string.manage_goal_progress_of,
            formatValue(goal.targetValue, type),
            unitLabel(type),
        )
        val pct = (snapshot.progressFraction * 100).toInt()
        binding.manageGoalPercent.text = ctx.getString(R.string.home_progress_ring_label, pct)
        binding.manageGoalProgress.progress = pct

        // Stat tiles.
        binding.manageGoalStatAvg.text = formatPerDay(snapshot.averagePerDay, type)
        binding.manageGoalStatNeeded.text = formatPerDay(snapshot.neededPerDay, type)
        binding.manageGoalStatSessionsLabel.setText(sessionsLabelRes(goal.activityType))
        binding.manageGoalStatSessions.text = snapshot.sessionsLogged.toString()

        // "Make main" only for an active goal that isn't already the main
        // one — finished goals never appear on the main card.
        val showMakeMain = snapshot.isActive && !snapshot.isMain
        binding.manageGoalActionMakeMain.isVisible = showMakeMain
        binding.manageGoalDividerAfterMakeMain.isVisible = showMakeMain

        // "Add 7 more days" only while the goal is running (the server
        // refuses ended goals) and while it stays within a year.
        val showExtend = snapshot.isActive &&
            goal.durationDays + EXTEND_DAYS <= NewGoalViewModel.DAYS_MAX
        binding.manageGoalActionExtend.isVisible = showExtend
        binding.manageGoalDividerAfterExtend.isVisible = showExtend
    }

    // ---------- formatting helpers ----------

    private fun subtitleFor(goal: Goal, type: GoalType): String {
        val ctx = requireContext()
        val startDate = goal.startDate.format(
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        )
        val metricLabel = metricLabel(type)
        return if (goal.activityType == null) {
            ctx.getString(R.string.manage_goal_subtitle_any, metricLabel, startDate)
        } else {
            ctx.getString(
                R.string.manage_goal_subtitle,
                activityLabel(goal.activityType),
                metricLabel,
                startDate,
            )
        }
    }

    private fun activityLabel(type: ActivityType): String = when (type) {
        ActivityType.RUNNING -> getString(R.string.activity_type_running)
        ActivityType.CYCLING -> getString(R.string.activity_type_cycling)
        ActivityType.WEIGHTLIFTING -> getString(R.string.activity_type_weightlifting)
    }

    private fun metricLabel(type: GoalType): String = getString(
        when (type) {
            GoalType.DISTANCE -> R.string.new_goal_metric_distance
            GoalType.DURATION -> R.string.new_goal_metric_time
            GoalType.COUNT -> R.string.new_goal_metric_sessions
            GoalType.VOLUME -> R.string.new_goal_metric_weight
        }
    )

    private fun unitLabel(type: GoalType): String = getString(
        when (type) {
            GoalType.DISTANCE -> R.string.goal_unit_km
            GoalType.COUNT -> R.string.goal_unit_workouts
            GoalType.DURATION -> R.string.goal_unit_min
            GoalType.VOLUME -> R.string.goal_unit_kg
        }
    )

    private fun sessionsLabelRes(activity: ActivityType?): Int = when (activity) {
        ActivityType.RUNNING -> R.string.manage_goal_stat_runs_logged
        ActivityType.CYCLING -> R.string.manage_goal_stat_rides_logged
        ActivityType.WEIGHTLIFTING -> R.string.manage_goal_stat_workouts_logged
        null -> R.string.manage_goal_stat_sessions_logged
    }

    private fun iconFor(activity: ActivityType?, type: GoalType): Int = when (activity) {
        ActivityType.RUNNING -> R.drawable.ic_run_24
        ActivityType.CYCLING -> R.drawable.ic_bike_24
        ActivityType.WEIGHTLIFTING -> R.drawable.ic_dumbbell_24
        null -> when (type) {
            GoalType.DISTANCE -> R.drawable.ic_run_24
            GoalType.VOLUME -> R.drawable.ic_dumbbell_24
            GoalType.DURATION -> R.drawable.ic_history_24
            GoalType.COUNT -> R.drawable.ic_flag_24
        }
    }

    private fun formatValue(value: Double, type: GoalType): String = when (type) {
        GoalType.COUNT, GoalType.DURATION, GoalType.VOLUME ->
            java.text.NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.toLong())
        GoalType.DISTANCE ->
            String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun formatPerDay(value: Double, type: GoalType): String {
        val ctx = requireContext()
        if (value <= 0.0) return getString(R.string.manage_goal_stat_value_blank)
        return when (type) {
            GoalType.DISTANCE ->
                ctx.getString(R.string.manage_goal_stat_value_distance, value)
            GoalType.DURATION ->
                ctx.getString(R.string.manage_goal_stat_value_time,
                    kotlin.math.max(1, kotlin.math.round(value).toInt()))
            GoalType.VOLUME -> {
                val fmt = java.text.NumberFormat.getIntegerInstance(Locale.getDefault())
                    .format(kotlin.math.round(value).toLong())
                ctx.getString(R.string.manage_goal_stat_value_volume, fmt)
            }
            GoalType.COUNT ->
                ctx.getString(R.string.manage_goal_stat_value_count,
                    kotlin.math.round(value).toInt())
        }
    }

    /** Lifecycle-safe flow collector — bottom-sheets don't extend `BaseFragment`. */
    private fun <T> collectOnStart(flow: Flow<T>, block: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(block)
            }
        }
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "goal_action_result"
        const val ACTION_KEY = "action"
        const val GOAL_ID_KEY = "goal_id"
        const val ACTION_EDIT = "edit"
        const val ACTION_MAKE_MAIN = "make_main"
        const val ACTION_EXTEND = "extend"
        const val ACTION_DELETE = "delete"

        private const val ARG_GOAL_ID = "goal_id"
        private const val EXTEND_DAYS = 7

        fun newInstance(goalId: String): GoalActionsBottomSheet =
            GoalActionsBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
            }
    }
}
