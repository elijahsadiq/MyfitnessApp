package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.FragmentGoalsBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.navigateSafe
import com.elijah.fitness.util.ResourcesTextResolver
import com.google.android.material.snackbar.Snackbar

/**
 * Goals tab (phase 8b redesign). The whole screen is a single
 * RecyclerView: the header, main-goal card, section headers and all
 * goal rows are view types in the list. Tapping a goal opens the
 * Manage actions sheet; "Try again" on an ended row opens the New Goal
 * sheet. Delete fires a 5s Snackbar with Undo.
 */
class GoalsFragment : BaseFragment<FragmentGoalsBinding>() {

    private val viewModel: GoalsViewModel by viewModels {
        GoalsViewModel.factory((requireContext().applicationContext as FitnessApp).container)
    }

    private val adapter = GoalsAdapter(
        onGoalTap = ::onGoalTap,
        onTryAgainTap = ::onTryAgainTap,
        onEmptyHeroCta = ::openNewGoalSheet,
        onQuickStartTap = ::openQuickStart,
    )

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentGoalsBinding =
        FragmentGoalsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The bottom nav sits below this screen: only the status bar
        // (and side bars in landscape) need clearing.
        applySystemBarInsets(target = binding.root, applyBottom = false)

        binding.goalsList.layoutManager = LinearLayoutManager(requireContext())
        binding.goalsList.adapter = adapter

        binding.newGoalFab.setOnClickListener { openNewGoalSheet() }
        // Same FAB behaviour as History: out of the way while scrolling down.
        binding.goalsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!binding.newGoalFab.isEnabled) return
                when {
                    dy > FAB_SCROLL_SLOP -> binding.newGoalFab.hide()
                    dy < -FAB_SCROLL_SLOP -> binding.newGoalFab.show()
                }
            }
        })

        parentFragmentManager.setFragmentResultListener(
            GoalActionsBottomSheet.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            val action = result.getString(GoalActionsBottomSheet.ACTION_KEY)
            val goalId = result.getString(GoalActionsBottomSheet.GOAL_ID_KEY)
                ?: return@setFragmentResultListener
            when (action) {
                GoalActionsBottomSheet.ACTION_EDIT -> onEditRequested(goalId)
                GoalActionsBottomSheet.ACTION_MAKE_MAIN -> onMakeMainRequested(goalId)
                GoalActionsBottomSheet.ACTION_EXTEND -> onExtendRequested(goalId)
                GoalActionsBottomSheet.ACTION_DELETE -> showDeleteConfirm(goalId)
            }
        }

        parentFragmentManager.setFragmentResultListener(
            DeleteGoalConfirmDialog.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            val action = result.getString(DeleteGoalConfirmDialog.ACTION_KEY)
            val goalId = result.getString(DeleteGoalConfirmDialog.GOAL_ID_KEY)
                ?: return@setFragmentResultListener
            if (action == DeleteGoalConfirmDialog.ACTION_CONFIRM) {
                onDeleteConfirmed(goalId)
            }
        }

        parentFragmentManager.setFragmentResultListener(
            NewGoalBottomSheet.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            if (result.getBoolean(NewGoalBottomSheet.RESULT_SAVED_KEY, false)) {
                snackbar(R.string.new_goal_saved_snackbar, Snackbar.LENGTH_SHORT, kind = SnackKind.SUCCESS).show()
            } else if (result.getBoolean(NewGoalBottomSheet.RESULT_UNAVAILABLE_KEY, false)) {
                snackbar(R.string.new_goal_edit_unavailable_snackbar, kind = SnackKind.ERROR).show()
            }
        }

        collectOnStart(viewModel.items) { items ->
            // The list itself carries the empty-state composition (header
            // + dark hero card + Quick Start rows). With no goals the hero
            // card's "Create a goal" is the one action, so the FAB hides.
            adapter.submitList(items)
            val hasGoals = items.none { it is GoalListItem.EmptyHeroCard }
            binding.newGoalFab.isEnabled = hasGoals
            if (hasGoals) binding.newGoalFab.show() else binding.newGoalFab.hide()
        }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.goalsLoadingBar.isVisible = loading
        }
        collectOnStart(viewModel.errorEvents) { event ->
            when (event) {
                is GoalsViewModel.ErrorEvent.Network -> showRetrySnackbar(event.messageRes)
                is GoalsViewModel.ErrorEvent.Generic -> showRetrySnackbar(event.messageRes)
                is GoalsViewModel.ErrorEvent.DeletedWithUndo -> showUndoSnackbar(event.goalId)
                GoalsViewModel.ErrorEvent.MainGoalChanged ->
                    snackbar(R.string.goals_main_updated_snackbar, Snackbar.LENGTH_SHORT, kind = SnackKind.SUCCESS).show()
                GoalsViewModel.ErrorEvent.Extended ->
                    snackbar(R.string.goals_extended_snackbar, Snackbar.LENGTH_SHORT, kind = SnackKind.SUCCESS).show()
            }
        }
    }

    override fun snackbarAnchorView(): View? = bindingOrNull?.newGoalFab

    private fun openNewGoalSheet() {
        findNavController().navigateSafe(
            R.id.action_goals_to_new_goal,
            NewGoalBottomSheet.argsFor(goalId = null),
        )
    }

    private fun openQuickStart(suggestion: GoalSuggestion) {
        findNavController().navigateSafe(
            R.id.action_goals_to_new_goal,
            NewGoalBottomSheet.argsFor(
                prefillActivity = suggestion.activity,
                prefillMetric = suggestion.metric,
                prefillTarget = suggestion.target,
                prefillDays = suggestion.days,
            ),
        )
    }

    private fun onGoalTap(goal: GoalWithProgress) {
        // A fast double-tap would stack two sheets (same tag doesn't dedupe).
        if (parentFragmentManager.findFragmentByTag("goal_actions") != null) return
        GoalActionsBottomSheet.newInstance(goal.goal.id)
            .show(parentFragmentManager, "goal_actions")
    }

    private fun onTryAgainTap(goal: GoalWithProgress) {
        // Phase-8b step 8: pre-fill the New Goal screen from the ended
        // goal so one tap gives the user the same activity/metric/target
        // in a fresh window. The start date is deliberately NOT carried
        // across — "Try again" means a new goal with today as day 1.
        findNavController().navigateSafe(
            R.id.action_goals_to_new_goal,
            NewGoalBottomSheet.argsFor(
                prefillActivity = NewGoalViewModel.ActivityOption.fromApi(goal.goal.activityType),
                prefillMetric = goal.goal.goalType(),
                // A Double all the way: a 10.5 km swim stays 10.5 km.
                prefillTarget = goal.goal.targetValue,
                prefillDays = goal.goal.durationDays,
            ),
        )
    }

    private fun onEditRequested(goalId: String) {
        findNavController().navigateSafe(
            R.id.action_goals_to_new_goal,
            NewGoalBottomSheet.argsFor(goalId = goalId),
        )
    }

    // Success snackbars arrive as ViewModel events once the server
    // confirms — no "Added 7 days" for a request that then fails.
    private fun onMakeMainRequested(goalId: String) {
        viewModel.makeMainGoal(goalId)
    }

    private fun onExtendRequested(goalId: String) {
        viewModel.extendGoal(goalId, EXTEND_DAYS)
    }

    private fun showDeleteConfirm(goalId: String) {
        val goal = findGoalById(goalId) ?: return
        DeleteGoalConfirmDialog.newInstance(
            goalId = goal.id,
            title = goal.displayTitle(ResourcesTextResolver(resources)),
            activityType = goal.activityType,
        ).show(parentFragmentManager, "delete_goal_confirm")
    }

    private fun onDeleteConfirmed(goalId: String) {
        val goal = findGoalById(goalId) ?: return
        viewModel.deleteWithUndo(goal)
    }

    private fun findGoalById(id: String): Goal? =
        viewModel.items.value
            .asSequence()
            .mapNotNull { item ->
                when (item) {
                    is GoalListItem.MainGoalCard -> item.goal.goal
                    is GoalListItem.Active -> item.goal.goal
                    is GoalListItem.Completed -> item.goal.goal
                    is GoalListItem.Ended -> item.goal.goal
                    else -> null
                }
            }
            .firstOrNull { it.id == id }

    private fun showUndoSnackbar(goalId: String) {
        snackbar(R.string.goals_deleted_snackbar, kind = SnackKind.SUCCESS)
            .setAction(R.string.goals_deleted_undo) { viewModel.undoDelete(goalId) }
            .show()
    }

    private fun showRetrySnackbar(messageRes: Int) {
        snackbar(messageRes, kind = SnackKind.ERROR)
            .setAction(R.string.goals_retry) { viewModel.refresh() }
            .show()
    }

    private companion object {
        const val EXTEND_DAYS = 7
        /** Ignore tiny scroll jitters (px) before hiding / showing the FAB. */
        const val FAB_SCROLL_SLOP = 8
    }
}
