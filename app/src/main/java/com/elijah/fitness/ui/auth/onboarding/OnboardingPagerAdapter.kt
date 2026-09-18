package com.elijah.fitness.ui.auth.onboarding

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.elijah.fitness.R

/**
 * Three-page pager adapter for the onboarding flow. Each page reuses
 * [OnboardingPageFragment] with per-page image + copy resources.
 */
class OnboardingPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = PAGES.size

    override fun createFragment(position: Int): Fragment {
        val page = PAGES[position]
        return OnboardingPageFragment.newInstance(page.image, page.title, page.body)
    }

    private data class Page(val image: Int, val title: Int, val body: Int)

    private companion object {
        val PAGES = listOf(
            Page(
                image = R.drawable.img_onboarding_cycling,
                title = R.string.auth_onboarding_page1_title,
                body = R.string.auth_onboarding_page1_body,
            ),
            Page(
                image = R.drawable.img_onboarding_cycling,
                title = R.string.auth_onboarding_page2_title,
                body = R.string.auth_onboarding_page2_body,
            ),
            Page(
                image = R.drawable.img_onboarding_progress,
                title = R.string.auth_onboarding_page3_title,
                body = R.string.auth_onboarding_page3_body,
            ),
        )
    }
}
