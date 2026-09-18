package com.elijah.fitness.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.elijah.fitness.R

/**
 * Circular progress ring used in the Home "This week" hero card.
 * A dim track (`#2A2E28` per the design) plus a lime arc that starts at
 * 12 o'clock and sweeps clockwise proportional to [progress] (0f..1f).
 */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val strokePx = dpToPx(4.5f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = 0xFF2A2E28.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.lime_accent)
    }
    private val rect = RectF()

    /** 0f..1f. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokePx / 2f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(rect, -90f, 360f * progress, false, ringPaint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
