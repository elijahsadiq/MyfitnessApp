package com.elijah.fitness.ui.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import com.elijah.fitness.analytics.BarPoint
import kotlin.math.min

/**
 * Minimal custom chart. Pure `Canvas` work — no third-party chart
 * dependency, no API key, and a readable `onDraw` that the written
 * report can cite as the worked example for Task 1b.
 *
 * ### Visual layout
 * ```
 *     42  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─    ← faint max-value guide
 *      ▁    ▁
 *      █    █     ▁        ▁                  ← rounded-top bars, max in lime
 *      █    █     █        █     ▁    ▁    █
 *     ─────────────────────────────────────   ← solid baseline
 *      M    T     W        T     F    S    S  ← per-bar labels
 * ```
 *
 * Design decisions worth noting in the report:
 * - **Bars are capped at [MAX_BAR_WIDTH_DP]** so a chart with a single
 *   data point doesn't render a slab filling the whole viewport.
 * - **Only the top corners are rounded** — bars grow out of a solid
 *   baseline, which the eye reads as "columns on a ground line".
 * - **Values draw on top of each bar** so the chart is informative even
 *   without an explicit y-axis label — important when the chart feeds
 *   different units (minutes / km/h / kg) on the same tab.
 * - **The maximum bar highlights in lime**, every other bar in a muted
 *   `surface_container_high`, matching the design-system contrast rule.
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
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1f
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(
                context.resources.displayMetrics.density * 4f,
                context.resources.displayMetrics.density * 4f,
            ), 0f,
        )
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = context.resources.getDimension(R.dimen.chart_label_size)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textAlign = Paint.Align.CENTER
        textSize = context.resources.getDimension(R.dimen.chart_value_size)
        isFakeBoldText = true
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = context.resources.getDimension(R.dimen.chart_label_size)
    }

    private val maxBarWidthPx = MAX_BAR_WIDTH_DP * context.resources.displayMetrics.density
    private val minBarWidthPx = MIN_BAR_WIDTH_DP * context.resources.displayMetrics.density
    private val cornerPx = context.resources.getDimension(R.dimen.chart_bar_corner)
    private val valueMarginPx = context.resources.displayMetrics.density * 4f

    private val rect = RectF()
    private val roundedPath = Path()

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
        if (bars.isEmpty()) {
            drawEmptyMessage(canvas)
            return
        }

        val labelAreaPx = labelPaint.textSize * 1.6f
        val valueAreaPx = valuePaint.textSize * 1.8f // room for the per-bar number
        val chartTop = paddingTop.toFloat() + valueAreaPx
        val chartBottom = height - paddingBottom.toFloat() - labelAreaPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val chartLeft = paddingLeft.toFloat()
        val chartRight = width - paddingRight.toFloat()
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)

        val barCount = bars.size
        val barSlot = chartWidth / barCount
        // Cap the bar width — a single-bar chart would otherwise stretch
        // its single bar to the full chart width, which reads as a slab.
        val barWidth = min(barSlot * 0.6f, maxBarWidthPx).coerceAtLeast(minBarWidthPx)

        val maxValue = bars.maxOf { it.value }
        if (maxValue <= 0.0) {
            drawEmptyMessage(canvas)
            return
        }

        // Faint dashed grid line at the top of the chart area — gives the
        // user a sense of "scale" without a full axis.
        val gridY = chartTop
        canvas.drawLine(chartLeft, gridY, chartRight, gridY, gridPaint)

        // Solid baseline at the bottom of the chart area.
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, baselinePaint)

        val activeIndex = bars
            .withIndex()
            .filter { it.value.value > 0.0 }
            .maxByOrNull { it.value.value }
            ?.index ?: -1

        for ((index, point) in bars.withIndex()) {
            val fraction = (point.value / maxValue).toFloat().coerceIn(0f, 1f)
            val barHeight = chartHeight * fraction
            val centerX = chartLeft + barSlot * (index + 0.5f)
            val left = centerX - barWidth / 2f
            val right = centerX + barWidth / 2f
            val top = chartBottom - barHeight

            if (point.value > 0.0) {
                rect.set(left, top, right, chartBottom)
                drawTopRoundedBar(canvas, rect, if (index == activeIndex) activePaint else barPaint)

                // Value above the bar — only for non-zero data to keep
                // zero-value days clean.
                val valueLabel = formatValue(point.value)
                val valueY = top - valueMarginPx
                canvas.drawText(valueLabel, centerX, valueY, valuePaint)
            }

            val labelY = chartBottom + labelPaint.textSize * 1.2f
            canvas.drawText(point.label, centerX, labelY, labelPaint)
        }
    }

    /**
     * Draw a rectangle whose TOP two corners are rounded but whose
     * bottom two corners are square — makes bars read as columns growing
     * from a baseline.
     */
    private fun drawTopRoundedBar(canvas: Canvas, bounds: RectF, paint: Paint) {
        val r = min(cornerPx, bounds.height() / 2f)
        roundedPath.rewind()
        roundedPath.moveTo(bounds.left, bounds.bottom)
        roundedPath.lineTo(bounds.left, bounds.top + r)
        roundedPath.quadTo(bounds.left, bounds.top, bounds.left + r, bounds.top)
        roundedPath.lineTo(bounds.right - r, bounds.top)
        roundedPath.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + r)
        roundedPath.lineTo(bounds.right, bounds.bottom)
        roundedPath.close()
        canvas.drawPath(roundedPath, paint)
    }

    private fun drawEmptyMessage(canvas: Canvas) {
        val cy = height / 2f + emptyPaint.textSize / 3f
        canvas.drawText(
            context.getString(R.string.chart_empty_label),
            width / 2f, cy, emptyPaint,
        )
    }

    /**
     * Compact value renderer — chart values span minutes, km/h, kg, and
     * seconds-per-km, so a single-decimal shape keeps the surface readable
     * regardless of unit. Values ≥ 1000 drop into `1.2k` form so the label
     * stays narrow enough to fit above the bar.
     */
    private fun formatValue(value: Double): String = when {
        value >= 1000.0 -> "%.1fk".format(value / 1000.0)
        value >= 10.0 -> value.toInt().toString()
        else -> "%.1f".format(value)
    }

    companion object {
        private const val MAX_BAR_WIDTH_DP = 36f
        private const val MIN_BAR_WIDTH_DP = 10f
    }
}
