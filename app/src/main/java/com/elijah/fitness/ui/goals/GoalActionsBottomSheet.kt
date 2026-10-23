package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.BottomSheetGoalActionsBinding
import com.elijah.fitness.util.Fonts
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.MetricText
import com.elijah.fitness.util.ResourcesTextResolver
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
        // onViewCreated closes the sheet before reaching here without an id.
        val goalId = arguments?.getString(ARG_GOAL_ID).orEmpty()
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
        // Opened without a goal (never by the app itself): close quietly
        // instead of crashing.
        val goalId = arguments?.getString(ARG_GOAL_ID)
        if (goalId == null) {
            dismissAllowingStateLoss()
            return
        }

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
        val text = ResourcesTextResolver(resources)
        val goal = snapshot.goal
        val type = goal.goalType()

        // Header: icon · title · subtitle. The icon is the goal's
        // carousel glyph (pulse for "any activity"); the subtitle wraps
        // between its parts, never inside the date.
        binding.manageGoalIcon.setImageResource(GoalDisplay.iconRes(goal.activityType))
        binding.manageGoalIcon.contentDescription = getString(GoalDisplay.activityNameRes(goal.activityType))
        binding.manageGoalTitle.text = GoalDisplay.titleForDisplay(goal.displayTitle(text))
        binding.manageGoalSubtitle.text =
            GoalDisplay.manageSubtitle(text, goal.activityType, type, goal.startDate)

        // Progress row. Time goals read "1 h 10 min" (small units) and
        // "of 2 h 30 min" — never raw minutes; distance "6.5 of 10 km".
        binding.manageGoalCurrentValue.text = if (type == GoalType.DURATION) {
            MetricText.duration(
                totalMinutes = GoalDisplay.currentMinutes(snapshot.currentValue),
                hoursUnit = getString(R.string.duration_unit_hours),
                minutesUnit = getString(R.string.duration_unit_minutes),
                unitColor = ContextCompat.getColor(ctx, R.color.text_secondary),
                unitScale = DURATION_UNIT_SCALE,
                unitTypeface = Fonts.medium(ctx),
            )
        } else {
            GoalDisplay.currentNumber(type, snapshot.currentValue, goal.targetValue)
        }
        binding.manageGoalOfTarget.text =
            GoalDisplay.ofTargetText(text, type, goal.activityType, goal.targetValue)
        val pct = (snapshot.progressFraction * 100).toInt()
        binding.manageGoalPercent.text = ctx.getString(R.string.home_progress_ring_label, pct)
        binding.manageGoalProgress.progress = pct

        // Stat tiles.
        binding.manageGoalStatAvg.text = GoalDisplay.perDayText(text, type, snapshot.averagePerDay)
        binding.manageGoalStatNeeded.text = GoalDisplay.perDayText(text, type, snapshot.neededPerDay)
        binding.manageGoalStatSessionsLabel.setText(sessionsLabelRes(goal.activityType))
        binding.manageGoalStatSessions.text = Formatters.formatGoalAmount(snapshot.sessionsLogged.toDouble())

        // "Make main" only for an active goal that isn't already the main
        // one — finished goals never appear on the main card.
        val showMakeMain = snapshot.isActive && !snapshot.isMain
        binding.manageGoalActionMakeMain.isVisible = showMakeMain
        binding.manageGoalDividerAfterMakeMain.isVisible = showMakeMain

        // "Add 7 more days" only while the goal is running (the server
        // refuses ended goals) and while it stays within a year.
        val showExtend = snapshot.isActive &&
            goal.durationDays + EXTEND_DAYS <= NewGoalPlan.DAYS_MAX
        binding.manageGoalActionExtend.isVisible = showExtend
        binding.manageGoalDividerAfterExtend.isVisible = showExtend
    }

    // ---------- formatting helpers ----------

    private fun sessionsLabelRes(activity: ActivityType?): Int = when (activity) {
        ActivityType.RUNNING -> R.string.manage_goal_stat_runs_logged
        ActivityType.CYCLING -> R.string.manage_goal_stat_rides_logged
        ActivityType.SWIMMING -> R.string.manage_goal_stat_swims_logged
        ActivityType.ROWING -> R.string.manage_goal_stat_rows_logged
        ActivityType.WEIGHTLIFTING -> R.string.manage_goal_stat_workouts_logged
        null -> R.string.manage_goal_stat_sessions_logged
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

        /** "h" / "min" at 15sp next to the 26sp numbers, matching "of …". */
        private const val DURATION_UNIT_SCALE = 15f / 26f

        fun newInstance(goalId: String): GoalActionsBottomSheet =
            GoalActionsBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
            }
    }
}
