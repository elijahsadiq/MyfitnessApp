package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.databinding.FragmentGoalsBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * Goals tab (phase 7 step 4). Rebuilt from the Stitch design: header
 * with title + "New goal" pill, a featured card for the most urgent
 * active goal, a list of other active goals, and a Completed section.
 * Tapping a row opens the actions sheet (Edit / Delete); delete shows
 * a Snackbar with Undo.
 *
 * The new-goal + edit flows are wired to placeholder Snackbars until
 * Step 5 delivers the bottom sheet.
 */
class GoalsFragment : BaseFragment<FragmentGoalsBinding>() {

    private val viewModel: GoalsViewModel by viewModels {
        GoalsViewModel.factory((requireContext().applicationContext as FitnessApp).container)
    }

    private val adapter = GoalsAdapter(::onGoalTap)

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentGoalsBinding =
        FragmentGoalsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.goalsList.layoutManager = LinearLayoutManager(requireContext())
        binding.goalsList.adapter = adapter

        binding.newGoalButton.setOnClickListener {
            // Step 5 will replace this with a navigation to the New Goal sheet.
            Snackbar.make(binding.root, R.string.goals_new_goal_todo_snackbar,
                Snackbar.LENGTH_SHORT).show()
        }

        parentFragmentManager.setFragmentResultListener(
            GoalActionsBottomSheet.FRAGMENT_RESULT_KEY, viewLifecycleOwner,
        ) { _, result ->
            val action = result.getString(GoalActionsBottomSheet.ACTION_KEY)
            val goalId = result.getString(GoalActionsBottomSheet.GOAL_ID_KEY)
                ?: return@setFragmentResultListener
            when (action) {
                GoalActionsBottomSheet.ACTION_EDIT -> onEditRequested()
                GoalActionsBottomSheet.ACTION_DELETE -> onDeleteConfirmed(goalId)
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
                is GoalsViewModel.ErrorEvent.DeletedWithUndo -> showUndoSnackbar(event.snapshot)
            }
        }
    }

    private fun onGoalTap(goal: GoalWithProgress) {
        GoalActionsBottomSheet.newInstance(goal.goal.id)
            .show(parentFragmentManager, "goal_actions")
    }

    private fun onEditRequested() {
        // Step 5 will replace this with a navigation to the edit variant of
        // the New Goal bottom sheet.
        Snackbar.make(binding.root, R.string.goals_edit_todo_snackbar,
            Snackbar.LENGTH_SHORT).show()
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
                    is GoalListItem.Featured -> item.goal.goal
                    is GoalListItem.Active -> item.goal.goal
                    is GoalListItem.Completed -> item.goal.goal
                    else -> null
                }
            }
            .firstOrNull { it.id == id }

    private fun showUndoSnackbar(snapshot: GoalsViewModel.GoalSnapshot) {
        Snackbar.make(binding.root, R.string.goals_deleted_snackbar, Snackbar.LENGTH_LONG)
            .setAction(R.string.goals_deleted_undo) { viewModel.undoDelete(snapshot) }
            .show()
    }

    private fun showRetrySnackbar(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.goals_retry) { viewModel.refresh() }
            .show()
    }
}
