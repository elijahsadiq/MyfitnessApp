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
 * Circular progress ring. Dim track + lime arc that sweeps clockwise
 * from 12 o'clock proportional to [progress] (0f..1f).
 *
 * Shared by the Home "This week" hero (4.5dp stroke, dark track) and the
 * Goals main-card ring (9dp stroke, `#353A32` track). Stroke width and
 * colours are set via `app:ringStrokeWidth` / `app:ringTrackColor` /
 * `app:ringArcColor` so the same view class renders both.
 */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    /** 0f..1f. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    init {
        val defaultStroke = dpToPx(4.5f)
        val defaultTrack = 0xFF2A2E28.toInt()
        val defaultArc = ContextCompat.getColor(context, R.color.lime_accent)
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ProgressRingView)
            try {
                trackPaint.strokeWidth = ta.getDimension(
                    R.styleable.ProgressRingView_ringStrokeWidth, defaultStroke)
                ringPaint.strokeWidth = trackPaint.strokeWidth
                trackPaint.color = ta.getColor(
                    R.styleable.ProgressRingView_ringTrackColor, defaultTrack)
                ringPaint.color = ta.getColor(
                    R.styleable.ProgressRingView_ringArcColor, defaultArc)
            } finally {
                ta.recycle()
            }
        } else {
            trackPaint.strokeWidth = defaultStroke
            ringPaint.strokeWidth = defaultStroke
            trackPaint.color = defaultTrack
            ringPaint.color = defaultArc
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = trackPaint.strokeWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(rect, -90f, 360f * progress, false, ringPaint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
