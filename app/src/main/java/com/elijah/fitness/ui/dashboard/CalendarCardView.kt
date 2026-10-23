package com.elijah.fitness.ui.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ViewCalendarCardBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Home calendar: a one-week strip that pulls down into a whole month.
 *
 * - Swipe the strip sideways for earlier weeks; swipe the open month for
 *   earlier months. Swiping moves the selected day with it.
 * - Drag down anywhere on the card, or tap the month name or the grab
 *   bar, to open the month; drag up (or tap again) to close it. Picking
 *   a day in the open month closes it on that day's week.
 *
 * The month grid is always laid out at full height inside the body.
 * While the card opens, the body grows from one row to six and the grid
 * slides down, so the selected week moves from the top to its own row
 * and the weeks around it unfold above and below. [progress] runs from
 * 0 (strip) to 1 (month).
 *
 * The view only draws [CalendarUi] and reports picks through [listener];
 * the selected day itself lives in DashboardViewModel.
 */
class CalendarCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        /** The user picked [date] — a tap, a swipe or "Today". */
        fun onDaySelected(date: LocalDate)

        /** The month finished opening (`true`) or closing (`false`). */
        fun onExpandedChanged(expanded: Boolean)
    }

    var listener: Listener? = null

    private val binding = ViewCalendarCardBinding.inflate(LayoutInflater.from(context), this)
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.calendar_row_height)
    private val weekAdapter = CalendarPageAdapter(CalendarPageAdapter.Mode.WEEK, ::onDayTapped)
    private val monthAdapter = CalendarPageAdapter(CalendarPageAdapter.Mode.MONTH, ::onDayTapped)

    private var ui: CalendarUi? = null
    private var progress = 0f
    private var reportedExpanded = false
    private var animator: ValueAnimator? = null

    /** True while the pagers are moved to match [ui], so their callbacks don't echo back. */
    private var syncingPagers = false

    // Vertical drag (open / close). Sideways drags belong to the pagers.
    private enum class Gesture { NONE, UNDECIDED, DRAG, OTHER }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingVelocity = FLING_DP_PER_SECOND * resources.displayMetrics.density
    private var velocityTracker: VelocityTracker? = null
    private var gesture = Gesture.NONE
    private var downX = 0f
    private var downY = 0f
    private var dragStartY = 0f
    private var dragStartProgress = 0f

    init {
        orientation = VERTICAL
        buildWeekdayRow()
        binding.monthPager.updateLayoutParams { height = rowHeight * CalendarMath.GRID_ROWS }
        setUpPager(binding.weekPager, weekAdapter, ::onWeekSwiped)
        setUpPager(binding.monthPager, monthAdapter, ::onMonthSwiped)
        binding.monthToggle.setOnClickListener { toggle() }
        binding.calendarHandle.setOnClickListener { toggle() }
        binding.todayButton.setOnClickListener { ui?.let { select(it.today) } }
    }

    // ---------- public API ----------

    fun setUi(next: CalendarUi) {
        ui = next
        weekAdapter.ui = next
        monthAdapter.ui = next
        syncPagers(next)
        renderHeader()
        // The selected week's row may have moved in the month grid.
        if (progress < 1f) applyProgress(progress)
    }

    /** Open or close the month, e.g. to restore it after a rotation. */
    fun setExpanded(expanded: Boolean, animate: Boolean) {
        val target = if (expanded) 1f else 0f
        if (animate) {
            animateTo(target)
        } else {
            animator?.cancel()
            applyProgress(target)
        }
    }

    // ---------- selection ----------

    private fun onDayTapped(date: LocalDate) {
        val current = ui ?: return
        if (!CalendarMath.isSelectable(date, current.today)) return
        select(date)
        // A day picked in the month: fold back onto its week.
        if (progress > 0f) animateTo(0f)
    }

    private fun onWeekSwiped(page: Int) {
        val current = ui ?: return
        val monday = CalendarMath.weekForPage(page, current.today)
        if (monday == CalendarMath.weekStart(current.selected)) return
        select(CalendarMath.selectionForWeek(monday, current.selected, current.today))
    }

    private fun onMonthSwiped(page: Int) {
        val current = ui ?: return
        val month = CalendarMath.monthForPage(page, current.today)
        if (month == YearMonth.from(current.selected)) return
        select(CalendarMath.selectionForMonth(month, current.selected, current.today))
    }

    /** Show the pick straight away; the ViewModel's copy follows. */
    private fun select(date: LocalDate) {
        val current = ui ?: return
        if (date != current.selected) setUi(current.copy(selected = date))
        listener?.onDaySelected(date)
    }

    private fun syncPagers(state: CalendarUi) {
        val weekPage = CalendarMath.pageForWeek(state.selected, state.today)
        val monthPage = CalendarMath.pageForMonth(YearMonth.from(state.selected), state.today)
        syncingPagers = true
        // Already there (e.g. mid-swipe): moving again would cut the swipe short.
        if (binding.weekPager.currentItem != weekPage) binding.weekPager.setCurrentItem(weekPage, false)
        if (binding.monthPager.currentItem != monthPage) binding.monthPager.setCurrentItem(monthPage, false)
        syncingPagers = false
    }

    private fun setUpPager(pager: ViewPager2, adapter: CalendarPageAdapter, onSwiped: (Int) -> Unit) {
        pager.adapter = adapter
        // Restyling the days on every pick shouldn't cross-fade.
        (pager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Our own syncPagers move, not a swipe (checked now, while
                // the flag is still set).
                if (syncingPagers) return
                // ViewPager2 calls this from inside its RecyclerView's
                // scroll callback, where the pages' adapter must not be
                // updated (RecyclerView logs an IllegalStateException).
                // Handle the swipe on the next frame instead.
                pager.post { onSwiped(position) }
            }
        })
    }

    // ---------- header ----------

    private fun buildWeekdayRow() {
        DayOfWeek.entries.forEach { day ->
            val letter = TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_Fitness_LabelSm)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                gravity = Gravity.CENTER
                isAllCaps = false
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            }
            binding.weekdayRow.addView(letter, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun renderHeader() {
        val current = ui ?: return
        val expanded = progress >= HALF
        val month = CalendarMath.formatMonth(YearMonth.from(current.selected), Locale.getDefault())
        binding.monthToggle.text = month
        binding.monthToggle.setIconResource(
            if (expanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24
        )
        binding.monthToggle.contentDescription = context.getString(
            if (expanded) R.string.home_calendar_show_week else R.string.home_calendar_show_month,
            month,
        )
        binding.todayButton.isVisible = current.selected != current.today
    }

    // ---------- open / close ----------

    private fun toggle() = animateTo(if (progress >= HALF) 0f else 1f)

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (progress == target) {
            applyProgress(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (MAX_ANIMATION_MS * abs(target - progress)).toLong()
                .coerceAtLeast(MIN_ANIMATION_MS)
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        val wasExpanded = progress >= HALF
        progress = value
        val anchorRow = ui?.let { CalendarMath.rowInMonth(it.selected) } ?: 0
        val extraRows = (CalendarMath.GRID_ROWS - 1) * rowHeight * value
        binding.calendarBody.updateLayoutParams { height = rowHeight + extraRows.roundToInt() }
        binding.monthPager.translationY = -anchorRow * rowHeight * (1f - value)
        binding.monthPager.isInvisible = value == 0f
        binding.weekPager.isInvisible = value > 0f

        val alpha = 1f + (CalendarPageViewHolder.MUTED_ALPHA - 1f) * value
        monthAdapter.outsideAlpha = alpha
        (binding.monthPager.getChildAt(0) as? RecyclerView)?.let { pages ->
            pages.children.forEach { child ->
                (pages.getChildViewHolder(child) as? CalendarPageViewHolder)?.applyOutsideAlpha(alpha)
            }
        }

        if ((progress >= HALF) != wasExpanded) renderHeader()
        if (value == 0f || value == 1f) {
            val expanded = value == 1f
            if (expanded != reportedExpanded) {
                reportedExpanded = expanded
                listener?.onExpandedChanged(expanded)
            }
        }
    }

    override fun onDetachedFromWindow() {
        // Land on the end state, so the ViewModel is told where it settled.
        animator?.end()
        super.onDetachedFromWindow()
    }

    // ---------- touch ----------

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = ev.rawX
            downY = ev.rawY
            gesture = Gesture.UNDECIDED
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
            // Until the direction is known, keep the page from scrolling.
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        velocityTracker?.addMovement(ev)
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            gesture = Gesture.NONE
            velocityTracker?.recycle()
            velocityTracker = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        ev.actionMasked == MotionEvent.ACTION_MOVE && decide(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            // Keep the gesture even when it starts on the letters or gaps.
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE ->
                if (gesture == Gesture.DRAG || decide(ev)) dragTo(ev.rawY)
            MotionEvent.ACTION_UP -> if (gesture == Gesture.DRAG) settle(fling = true) else performClick()
            MotionEvent.ACTION_CANCEL -> if (gesture == Gesture.DRAG) settle(fling = false)
        }
        return true
    }

    // No click action of its own; overridden so a tap still reaches accessibility.
    override fun performClick(): Boolean = super.performClick()

    /**
     * Decide once per gesture who owns it: a vertical drag that can open
     * or close the month is ours; a sideways one is the pager's; any
     * other vertical drag goes back to the page so it scrolls.
     */
    private fun decide(ev: MotionEvent): Boolean {
        if (gesture != Gesture.UNDECIDED) return gesture == Gesture.DRAG
        val dx = ev.rawX - downX
        val dy = ev.rawY - downY
        if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
            gesture = Gesture.OTHER
        } else if (abs(dy) > touchSlop) {
            val canMove = if (dy > 0) progress < 1f else progress > 0f
            if (canMove) {
                gesture = Gesture.DRAG
                animator?.cancel()
                dragStartY = ev.rawY
                dragStartProgress = progress
            } else {
                gesture = Gesture.OTHER
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return gesture == Gesture.DRAG
    }

    private fun dragTo(rawY: Float) {
        val range = (CalendarMath.GRID_ROWS - 1) * rowHeight.toFloat()
        applyProgress((dragStartProgress + (rawY - dragStartY) / range).coerceIn(0f, 1f))
    }

    private fun settle(fling: Boolean) {
        val velocity = velocityTracker?.takeIf { fling }?.let {
            it.computeCurrentVelocity(MILLIS_PER_SECOND)
            it.yVelocity
        } ?: 0f
        animateTo(
            when {
                velocity > flingVelocity -> 1f
                velocity < -flingVelocity -> 0f
                progress >= HALF -> 1f
                else -> 0f
            }
        )
    }

    private companion object {
        const val HALF = 0.5f
        const val MAX_ANIMATION_MS = 280L
        const val MIN_ANIMATION_MS = 120L
        const val FLING_DP_PER_SECOND = 500f
        const val MILLIS_PER_SECOND = 1000
    }
}
