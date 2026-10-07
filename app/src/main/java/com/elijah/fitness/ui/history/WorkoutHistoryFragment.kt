package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentWorkoutHistoryBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.util.Formatters
import com.google.android.material.snackbar.Snackbar

/**
 * Workout history screen (Step 9 restyle).
 *
 * Renders the dark "Weekly momentum" card (this week's total km + the
 * 7-bar chart + a trend pill), the filter chips (with a count badge on
 * "All"), and a section-headered list of workouts. FAB opens the
 * "Choose activity" bottom sheet.
 */
class WorkoutHistoryFragment : BaseFragment<FragmentWorkoutHistoryBinding>() {

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container
        )
    }

    private val adapter = WorkoutAdapter(::onWorkoutTapped)

    private fun onWorkoutTapped(workout: com.elijah.fitness.data.model.Workout) {
        findNavController().navigate(
            R.id.action_history_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to workout.id),
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentWorkoutHistoryBinding =
        FragmentWorkoutHistoryBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(applyBottom = false)
        applyBottomNavInset(binding.scroll)

        binding.workoutList.adapter = adapter
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())

        ItemTouchHelper(
            WorkoutSwipeToDeleteCallback(adapter) { position ->
                val item = adapter.currentList.getOrNull(position)
                if (item is HistoryItem.Row) viewModel.deleteWithUndo(item.workout)
            }
        ).attachToRecyclerView(binding.workoutList)

        binding.addWorkoutFab.setOnClickListener {
            findNavController().navigate(R.id.action_history_to_choose_activity)
        }

        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                binding.chipRunning.id -> HistoryViewModel.Filter.RUNNING
                binding.chipCycling.id -> HistoryViewModel.Filter.CYCLING
                binding.chipWeightlifting.id -> HistoryViewModel.Filter.WEIGHTLIFTING
                else -> HistoryViewModel.Filter.ALL
            }
            viewModel.setFilter(filter)
        }

        collectOnStart(viewModel.sectioned) { items ->
            adapter.submitList(items)
            val isEmpty = items.isEmpty()
            binding.emptyState.isVisible = isEmpty
            binding.workoutList.isVisible = !isEmpty
        }
        collectOnStart(viewModel.totalCount) { count ->
            binding.chipAll.text = if (count > 0) {
                getString(R.string.history_filter_all_with_count, count)
            } else {
                getString(R.string.history_filter_all)
            }
        }
        collectOnStart(viewModel.momentum) { renderMomentum(it) }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.loadingBar.isVisible = loading
        }
        collectOnStart(viewModel.errorEvents) { event ->
            when (event) {
                is HistoryViewModel.ErrorEvent.Network ->
                    Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_LONG)
                        .setAction(R.string.workouts_retry) { viewModel.refresh() }
                        .show()
                is HistoryViewModel.ErrorEvent.Generic ->
                    Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_LONG)
                        .setAction(R.string.workouts_retry) { viewModel.refresh() }
                        .show()
                is HistoryViewModel.ErrorEvent.DeletedWithUndo -> {
                    Snackbar.make(binding.root, R.string.history_deleted_snackbar,
                        Snackbar.LENGTH_LONG)
                        .setAction(R.string.history_deleted_undo) {
                            viewModel.undoDelete(event.workoutId)
                        }
                        .show()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.workoutList.adapter = null
        super.onDestroyView()
    }

    private fun renderMomentum(m: HistoryViewModel.Momentum) {
        binding.momentumTotal.text = String.format(
            java.util.Locale.getDefault(),
            "%.1f",
            m.totalDistanceMeters / 1000.0,
        )
        binding.momentumChart.setData(m.perDayMeters, m.activeDayIndex)

        val trend = m.trendPercent
        if (trend != null) {
            binding.momentumTrendPill.isVisible = true
            binding.momentumTrendPill.text = getString(
                R.string.history_momentum_trend_format,
                if (trend >= 0) "+" else "",
                trend,
            )
        } else {
            binding.momentumTrendPill.isVisible = false
        }
    }
}
