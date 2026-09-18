package com.elijah.fitness.ui.auth.onboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.elijah.fitness.R

/**
 * Custom [View] used by the onboarding host as a shared page indicator.
 * The active page shows as a short lime pill; the others as small dots
 * in `onboarding_dot_inactive`.
 *
 * Sizing is driven by the three onboarding dimens (`page_indicator_dot_size`,
 * `page_indicator_active_width`, `page_indicator_gap`) so tweaking the
 * design values doesn't require touching Kotlin.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dotSize = resources.getDimensionPixelSize(R.dimen.page_indicator_dot_size).toFloat()
    private val activeWidth = resources.getDimensionPixelSize(R.dimen.page_indicator_active_width).toFloat()
    private val gap = resources.getDimensionPixelSize(R.dimen.page_indicator_gap).toFloat()

    private val activeColor = ContextCompat.getColor(context, R.color.lime_accent)
    private val inactiveColor = ContextCompat.getColor(context, R.color.onboarding_dot_inactive)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var count: Int = 3
        set(value) {
            field = value.coerceAtLeast(0)
            requestLayout()
            invalidate()
        }

    var activeIndex: Int = 0
        set(value) {
            field = if (count == 0) 0 else value.coerceIn(0, count - 1)
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (count == 0) 0f else {
            (count - 1) * dotSize + activeWidth + (count - 1) * gap
        }
        val width = resolveSize(desiredWidth.toInt(), widthMeasureSpec)
        val height = resolveSize(dotSize.toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        var x = 0f
        for (i in 0 until count) {
            if (i == activeIndex) {
                paint.color = activeColor
                rect.set(x, cy - dotSize / 2f, x + activeWidth, cy + dotSize / 2f)
                canvas.drawRoundRect(rect, dotSize / 2f, dotSize / 2f, paint)
                x += activeWidth + gap
            } else {
                paint.color = inactiveColor
                canvas.drawCircle(x + dotSize / 2f, cy, dotSize / 2f, paint)
                x += dotSize + gap
            }
        }
    }
}
