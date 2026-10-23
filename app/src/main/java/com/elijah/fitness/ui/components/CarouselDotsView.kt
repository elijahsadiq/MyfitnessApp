package com.elijah.fitness.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.elijah.fitness.R

/**
 * Dots under an [ActivityCarouselView] (phase 8d): the selected one is a
 * 22 x 8dp dark pill, the others 8dp pale dots, 6dp apart. When the
 * selection moves, the old pill shrinks and the new one grows over
 * [WIDTH_ANIMATION_MS] (the designs' `transition: width 0.3s`); the
 * colours switch at once, as in the designs.
 *
 * Purely visual: hidden from TalkBack, which hears the carousel's own
 * "Activity, Swimming, 3 of 5" instead. Mirrors in right-to-left
 * layouts so dot 1 sits under the first card.
 */
class CarouselDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dotSize = resources.getDimension(R.dimen.carousel_dot_size)
    private val activeWidth = resources.getDimension(R.dimen.carousel_dot_active_width)
    private val gap = resources.getDimension(R.dimen.carousel_dot_gap)

    private val activeColor = ContextCompat.getColor(context, R.color.text_on_lime)
    private val inactiveColor = ContextCompat.getColor(context, R.color.dot_inactive)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var selected = 0
    private var previous = 0
    /** 0 → the pill is still on [previous]; 1 → it is fully on [selected]. */
    private var progress = 1f
    private var animator: ValueAnimator? = null

    var count: Int = 0
        set(value) {
            val safe = value.coerceAtLeast(0)
            if (field == safe) return
            field = safe
            selected = selected.coerceIn(0, (safe - 1).coerceAtLeast(0))
            previous = selected
            progress = 1f
            requestLayout()
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Moves the pill to [index], animating the widths unless [animate] is false. */
    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        if (target == selected && progress >= 1f) return
        animator?.cancel()
        previous = selected
        selected = target
        if (!animate || previous == target || !isAttachedToWindow) {
            previous = target
            progress = 1f
            invalidate()
            return
        }
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WIDTH_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        progress = 1f
        previous = selected
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (count == 0) 0f else activeWidth + (count - 1) * (dotSize + gap)
        val width = resolveSize(
            (desiredWidth + paddingLeft + paddingRight).toInt(),
            widthMeasureSpec,
        )
        val height = resolveSize(
            (dotSize + paddingTop + paddingBottom).toInt(),
            heightMeasureSpec,
        )
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count == 0) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val contentWidth = activeWidth + (count - 1) * (dotSize + gap)
        val start = paddingLeft + (width - paddingLeft - paddingRight - contentWidth) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val radius = dotSize / 2f
        var x = 0f
        for (i in 0 until count) {
            val w = when (i) {
                selected -> lerp(dotSize, activeWidth, progress)
                previous -> lerp(activeWidth, dotSize, progress)
                else -> dotSize
            }
            val left = if (rtl) start + contentWidth - x - w else start + x
            paint.color = if (i == selected) activeColor else inactiveColor
            rect.set(left, cy - radius, left + w, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, paint)
            x += w + gap
        }
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private companion object {
        const val WIDTH_ANIMATION_MS = 300L
    }
}
