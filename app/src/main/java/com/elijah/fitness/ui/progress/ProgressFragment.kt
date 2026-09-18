package com.elijah.fitness.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentProgressBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Placeholder for the Progress tab. Real charts arrive in phase 5.
 * Occupies the third bottom-nav slot so the four tabs match the design.
 */
class ProgressFragment : BaseFragment<FragmentProgressBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProgressBinding =
        FragmentProgressBinding.inflate(inflater, container, false)
}
