package com.elijah.fitness.ui.dashboard

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ItemWeekStripDayBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The horizontal week strip on Home. Seven day cells (M..S) with:
 *  - Day letter
 *  - Day-of-month number
 *  - A lime dot if the user has a workout that day
 *  - A dark pill background if the day is today
 *
 * Data-driven — the fragment calls [setData] with the current
 * [LocalDate] and the set of days that already have a workout logged.
 */
class WeekStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = HORIZONTAL
        weightSum = 7f
        setPadding(dpToPx(6f))
    }

    fun setData(monday: LocalDate, today: LocalDate, daysWithWorkouts: Set<DayOfWeek>) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            val cellBinding = ItemWeekStripDayBinding.inflate(inflater, this, false).apply {
                dayLetter.text = date.dayOfWeek.letter()
                dayNumber.text = date.dayOfMonth.toString()
                val hasWorkout = date.dayOfWeek in daysWithWorkouts
                dayDot.visibility = if (hasWorkout) View.VISIBLE else View.INVISIBLE
                val isToday = date == today
                if (isToday) {
                    root.setBackgroundResource(R.drawable.bg_week_strip_today)
                    dayLetter.setTextColor(ContextCompat.getColor(context, R.color.text_on_dark_muted))
                    dayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_on_dark))
                    // Lime only on the dark pill — a lime dot on white is
                    // close to invisible.
                    dayDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.lime_accent)
                } else {
                    root.background = null
                    dayLetter.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    dayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    dayDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.text_primary)
                }
                root.contentDescription = date.format(CELL_DESCRIPTION) +
                    if (hasWorkout) context.getString(R.string.home_week_strip_active_suffix) else ""
            }
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER
            }
            addView(cellBinding.root, lp)
        }
    }

    /** "M", "T", "W"… in the phone's language. */
    private fun DayOfWeek.letter(): String =
        getDisplayName(TextStyle.NARROW, Locale.getDefault())

    private companion object {
        val CELL_DESCRIPTION: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}
