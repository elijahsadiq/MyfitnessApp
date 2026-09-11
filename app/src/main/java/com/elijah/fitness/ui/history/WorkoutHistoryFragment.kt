package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentWorkoutHistoryBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Workout history screen: RecyclerView with two row types, filter chips,
 * empty state, and a FAB that opens the "choose activity" bottom sheet.
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
    }

    override fun onDestroyView() {
        binding.workoutList.adapter = null
        super.onDestroyView()
    }
}
