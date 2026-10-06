package com.elijah.fitness.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentProgressBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Progress tab placeholder. A full charts + analytics rebuild lands as
 * Phase-8 Step 5; this file currently just consumes system-bar insets
 * and renders the design-system placeholder.
 */
class ProgressFragment : BaseFragment<FragmentProgressBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProgressBinding =
        FragmentProgressBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets()
    }
}
