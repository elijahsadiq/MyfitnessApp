package com.elijah.fitness.util

import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.ColorInt

/**
 * Builds "4.12 km"-style metric text: the number at full size, the unit
 * smaller, muted and in a lighter weight. Doing it in ONE TextView
 * (instead of a number view plus a unit view side by side) lets
 * auto-size shrink the pair together on narrow phones or large font
 * settings, so neither part gets clipped.
 */
object MetricText {

    fun of(
        value: CharSequence,
        unit: CharSequence?,
        @ColorInt unitColor: Int,
        unitScale: Float = 0.5f,
        unitTypeface: Typeface? = null,
    ): CharSequence {
        if (unit.isNullOrEmpty()) return value
        val out = SpannableStringBuilder(value).append(UNIT_GAP)
        appendUnit(out, unit, unitColor, unitScale, unitTypeface)
        return out
    }

    /**
     * A duration in the same style (phase 8d): "3 h 8 min" with both
     * numbers at full size and "h" / "min" small. Under an hour the
     * result is exactly [of] ("45 min"). [trailing] is appended small
     * after the duration, e.g. "/ 2 h 30 min" on a goal card.
     */
    fun duration(
        totalMinutes: Long,
        hoursUnit: CharSequence,
        minutesUnit: CharSequence,
        @ColorInt unitColor: Int,
        unitScale: Float = 0.5f,
        unitTypeface: Typeface? = null,
        trailing: CharSequence? = null,
    ): CharSequence {
        val (hours, minutes) = DurationFormatter.split(totalMinutes)
        val out = SpannableStringBuilder()
        if (hours > 0L) {
            out.append(hours.toString()).append(UNIT_GAP)
            appendUnit(out, hoursUnit, unitColor, unitScale, unitTypeface)
            if (minutes > 0L) {
                // A full-size space keeps "3 h" and "8 min" visibly apart.
                out.append(SEGMENT_GAP)
                out.append(minutes.toString()).append(UNIT_GAP)
                appendUnit(out, minutesUnit, unitColor, unitScale, unitTypeface)
            }
        } else {
            out.append(minutes.toString()).append(UNIT_GAP)
            appendUnit(out, minutesUnit, unitColor, unitScale, unitTypeface)
        }
        if (!trailing.isNullOrEmpty()) {
            out.append(UNIT_GAP)
            appendUnit(out, trailing, unitColor, unitScale, unitTypeface)
        }
        return out
    }

    private fun appendUnit(
        out: SpannableStringBuilder,
        unit: CharSequence,
        @ColorInt unitColor: Int,
        unitScale: Float,
        unitTypeface: Typeface?,
    ) {
        val start = out.length
        out.append(unit)
        val end = out.length
        out.setSpan(RelativeSizeSpan(unitScale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(ForegroundColorSpan(unitColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (unitTypeface != null) {
            out.setSpan(TypefaceSpanCompat(unitTypeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Thin space keeps the unit visually attached to its number. */
    private const val UNIT_GAP = " "

    /** Between "3 h" and "8 min". */
    private const val SEGMENT_GAP = " "

    /**
     * `TypefaceSpan(Typeface)` only exists from Android 9; this does the
     * same on Android 8.1 (minSdk 27).
     */
    private class TypefaceSpanCompat(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) = apply(paint)
        override fun updateMeasureState(paint: TextPaint) = apply(paint)

        private fun apply(paint: Paint) {
            paint.typeface = typeface
        }
    }
}
