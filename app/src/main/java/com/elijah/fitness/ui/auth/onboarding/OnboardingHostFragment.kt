package com.elijah.fitness.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentOnboardingHostBinding
import com.elijah.fitness.ui.base.BaseFragment
import kotlinx.coroutines.launch

/**
 * Onboarding host — a ViewPager2 with three [OnboardingPageFragment]
 * pages, plus the shared page-indicator + navigation controls the pages
 * themselves don't own.
 *
 * Page 1-2 show Skip (top) + indicator + circular arrow (bottom).
 * Page 3 hides those and shows "Get started" + "I already have an
 * account". Both terminal paths mark onboarding as completed so a
 * relaunch doesn't repeat this flow.
 *
 * Forces light status-bar icons while attached because the surface is
 * `surface_dark`; restores the default on detach so the light-bg
 * login/register screens still see dark icons.
 */
class OnboardingHostFragment : BaseFragment<FragmentOnboardingHostBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentOnboardingHostBinding =
        FragmentOnboardingHostBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onboardingPager.adapter = OnboardingPagerAdapter(this)
        binding.pageIndicator.count = PAGE_COUNT

        binding.onboardingPager.registerOnPageChangeCallback(pageChangeCallback)
        renderControls(page = 0)

        binding.skipButton.setOnClickListener { finishOnboardingTo(R.id.action_onboarding_to_login) }
        binding.nextButton.setOnClickListener {
            val next = binding.onboardingPager.currentItem + 1
            if (next < PAGE_COUNT) {
                binding.onboardingPager.currentItem = next
            } else {
                finishOnboardingTo(R.id.action_onboarding_to_location_permission)
            }
        }
        binding.getStartedButton.setOnClickListener {
            finishOnboardingTo(R.id.action_onboarding_to_location_permission)
        }
        binding.haveAccountButton.setOnClickListener {
            finishOnboardingTo(R.id.action_onboarding_to_login)
        }
    }

    override fun onResume() {
        super.onResume()
        setLightStatusBarIcons(false)
    }

    override fun onPause() {
        super.onPause()
        // Reset for the next destination in case it uses a light surface.
        setLightStatusBarIcons(true)
    }

    override fun onDestroyView() {
        binding.onboardingPager.unregisterOnPageChangeCallback(pageChangeCallback)
        binding.onboardingPager.adapter = null
        super.onDestroyView()
    }

    private fun setLightStatusBarIcons(lightIcons: Boolean) {
        val window = requireActivity().window
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }

    private fun renderControls(page: Int) {
        binding.pageIndicator.activeIndex = page
        val isLast = page == PAGE_COUNT - 1
        binding.skipButton.isVisible = !isLast
        binding.pageIndicator.isVisible = !isLast
        binding.nextButton.isVisible = !isLast
        binding.getStartedButton.isVisible = isLast
        binding.haveAccountButton.isVisible = isLast
    }

    private fun finishOnboardingTo(actionId: Int) {
        val container = (requireContext().applicationContext as FitnessApp).container
        // Persist onboarding-completed as soon as the user exits any way,
        // so a Skip / Get started / "I already have an account" tap all
        // stop the pages reappearing on the next launch.
        viewLifecycleOwner.lifecycleScope.launch {
            container.onboardingStore.markCompleted()
            findNavController().navigate(actionId)
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            renderControls(position)
        }
    }

    private companion object {
        const val PAGE_COUNT = 3
    }
}
