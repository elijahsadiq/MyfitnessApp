package com.elijah.fitness.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.databinding.FragmentProfileBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Phase-1 profile screen — read-only view of the body weight used for
 * calorie calculations. Editing lands in a later phase.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userSettings = (requireContext().applicationContext as FitnessApp)
            .container.userSettings
        collectOnStart(userSettings.bodyWeightKg) { kg ->
            binding.bodyWeightValue.text = getString(com.elijah.fitness.R.string.profile_body_weight_value, kg)
        }
    }
}
