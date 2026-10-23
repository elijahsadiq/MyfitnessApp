package com.elijah.fitness.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import com.elijah.fitness.R
import com.elijah.fitness.util.Fonts

/**
 * Pill-shaped single-choice control (phase 8d): a rounded track with a
 * 1dp divider border holding 2–3 equal pill segments with a gap between
 * them. The selected segment is `surface_dark` with white text; the
 * others are transparent with dark text, Bold, 14sp or 15sp.
 *
 * Material's `MaterialButtonToggleGroup` squares the inner corners of
 * joined buttons and has no gap, so it can't draw separate pills — this
 * is a plain [LinearLayout] of [TextView]s with its own exclusivity.
 *
 * ### Touch targets
 * Each segment view is as tall as the whole track and as wide as its
 * share of it; the visible pill is drawn inset inside it. So the touch
 * area is ≥ 48dp tall (40dp pill + padding + border on the log sheet)
 * and there's no dead space between segments.
 *
 * ### Equal pills
 * The outer segments carry the track padding and border as inset, the
 * inner ones only half a gap per side, so equal segment views would draw
 * a wider middle pill. [onMeasure] gives each segment its own insets
 * plus an equal share of the rest, so every visible pill is the same
 * width (the design's `flex: 1 1 0` buttons with a 4px gap).
 *
 * ### TalkBack
 * The track reads as a radio group, each segment as a radio button
 * ("Live with GPS, radio button, checked, 1 of 2").
 *
 * Sizes and the track colour come from the `Widget.Fitness.PillSegmented.*`
 * styles (log sheet, pool length, measure by).
 */
class PillSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Called when the user picks a different segment. */
    fun interface OnSegmentSelectedListener {
        fun onSegmentSelected(index: Int)
    }

    private val segmentHeight: Int
    private val trackPadding: Int
    private val segmentGap: Int
    private val textSizePx: Float
    private val strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_hairline)
    private val labelPadding = resources.getDimensionPixelSize(R.dimen.space_sm)
    private val pillRadius = resources.getDimension(R.dimen.radius_pill)

    private val selectedFill = ContextCompat.getColor(context, R.color.surface_dark)
    private val selectedText = ContextCompat.getColor(context, R.color.text_on_dark)
    private val unselectedText = ContextCompat.getColor(context, R.color.text_on_lime)
    private val rippleColor = ColorStateList.valueOf(
        ContextCompat.getColor(context, R.color.surface_container_high),
    )

    private val segments = mutableListOf<TextView>()
    private val pills = mutableListOf<GradientDrawable>()

    /** Each segment's pill inset on its start and end side, in segment order. */
    private val segmentInsets = mutableListOf<Pair<Int, Int>>()
    private var listener: OnSegmentSelectedListener? = null

    /** Index of the selected segment. */
    var selectedIndex: Int = 0
        private set

    val optionCount: Int get() = segments.size

    init {
        orientation = HORIZONTAL
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.PillSegmentedControl,
            defStyleAttr,
            R.style.Widget_Fitness_PillSegmented,
        )
        try {
            segmentHeight = a.getDimensionPixelSize(
                R.styleable.PillSegmentedControl_pillSegmentHeight,
                resources.getDimensionPixelSize(R.dimen.segment_log_height),
            )
            trackPadding = a.getDimensionPixelSize(
                R.styleable.PillSegmentedControl_pillTrackPadding,
                resources.getDimensionPixelSize(R.dimen.segment_log_track_padding),
            )
            segmentGap = a.getDimensionPixelSize(
                R.styleable.PillSegmentedControl_pillSegmentGap,
                resources.getDimensionPixelSize(R.dimen.segment_log_gap),
            )
            textSizePx = a.getDimension(
                R.styleable.PillSegmentedControl_pillTextSize,
                resources.getDimension(R.dimen.segment_text_size),
            )
            val trackColor = a.getColor(
                R.styleable.PillSegmentedControl_pillTrackColor,
                ContextCompat.getColor(context, R.color.surface_white),
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = pillRadius
                setColor(trackColor)
                setStroke(strokeWidth, ContextCompat.getColor(context, R.color.divider))
            }
        } finally {
            a.recycle()
        }

        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioGroup::class.java.name
                info.setCollectionInfo(
                    CollectionInfoCompat.obtain(
                        1,
                        segments.size,
                        false,
                        CollectionInfoCompat.SELECTION_MODE_SINGLE,
                    ),
                )
            }
        })

        if (isInEditMode) setOptions(listOf("Option", "Option"))
    }

    fun setOnSegmentSelectedListener(listener: OnSegmentSelectedListener?) {
        this.listener = listener
    }

    /** Replaces the segments with [labels] and selects [selected] (no callback). */
    fun setOptions(labels: List<CharSequence>, selected: Int = 0) {
        removeAllViews()
        segments.clear()
        pills.clear()
        segmentInsets.clear()
        labels.forEachIndexed { index, label ->
            val segment = createSegment(index, labels.size, label)
            addView(segment)
            segments += segment
        }
        selectedIndex = if (labels.isEmpty()) 0 else selected.coerceIn(0, labels.lastIndex)
        renderSelection()
    }

    /** Selects [index] from code. [notify] also calls the listener. */
    fun setSelectedIndex(index: Int, notify: Boolean = false) {
        if (index !in segments.indices || index == selectedIndex) return
        selectedIndex = index
        renderSelection()
        if (notify) listener?.onSegmentSelected(index)
    }

    private fun createSegment(index: Int, total: Int, label: CharSequence): TextView {
        // The pill is inset so the gap / track padding fall inside the
        // (touchable) segment view: two neighbours' insets add up to the gap.
        val edge = trackPadding + strokeWidth
        val insetStart = if (index == 0) edge else segmentGap - segmentGap / 2
        val insetEnd = if (index == total - 1) edge else segmentGap / 2

        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillRadius
            setColor(Color.TRANSPARENT)
        }
        pills += pill
        segmentInsets += insetStart to insetEnd

        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            minHeight = segmentHeight + 2 * edge
            background = segmentBackground(pill, insetStart, insetEnd)
            setPaddingRelative(
                insetStart + labelPadding,
                edge + strokeWidth,
                insetEnd + labelPadding,
                edge + strokeWidth,
            )
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Fonts.bold(context)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            text = label
            isClickable = true
            isFocusable = true
            setOnClickListener { setSelectedIndex(index, notify = true) }
            ViewCompat.setAccessibilityDelegate(this, SegmentAccessibility(index))
        }
    }

    /**
     * The segment's pill (with its ripple), inset inside the segment view.
     * [InsetDrawable] only takes left / right, so the start / end insets
     * swap sides in a right-to-left layout, where the first segment sits
     * on the right.
     */
    private fun segmentBackground(pill: GradientDrawable, insetStart: Int, insetEnd: Int): RippleDrawable {
        val edge = trackPadding + strokeWidth
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val left = if (rtl) insetEnd else insetStart
        val right = if (rtl) insetStart else insetEnd
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillRadius
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            rippleColor,
            InsetDrawable(pill, left, edge, right, edge),
            InsetDrawable(mask, left, edge, right, edge),
        )
    }

    /**
     * With a fixed width, each segment is its own start + end inset plus
     * an equal share of what's left: `LinearLayout` adds a weighted
     * child's share to its base width, so the visible pills come out
     * equal (to the pixel). With a wrapping width the segments size to
     * their labels, as before.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fixedWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
        segments.forEachIndexed { index, segment ->
            val lp = segment.layoutParams as? LayoutParams ?: return@forEachIndexed
            val (insetStart, insetEnd) = segmentInsets.getOrNull(index) ?: return@forEachIndexed
            lp.width = if (fixedWidth) insetStart + insetEnd else 0
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The layout direction is only known once attached: redo the pill insets then. */
    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        // Can run from the View constructor, before this class's fields exist.
        @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
        if (segments == null || pills == null || segmentInsets == null) return
        segments.forEachIndexed { index, segment ->
            val pill = pills.getOrNull(index) ?: return@forEachIndexed
            val (insetStart, insetEnd) = segmentInsets.getOrNull(index) ?: return@forEachIndexed
            segment.background = segmentBackground(pill, insetStart, insetEnd)
        }
    }

    private fun renderSelection() {
        segments.forEachIndexed { index, segment ->
            val on = index == selectedIndex
            pills.getOrNull(index)?.setColor(if (on) selectedFill else Color.TRANSPARENT)
            segment.setTextColor(if (on) selectedText else unselectedText)
            segment.isSelected = on
        }
    }

    /** A segment reads as one radio button of the group. */
    private inner class SegmentAccessibility(private val index: Int) : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val checked = index == selectedIndex
            info.className = RadioButton::class.java.name
            info.isCheckable = true
            info.isChecked = checked
            info.setCollectionItemInfo(
                CollectionItemInfoCompat.obtain(0, 1, index, 1, false, checked),
            )
        }
    }
}
