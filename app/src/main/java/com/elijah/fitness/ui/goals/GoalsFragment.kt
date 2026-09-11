package com.elijah.fitness.ui.goals

import android.view.LayoutInflater
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentGoalsBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Placeholder for the goals screen. Real goals logic arrives in phase 5.
 */
class GoalsFragment : BaseFragment<FragmentGoalsBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentGoalsBinding =
        FragmentGoalsBinding.inflate(inflater, container, false)
}
