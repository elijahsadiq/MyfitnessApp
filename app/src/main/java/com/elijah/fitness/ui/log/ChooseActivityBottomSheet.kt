package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.BottomSheetChooseActivityBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Presented by the History FAB. Offers the three phase-1 activity types
 * and navigates to the matching log screen, passing the chosen
 * [ActivityType] as a String nav argument.
 *
 * Note: this fragment can't extend `BaseFragment` because it has to extend
 * `BottomSheetDialogFragment`. It handles binding lifecycle inline —
 * short enough to justify duplicating the pattern.
 */
class ChooseActivityBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseActivityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetChooseActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.optionRunning.setOnClickListener {
            navigateToLogDistance(ActivityType.RUNNING)
        }
        binding.optionCycling.setOnClickListener {
            navigateToLogDistance(ActivityType.CYCLING)
        }
        binding.optionWeightlifting.setOnClickListener {
            findNavController().navigate(
                R.id.action_choose_to_log_strength,
                bundleOf(ARG_ACTIVITY_TYPE to ActivityType.WEIGHTLIFTING.name)
            )
        }
    }

    private fun navigateToLogDistance(type: ActivityType) {
        findNavController().navigate(
            R.id.action_choose_to_log_distance,
            bundleOf(ARG_ACTIVITY_TYPE to type.name)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ACTIVITY_TYPE = "activityType"
    }
}
