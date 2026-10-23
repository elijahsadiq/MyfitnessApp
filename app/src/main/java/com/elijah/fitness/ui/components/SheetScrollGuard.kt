package com.elijah.fitness.ui.components

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Makes a bottom sheet with a scroll view inside close only on a
 * deliberate drag, wherever the drag starts (phase 8d: the New goal and
 * Log a workout sheets).
 *
 * ### Why
 * Material's `BottomSheetBehavior` picks the first nested-scrolling view
 * in the sheet as its scrolling child. A drag that starts on that child
 * moves the sheet by nested scrolling, and when the finger lifts, a
 * `skipCollapsed` sheet hides after any downward drag, however short or
 * slow (`onStopNestedScroll` asks `shouldHide()`, which is always true
 * with `skipCollapsed`). A drag anywhere else goes through the sheet's
 * `ViewDragHelper` instead, which only hides when the sheet is let go
 * low or flung, and otherwise springs back. So a nudge on the summary or
 * the steppers closed the sheet and lost the user's input, while the
 * same nudge on the header did not.
 *
 * ### What it does
 * - **The content fits** (a 6.1" phone): the scroll view has nested
 *   scrolling turned off. The behaviour then finds no scrolling child,
 *   and every drag in the sheet follows the header's rules: let go low
 *   or flung, it closes; a short drag springs back.
 * - **The content scrolls** (a short screen, a large font): nested
 *   scrolling stays on so the content can scroll, and the sheet stops
 *   skipping its collapsed state, with the collapsed height set to the
 *   whole sheet. Collapsed then looks exactly like expanded, and
 *   `shouldHide()` uses its normal rule: hide only when let go past
 *   about half the sheet (a fast flick counts extra). A short drag
 *   springs back here too. The price: on such a screen a quick flick
 *   has to be firmer to close the sheet.
 *
 * The behaviour looks for its scrolling child each time it lays the
 * sheet out, which is when this check runs too, and again when the
 * sheet comes to rest. Nothing switches while the sheet is being
 * dragged or settling.
 */
object SheetScrollGuard {

    fun install(scrollView: NestedScrollView, behavior: BottomSheetBehavior<*>?) {
        // Checked again whenever the sheet comes to rest (see update).
        behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (!isMoving(newState)) update(scrollView, behavior)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        })
        // Off until a layout shows the content doesn't fit.
        scrollView.isNestedScrollingEnabled = false
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            update(scrollView, behavior)
        }
        scrollView.addOnLayoutChangeListener(listener)
        scrollView.getChildAt(0)?.addOnLayoutChangeListener(listener)
    }

    private fun update(scrollView: NestedScrollView, behavior: BottomSheetBehavior<*>?) {
        val content = scrollView.getChildAt(0) ?: return
        val viewport = scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
        if (viewport <= 0) return
        val scrolls = content.height > viewport

        if (behavior != null) {
            // Never switch mid-gesture. A sheet that reaches the top of the
            // screen gets the status bar's height as padding and loses it
            // again once dragged, so content that only just scrolls "fits"
            // during the drag. Switching then would cut the drag off.
            if (isMoving(behavior.state)) return
            val sheetHeight = sheetOf(scrollView, behavior)?.height ?: 0
            if (scrolls && sheetHeight > 0) {
                behavior.skipCollapsed = false
                if (behavior.peekHeight != sheetHeight) behavior.peekHeight = sheetHeight
            } else if (!scrolls) {
                behavior.skipCollapsed = true
            }
        }

        if (scrollView.isNestedScrollingEnabled == scrolls) return
        scrollView.isNestedScrollingEnabled = scrolls
        // Normally this runs inside the sheet's own layout, before the
        // behaviour looks for its scrolling child. If it ran in a later
        // pass, one more layout makes sure the behaviour sees the change.
        scrollView.post { scrollView.requestLayout() }
    }

    private fun isMoving(state: Int): Boolean =
        state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING

    /** The sheet view [behavior] moves: the ancestor of [view] that carries it. */
    private fun sheetOf(view: View, behavior: BottomSheetBehavior<*>): View? {
        var current: View? = view
        while (current != null) {
            val params = current.layoutParams as? CoordinatorLayout.LayoutParams
            if (params?.behavior === behavior) return current
            current = current.parent as? View
        }
        return null
    }
}
