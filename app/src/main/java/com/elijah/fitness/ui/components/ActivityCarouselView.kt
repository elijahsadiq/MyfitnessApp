package com.elijah.fitness.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.elijah.fitness.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Swipeable activity cards (phase 8d): a [ViewPager2] that shows one
 * card in the middle with its neighbours peeking in on both sides. Used
 * by the Log a workout sheet (big cards) and the New goal sheet (smaller
 * cards); [ActivityCardSpec] holds the difference.
 *
 * ### How the peek works
 * - The pager's inner RecyclerView gets side padding of
 *   `(width - card - gap) / 2`, worked out from this view's width in
 *   [onMeasure], so the selected card is always centred: 63dp a side on
 *   a 390dp phone with the 250dp log-sheet card and 14dp gap, which puts
 *   the card's edge 70dp in from the screen edge (63 + half the gap, the
 *   design's 70px offset). Less on a 360dp phone, never negative. The
 *   cards keep their size.
 * - `clipToPadding = false` lets the neighbours draw into that padding.
 * - Each page is card + gap wide; the card sits centred in it, which is
 *   what puts the gap between two cards.
 * - [PeekPageTransformer] shrinks and fades the cards as they move away
 *   from the centre (0.9 scale, 0.7 alpha one page out).
 *
 * Tapping a side card moves to it. Selection changes are reported
 * through [OnActivitySelectedListener]; when the user caused them,
 * TalkBack hears "Activity, Swimming, 3 of 5".
 */
class ActivityCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Called once per newly selected card. */
    fun interface OnActivitySelectedListener {
        /**
         * @param fromUser true for a swipe, a tap on a card or
         *   [showActivity]; false for [setUp] and [jumpTo].
         */
        fun onActivitySelected(position: Int, fromUser: Boolean)
    }

    private val pager = ViewPager2(context)
    private val recycler: RecyclerView? = pager.getChildAt(0) as? RecyclerView

    private var adapter: ActivityCardAdapter? = null
    private var listener: OnActivitySelectedListener? = null

    private var cardWidthPx = 0
    private var cardHeightPx = 0
    private var cardGapPx = 0

    /** Set while the code (not the user) moves the pager. */
    private var silentSelection = false
    private var lastSelected = RecyclerView.NO_POSITION

    /** The selected card's index (0 before [setUp]). */
    val currentIndex: Int get() = pager.currentItem

    val itemCount: Int get() = adapter?.itemCount ?: 0

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val current = adapter ?: return
            if (position !in 0 until current.itemCount) return
            // Repaint the two cards after this pass: RecyclerView must not
            // be told about item changes from inside its own scroll callback.
            pager.post {
                if (adapter === current) current.setSelectedPosition(position)
            }
            if (position == lastSelected) return
            lastSelected = position
            val fromUser = !silentSelection
            listener?.onActivitySelected(position, fromUser)
            if (fromUser) announceSelection(position)
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        resolveSpec(ActivityCardSpec.LOG_SHEET)

        pager.offscreenPageLimit = 1
        pager.clipChildren = false
        pager.clipToPadding = false
        recycler?.apply {
            clipToPadding = false
            clipChildren = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // The pager scrolls sideways on its own. Left on, its nested
            // (horizontal) scroll start on touch-down makes a bottom sheet's
            // CoordinatorLayout drop the sheet's own vertical nested scroll,
            // so a drag down that starts on a card wouldn't close the sheet.
            isNestedScrollingEnabled = false
        }
        pager.setPageTransformer(PeekPageTransformer())
        pager.registerOnPageChangeCallback(pageCallback)
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Fills the carousel with [items] and selects [selected] without
     * animating or announcing it. Call once per view.
     */
    fun setUp(
        spec: ActivityCardSpec,
        items: List<ActivityCardItem>,
        selected: Int,
        listener: OnActivitySelectedListener,
    ) {
        resolveSpec(spec)
        val start = if (items.isEmpty()) 0 else selected.coerceIn(0, items.lastIndex)
        val newAdapter = ActivityCardAdapter(spec) { position -> onCardTapped(position) }
        newAdapter.submit(items, start)
        adapter = newAdapter
        this.listener = listener
        lastSelected = RecyclerView.NO_POSITION
        pager.adapter = newAdapter
        jumpTo(start)
        requestLayout()
    }

    /** Moves to [index] as a user action: smooth scroll + TalkBack announcement. */
    fun showActivity(index: Int, smooth: Boolean = true) {
        val count = itemCount
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        if (target != pager.currentItem) pager.setCurrentItem(target, smooth)
    }

    /** Moves to [index] at once, without announcing it (restoring a selection). */
    fun jumpTo(index: Int) {
        val count = itemCount
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        silentSelection = true
        try {
            if (target != pager.currentItem) {
                pager.setCurrentItem(target, false)
            } else if (lastSelected != target) {
                // Already on it (index 0 straight after setUp): ViewPager2
                // won't call back, so report it ourselves.
                pageCallback.onPageSelected(target)
            }
        } finally {
            silentSelection = false
        }
        pager.post { pager.requestTransform() }
    }

    fun showPrevious() = showActivity(currentIndex - 1)

    fun showNext() = showActivity(currentIndex + 1)

    private fun onCardTapped(position: Int) {
        if (position != pager.currentItem) showActivity(position)
    }

    private fun resolveSpec(spec: ActivityCardSpec) {
        cardWidthPx = resources.getDimensionPixelSize(spec.cardWidth)
        cardHeightPx = resources.getDimensionPixelSize(spec.cardHeight)
        cardGapPx = resources.getDimensionPixelSize(spec.cardGap)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        updatePeekPadding(width - paddingLeft - paddingRight)
        val height = cardHeightPx + paddingTop + paddingBottom
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    /** Side padding that centres one card + gap; 0 when the view is narrower. */
    private fun updatePeekPadding(contentWidth: Int) {
        val rv = recycler ?: return
        val side = max(0, (contentWidth - cardWidthPx - cardGapPx) / 2)
        if (rv.paddingLeft != side || rv.paddingRight != side) {
            rv.setPadding(side, 0, side, 0)
        }
    }

    private fun announceSelection(position: Int) {
        val item = adapter?.items?.getOrNull(position) ?: return
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return
        if (!manager.isEnabled) return
        val message = context.getString(
            R.string.activity_carousel_announcement,
            context.getString(item.accessibilityNameRes),
            position + 1,
            itemCount,
        )
        // No visible text changes when the card changes, so a live region
        // has nothing to read; an announcement is the only way to say it.
        @Suppress("DEPRECATION")
        announceForAccessibility(message)
    }

    /**
     * Shrinks and fades the pages beside the centre: full size at the
     * centre, [MIN_SCALE] and [MIN_ALPHA] one page away, in between while
     * a page is moving.
     */
    private class PeekPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val distance = min(1f, abs(position))
            val scale = 1f - (1f - MIN_SCALE) * distance
            page.scaleX = scale
            page.scaleY = scale
            page.alpha = 1f - (1f - MIN_ALPHA) * distance
        }
    }

    private companion object {
        const val MIN_SCALE = 0.9f
        const val MIN_ALPHA = 0.7f
    }
}
