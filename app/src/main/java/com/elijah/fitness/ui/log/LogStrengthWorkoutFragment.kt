package com.elijah.fitness.ui.log

import android.view.LayoutInflater
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentLogStrengthWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Step 6 stub. Step 7 adds the sets list, add-set dialog, and save logic.
 */
class LogStrengthWorkoutFragment : BaseFragment<FragmentLogStrengthWorkoutBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLogStrengthWorkoutBinding =
        FragmentLogStrengthWorkoutBinding.inflate(inflater, container, false)
}
