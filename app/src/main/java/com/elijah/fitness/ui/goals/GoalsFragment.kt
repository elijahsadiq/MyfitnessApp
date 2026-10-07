package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.databinding.FragmentGoalsBinding
import com.elijah.fitness.ui.base.BaseFragment
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
        onNewGoalTap = ::openNewGoalSheet,
        onTryAgainTap = ::onTryAgainTap,
    )

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentGoalsBinding =
        FragmentGoalsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Status-bar inset lives on the fragment root; nav-bar inset is
        // applied to the list so it clears the bottom-nav rail without a
        // hardcoded padding. Preserve the list's XML `paddingBottom =
        // fab_margin_bottom` and ADD the inset on top (straight assignment
        // would wipe + accumulate across re-dispatch).
        applySystemBarInsets(target = binding.root, applyBottom = false)
        val listBaseBottom = binding.goalsList.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.goalsList) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = listBaseBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.goalsList)

        binding.goalsList.layoutManager = LinearLayoutManager(requireContext())
        binding.goalsList.adapter = adapter

        binding.newGoalEmptyButton.setOnClickListener { openNewGoalSheet() }

        parentFragmentManager.setFragmentResultListener(
            GoalActionsBottomSheet.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            val action = result.getString(GoalActionsBottomSheet.ACTION_KEY)
            val goalId = result.getString(GoalActionsBottomSheet.GOAL_ID_KEY)
                ?: return@setFragmentResultListener
            when (action) {
                GoalActionsBottomSheet.ACTION_EDIT -> onEditRequested(goalId)
                GoalActionsBottomSheet.ACTION_DELETE -> onDeleteConfirmed(goalId)
            }
        }

        parentFragmentManager.setFragmentResultListener(
            NewGoalFragment.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            if (result.getBoolean(NewGoalFragment.RESULT_SAVED_KEY, false)) {
                Snackbar.make(binding.root, R.string.new_goal_saved_snackbar,
                    Snackbar.LENGTH_SHORT).show()
            }
        }

        collectOnStart(viewModel.items) { items ->
            adapter.submitList(items)
            val hasContent = items.isNotEmpty()
            binding.goalsList.isVisible = hasContent
            binding.goalsEmptyState.isVisible =
                !hasContent && !viewModel.isLoading.value
        }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.goalsLoadingBar.isVisible = loading
            if (loading) binding.goalsEmptyState.isVisible = false
        }
        collectOnStart(viewModel.errorEvents) { event ->
            when (event) {
                is GoalsViewModel.ErrorEvent.Network -> showRetrySnackbar(event.messageRes)
                is GoalsViewModel.ErrorEvent.Generic -> showRetrySnackbar(event.messageRes)
                is GoalsViewModel.ErrorEvent.DeletedWithUndo -> showUndoSnackbar(event.goalId)
                is GoalsViewModel.ErrorEvent.GoalCompleted -> showCompletionSnackbar(event.title)
            }
        }
    }

    private fun openNewGoalSheet() {
        findNavController().navigate(
            R.id.action_goals_to_new_goal,
            NewGoalFragment.argsFor(goalId = null),
        )
    }

    private fun onGoalTap(goal: GoalWithProgress) {
        GoalActionsBottomSheet.newInstance(goal.goal.id)
            .show(parentFragmentManager, "goal_actions")
    }

    private fun onTryAgainTap(goal: GoalWithProgress) {
        // Phase-8b Step 6: "Try again" opens a fresh New Goal screen. Pre-
        // fill from the ended goal is a stretch that doesn't belong here —
        // the user still gets the right activity-type stepper defaults the
        // moment they re-pick the activity, so the extra taps are small.
        openNewGoalSheet()
    }

    private fun onEditRequested(goalId: String) {
        findNavController().navigate(
            R.id.action_goals_to_new_goal,
            NewGoalFragment.argsFor(goalId = goalId),
        )
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
        Snackbar.make(binding.root, R.string.goals_deleted_snackbar, Snackbar.LENGTH_LONG)
            .setAction(R.string.goals_deleted_undo) { viewModel.undoDelete(goalId) }
            .show()
    }

    private fun showRetrySnackbar(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.goals_retry) { viewModel.refresh() }
            .show()
    }

    private fun showCompletionSnackbar(title: String) {
        val text = getString(R.string.goals_completed_snackbar, title)
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }
}
