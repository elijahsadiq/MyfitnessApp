package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentWorkoutHistoryBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * Workout history screen: RecyclerView with two row types, filter chips,
 * empty state, and a FAB that opens the "choose activity" bottom sheet.
 *
 * Phase-2 additions: a top LinearProgressIndicator while the repository
 * refreshes, and a Snackbar-with-Retry when the refresh fails. Any
 * cached data stays on screen during a failed refresh — the user isn't
 * left staring at a blank list.
 */
class WorkoutHistoryFragment : BaseFragment<FragmentWorkoutHistoryBinding>() {

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container
        )
    }

    private val adapter = WorkoutAdapter()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentWorkoutHistoryBinding =
        FragmentWorkoutHistoryBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.workoutList.adapter = adapter
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())

        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                binding.chipRunning.id -> HistoryViewModel.Filter.RUNNING
                binding.chipCycling.id -> HistoryViewModel.Filter.CYCLING
                binding.chipWeightlifting.id -> HistoryViewModel.Filter.WEIGHTLIFTING
                else -> HistoryViewModel.Filter.ALL
            }
            viewModel.setFilter(filter)
        }

        binding.addWorkoutFab.setOnClickListener {
            findNavController().navigate(R.id.action_history_to_choose_activity)
        }

        collectOnStart(viewModel.workouts) { workouts ->
            adapter.submitList(workouts)
            showEmptyState(
                emptyView = binding.emptyState,
                contentView = binding.workoutList,
                isEmpty = workouts.isEmpty()
            )
        }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.loadingBar.isVisible = loading
        }
        collectOnStart(viewModel.errorEvents) { event ->
            val messageRes = when (event) {
                is HistoryViewModel.ErrorEvent.Network -> event.messageRes
                is HistoryViewModel.ErrorEvent.Generic -> event.messageRes
            }
            Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
                .setAction(R.string.workouts_retry) { viewModel.refresh() }
                .show()
        }
    }

    override fun onDestroyView() {
        binding.workoutList.adapter = null
        super.onDestroyView()
    }
}
