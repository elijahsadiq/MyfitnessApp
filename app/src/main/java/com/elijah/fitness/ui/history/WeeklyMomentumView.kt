package com.elijah.fitness.ui.history

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
 * Custom [View] used inside the History "Weekly momentum" dark card.
 * Draws seven vertical bars (Mon..Sun) sized by daily distance and
 * highlights the current day label.
 *
 * Bar values are normalised — the tallest bar reaches 100 % of the
 * bar-track height; empty days show only the track. Sizing constants
 * pulled from resources so tweaks live in dimens.xml.
 */
class WeeklyMomentumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt() // white 20% — matches the design's translucent track
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lime_accent)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt() // white 70%
        textAlign = Paint.Align.CENTER
        textSize = spToPx(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val activeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lime_accent)
        textAlign = Paint.Align.CENTER
        textSize = spToPx(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private var normalisedValues: FloatArray = FloatArray(7)  // 0..1
    private var activeIndex: Int = -1
    private val labels = arrayOf("M", "T", "W", "T", "F", "S", "S")
    private val cornerRadius = dpToPx(6f)
    private val labelTopMargin = dpToPx(6f)

    /**
     * @param dailyValues 7 raw values in Monday..Sunday order (e.g. km).
     * @param activeIndex 0..6 for the current day, or -1 for none.
     */
    fun setData(dailyValues: DoubleArray, activeIndex: Int) {
        require(dailyValues.size == 7) { "Expected 7 daily values; got ${dailyValues.size}" }
        val max = dailyValues.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        normalisedValues = FloatArray(7) { i -> (dailyValues[i] / max).toFloat() }
        this.activeIndex = activeIndex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellCount = 7
        val gapPx = dpToPx(8f)
        val totalGap = gapPx * (cellCount - 1)
        val cellWidth = (width - totalGap) / cellCount.toFloat()
        val barTrackHeight = height - labelTopMargin - spToPx(14f)  // reserve label row

        for (i in 0 until cellCount) {
            val left = i * (cellWidth + gapPx)
            val right = left + cellWidth
            // Track (dim rounded rect covering full available height)
            val trackRect = RectF(left, 0f, right, barTrackHeight)
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

            // Bar fill (from bottom, height proportional to value)
            val fillHeight = barTrackHeight * normalisedValues[i]
            if (fillHeight > 0f) {
                val fillRect = RectF(left, barTrackHeight - fillHeight, right, barTrackHeight)
                canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, barPaint)
            }

            // Day label
            val labelY = barTrackHeight + labelTopMargin + spToPx(11f)
            val paint = if (i == activeIndex) activeLabelPaint else labelPaint
            canvas.drawText(labels[i], (left + right) / 2f, labelY, paint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}
