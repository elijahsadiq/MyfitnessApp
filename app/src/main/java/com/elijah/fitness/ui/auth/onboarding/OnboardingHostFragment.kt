package com.elijah.fitness.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentOnboardingHostBinding
import com.elijah.fitness.ui.base.BaseFragment
import kotlinx.coroutines.launch

/**
 * Placeholder for the ViewPager2-based onboarding host. Step 6 replaces
 * this with three welcome pages + the location-permission priming
 * screen. Until then, the "Continue" button marks onboarding done and
 * navigates to the login destination — enough to verify the launcher's
 * first-launch routing works end-to-end.
 */
class OnboardingHostFragment : BaseFragment<FragmentOnboardingHostBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentOnboardingHostBinding =
        FragmentOnboardingHostBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onboardingContinue.setOnClickListener {
            val container = (requireContext().applicationContext as FitnessApp).container
            viewLifecycleOwner.lifecycleScope.launch {
                container.onboardingStore.markCompleted()
                findNavController().navigate(R.id.action_onboarding_to_login)
            }
        }
    }
}
