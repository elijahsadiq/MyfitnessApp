package com.elijah.fitness.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import com.elijah.fitness.databinding.FragmentDashboardBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Weekly-stats dashboard. Step 5 ships a placeholder; Step 8 replaces the
 * body with real cards driven by the workout repository.
 */
class DashboardFragment : BaseFragment<FragmentDashboardBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDashboardBinding =
        FragmentDashboardBinding.inflate(inflater, container, false)
}
