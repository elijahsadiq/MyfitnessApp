package com.elijah.fitness.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import com.elijah.fitness.databinding.FragmentOnboardingPageBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * One page inside the onboarding [androidx.viewpager2.widget.ViewPager2].
 * Extends the phase-1 `BaseFragment` (per the spec — every fragment
 * except the bottom sheet must). Content is passed via arguments so the
 * three page instances share one layout and one class.
 */
class OnboardingPageFragment : BaseFragment<FragmentOnboardingPageBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentOnboardingPageBinding =
        FragmentOnboardingPageBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        binding.heroImage.setImageResource(args.getInt(ARG_IMAGE))
        binding.pageTitle.setText(args.getInt(ARG_TITLE))
        binding.pageBody.setText(args.getInt(ARG_BODY))
    }

    companion object {
        private const val ARG_IMAGE = "image"
        private const val ARG_TITLE = "title"
        private const val ARG_BODY = "body"

        fun newInstance(
            @DrawableRes image: Int,
            @StringRes title: Int,
            @StringRes body: Int,
        ): OnboardingPageFragment = OnboardingPageFragment().apply {
            arguments = bundleOf(ARG_IMAGE to image, ARG_TITLE to title, ARG_BODY to body)
        }
    }
}
