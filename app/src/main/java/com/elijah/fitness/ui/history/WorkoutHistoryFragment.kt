package com.elijah.fitness.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentWorkoutHistoryBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * List of past workouts. Step 5 ships a placeholder; Step 6 replaces the
 * body with a RecyclerView + filter chips + FAB.
 */
class WorkoutHistoryFragment : BaseFragment<FragmentWorkoutHistoryBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentWorkoutHistoryBinding =
        FragmentWorkoutHistoryBinding.inflate(inflater, container, false)
}
