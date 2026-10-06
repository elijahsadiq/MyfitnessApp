package com.elijah.fitness.ui.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import com.elijah.fitness.analytics.BarPoint

/**
 * Minimal custom chart. Draws a row of bars (one per [BarPoint]) with the
 * tallest bar at the lime accent and the rest in a muted surface tone.
 * Pure `Canvas` work — no third-party chart dependency, no API key,
 * and a readable `onDraw` that the written report can cite as the
 * worked example for Task 1b.
 *
 * Not a general-purpose chart library — specifically shaped around the
 * Progress tab's two use cases: activity bars (7/5/12 bars) and trend
 * bars (≤ 12 bars).
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var bars: List<BarPoint> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_container_high)
        style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lime_accent)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = context.resources.getDimension(R.dimen.chart_label_size)
    }

    private val rect = RectF()

    fun setData(points: List<BarPoint>) {
        bars = points
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hDefault = resources.getDimensionPixelSize(R.dimen.chart_default_height)
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> hDefault
        }
        setMeasuredDimension(wSize, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val labelAreaPx = labelPaint.textSize * 1.6f
        val chartTop = paddingTop.toFloat()
        val chartBottom = height - paddingBottom.toFloat() - labelAreaPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val chartLeft = paddingLeft.toFloat()
        val chartRight = width - paddingRight.toFloat()
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)

        val gap = (chartWidth / bars.size) * 0.25f
        val barSlot = chartWidth / bars.size
        val barWidth = (barSlot - gap).coerceAtLeast(1f)

        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1.0)
        val activeIndex = bars
            .withIndex()
            .filter { it.value.value > 0.0 }
            .maxByOrNull { it.value.value }
            ?.index ?: -1

        for ((index, point) in bars.withIndex()) {
            val fraction = (point.value / maxValue).toFloat().coerceIn(0f, 1f)
            val barHeight = chartHeight * fraction
            val left = chartLeft + barSlot * index + gap / 2f
            val top = chartBottom - barHeight
            rect.set(left, top, left + barWidth, chartBottom)
            val paint = if (index == activeIndex) activePaint else barPaint
            val corner = resources.getDimension(R.dimen.chart_bar_corner)
            canvas.drawRoundRect(rect, corner, corner, paint)

            val labelY = chartBottom + labelPaint.textSize * 1.2f
            canvas.drawText(point.label, left + barWidth / 2f, labelY, labelPaint)
        }
    }
}
