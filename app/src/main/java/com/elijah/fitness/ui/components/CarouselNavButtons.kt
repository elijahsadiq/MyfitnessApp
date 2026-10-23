package com.elijah.fitness.ui.components

import android.view.View

/**
 * The round previous / next buttons beside a carousel's dots (phase 8d,
 * `Widget.Fitness.RoundIconButton`). At either end the button that
 * can't move any further fades to [DISABLED_ALPHA] and stops responding,
 * like the designs' `opacity: 0.35`.
 */
object CarouselNavButtons {

    const val DISABLED_ALPHA = 0.35f

    /** Wires the two buttons to step [carousel] one card at a time. */
    fun bind(previous: View, next: View, carousel: ActivityCarouselView) {
        previous.setOnClickListener { carousel.showPrevious() }
        next.setOnClickListener { carousel.showNext() }
    }

    /** Fades out whichever button has no card left in its direction. */
    fun update(previous: View, next: View, index: Int, count: Int) {
        setActive(previous, index > 0)
        setActive(next, index < count - 1)
    }

    private fun setActive(button: View, active: Boolean) {
        button.isEnabled = active
        button.alpha = if (active) 1f else DISABLED_ALPHA
    }
}
