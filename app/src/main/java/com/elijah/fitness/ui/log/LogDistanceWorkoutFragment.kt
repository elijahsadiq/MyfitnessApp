package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentLogDistanceWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Step 6 stub. Real form fields (date/time, duration, distance, notes,
 * validation, live pace/speed preview) land in Step 7. Existing behaviour:
 * reads the [ActivityType] passed by [ChooseActivityBottomSheet] and shows
 * the right title.
 */
class LogDistanceWorkoutFragment : BaseFragment<FragmentLogDistanceWorkoutBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLogDistanceWorkoutBinding =
        FragmentLogDistanceWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activityType = arguments
            ?.getString(ChooseActivityBottomSheet.ARG_ACTIVITY_TYPE)
            ?.let(ActivityType::valueOf)
            ?: ActivityType.RUNNING

        binding.logDistanceTitle.text = getString(
            when (activityType) {
                ActivityType.RUNNING -> R.string.log_distance_title_running
                ActivityType.CYCLING -> R.string.log_distance_title_cycling
                else -> R.string.log_distance_title_running
            }
        )
    }
}
