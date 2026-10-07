package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.BottomSheetChooseActivityBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
class ChooseActivityBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseActivityBinding? = null
    private val binding get() = _binding!!

    private var pendingDistance: ActivityType? = null

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

        binding.optionRunning.setOnClickListener { showModeStep(ActivityType.RUNNING) }
        binding.optionCycling.setOnClickListener { showModeStep(ActivityType.CYCLING) }
        binding.optionWeightlifting.setOnClickListener {
            findNavController().navigate(
                R.id.action_choose_to_log_strength,
                bundleOf(ARG_ACTIVITY_TYPE to ActivityType.WEIGHTLIFTING.name)
            )
        }

        binding.optionTrack.setOnClickListener { onTrackChosen() }
        binding.optionManual.setOnClickListener { onManualChosen() }
        binding.modeBackButton.setOnClickListener { showActivityStep() }
    }

    private fun showActivityStep() {
        pendingDistance = null
        binding.stepActivity.isVisible = true
        binding.stepMode.isVisible = false
    }

    private fun showModeStep(activityType: ActivityType) {
        pendingDistance = activityType
        binding.stepActivity.isVisible = false
        binding.stepMode.isVisible = true
    }

    private fun onTrackChosen() {
        val activity = pendingDistance ?: return
        findNavController().navigate(
            R.id.action_choose_to_live_tracking,
            bundleOf(ARG_ACTIVITY_TYPE to activity.name),
        )
    }

    private fun onManualChosen() {
        val activity = pendingDistance ?: return
        findNavController().navigate(
            R.id.action_choose_to_log_distance,
            bundleOf(ARG_ACTIVITY_TYPE to activity.name),
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
