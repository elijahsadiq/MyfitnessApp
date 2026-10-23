package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentWorkoutHistoryBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.navigateSafe
import kotlinx.coroutines.flow.combine

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
        findNavController().navigateSafe(
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
        // The bottom nav sits below this screen: only the status bar
        // (and side bars in landscape) need clearing.
        applySystemBarInsets(applyBottom = false)

        // Standard FAB behaviour: tuck it away while scrolling down so it
        // never sits on top of a row you're reading; bring it back on the
        // way up.
        binding.scroll.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
                when {
                    y > oldY + FAB_SCROLL_SLOP -> binding.addWorkoutFab.hide()
                    y < oldY - FAB_SCROLL_SLOP || y == 0 -> binding.addWorkoutFab.show()
                }
            }
        )

        binding.workoutList.adapter = adapter
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())

        ItemTouchHelper(
            WorkoutSwipeToDeleteCallback(adapter) { position ->
                val item = adapter.currentList.getOrNull(position)
                if (item is HistoryItem.Row) viewModel.deleteWithUndo(item.workout)
            }
        ).attachToRecyclerView(binding.workoutList)

        binding.addWorkoutFab.setOnClickListener {
            findNavController().navigateSafe(R.id.action_history_to_choose_activity)
        }

        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                binding.chipRunning.id -> HistoryViewModel.Filter.RUNNING
                binding.chipCycling.id -> HistoryViewModel.Filter.CYCLING
                binding.chipSwimming.id -> HistoryViewModel.Filter.SWIMMING
                binding.chipRowing.id -> HistoryViewModel.Filter.ROWING
                binding.chipWeightlifting.id -> HistoryViewModel.Filter.WEIGHTLIFTING
                else -> HistoryViewModel.Filter.ALL
            }
            viewModel.setFilter(filter)
        }

        // The filter is collected with the list so switching between two
        // empty filters still updates the "No swims yet" wording.
        collectOnStart(viewModel.sectioned.combine(viewModel.filter) { items, filter -> items to filter }) {
                (items, filter) ->
            adapter.submitList(items)
            val isEmpty = items.isEmpty()
            binding.emptyState.isVisible = isEmpty
            binding.workoutList.isVisible = !isEmpty
            if (isEmpty) renderEmptyState(filter)
        }
        collectOnStart(viewModel.momentum) { renderMomentum(it) }
        collectOnStart(viewModel.isLoading) { loading ->
            binding.loadingBar.isVisible = loading
        }
        collectOnStart(viewModel.errorEvents) { event ->
            when (event) {
                is HistoryViewModel.ErrorEvent.Network ->
                    snackbar(event.messageRes, kind = SnackKind.ERROR)
                        .setAction(R.string.workouts_retry) { viewModel.refresh() }
                        .show()
                is HistoryViewModel.ErrorEvent.Generic ->
                    snackbar(event.messageRes, kind = SnackKind.ERROR)
                        .setAction(R.string.workouts_retry) { viewModel.refresh() }
                        .show()
                is HistoryViewModel.ErrorEvent.DeletedWithUndo -> {
                    snackbar(R.string.history_deleted_snackbar, kind = SnackKind.SUCCESS)
                        .setAction(R.string.history_deleted_undo) {
                            viewModel.undoDelete(event.workoutId)
                        }
                        .show()
                }
            }
        }
    }

    override fun snackbarAnchorView(): View? = bindingOrNull?.addWorkoutFab

    override fun onDestroyView() {
        binding.workoutList.adapter = null
        super.onDestroyView()
    }

    private companion object {
        /** Ignore tiny scroll jitters (px) before hiding / showing the FAB. */
        const val FAB_SCROLL_SLOP = 8
    }

    /** "No workouts yet" for All; "No swims yet" etc. for one activity. */
    private fun renderEmptyState(filter: HistoryViewModel.Filter) {
        val titleRes = when (filter) {
            HistoryViewModel.Filter.ALL -> R.string.history_empty_title
            HistoryViewModel.Filter.RUNNING -> R.string.history_empty_title_running
            HistoryViewModel.Filter.CYCLING -> R.string.history_empty_title_cycling
            HistoryViewModel.Filter.SWIMMING -> R.string.history_empty_title_swimming
            HistoryViewModel.Filter.ROWING -> R.string.history_empty_title_rowing
            HistoryViewModel.Filter.WEIGHTLIFTING -> R.string.history_empty_title
        }
        binding.emptyTitle.setText(titleRes)
        binding.emptyBody.setText(
            if (filter == HistoryViewModel.Filter.ALL) R.string.history_empty_body
            else R.string.history_empty_body_filtered
        )
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
