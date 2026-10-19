package com.elijah.fitness.ui.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ItemCalendarDayBinding
import com.elijah.fitness.databinding.ItemCalendarPageBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What one calendar page shows: rows of days from [firstDay], for [month] (null on a week page). */
data class CalendarPage(
    val firstDay: LocalDate,
    val month: YearMonth?,
    val ui: CalendarUi,
)

/**
 * Pages for the Home calendar's two ViewPager2s: [Mode.WEEK] (one week
 * per page — the strip) and [Mode.MONTH] (a six-row month grid per page).
 * The last page is always the current week / month.
 */
class CalendarPageAdapter(
    private val mode: Mode,
    private val onDayTap: (LocalDate) -> Unit,
) : RecyclerView.Adapter<CalendarPageViewHolder>() {

    enum class Mode(val rows: Int) { WEEK(1), MONTH(CalendarMath.GRID_ROWS) }

    var ui: CalendarUi? = null
        // A new day can add a page; anything else only restyles the days.
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            val pagesChanged = field?.today != value?.today
            field = value
            if (pagesChanged) notifyDataSetChanged() else notifyItemRangeChanged(0, itemCount)
        }

    /**
     * Alpha of the days that belong to the next / previous month. They
     * fade while the month opens, so the week row matches the strip.
     */
    var outsideAlpha = 1f

    override fun getItemCount(): Int {
        val today = ui?.today ?: return 0
        return when (mode) {
            Mode.WEEK -> CalendarMath.weekPageCount(today)
            Mode.MONTH -> CalendarMath.monthPageCount()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarPageViewHolder =
        CalendarPageViewHolder(
            ItemCalendarPageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            mode.rows,
            onDayTap,
        )

    override fun onBindViewHolder(holder: CalendarPageViewHolder, position: Int) {
        val ui = ui ?: return
        val page = when (mode) {
            Mode.WEEK -> CalendarPage(CalendarMath.weekForPage(position, ui.today), null, ui)
            Mode.MONTH -> {
                val month = CalendarMath.monthForPage(position, ui.today)
                CalendarPage(CalendarMath.gridStart(month), month, ui)
            }
        }
        holder.outsideAlpha = outsideAlpha
        holder.bind(page)
    }
}

/**
 * One week or month page. The day cells are inflated once per holder and
 * restyled on every bind.
 */
class CalendarPageViewHolder(
    binding: ItemCalendarPageBinding,
    rows: Int,
    private val onDayTap: (LocalDate) -> Unit,
) : BaseViewHolder<CalendarPage>(binding) {

    private val cells: List<ItemCalendarDayBinding>
    private var page: CalendarPage? = null
    private val locale = Locale.getDefault()
    private val cellDescription = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)

    /** See [CalendarPageAdapter.outsideAlpha]. */
    var outsideAlpha = 1f

    init {
        val context = binding.root.context
        val inflater = LayoutInflater.from(context)
        val rowHeight = context.resources.getDimensionPixelSize(R.dimen.calendar_row_height)
        cells = buildList {
            repeat(rows) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                binding.calendarRows.addView(
                    row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight),
                )
                repeat(CalendarMath.DAYS_PER_WEEK) {
                    val cell = ItemCalendarDayBinding.inflate(inflater, row, false)
                    row.addView(cell.root)
                    add(cell)
                }
            }
        }
    }

    override fun bind(item: CalendarPage) {
        page = item
        cells.forEachIndexed { index, cell ->
            bindDay(cell, item.firstDay.plusDays(index.toLong()), item)
        }
    }

    /** Re-apply only the alpha — runs on every frame while the month opens. */
    fun applyOutsideAlpha(alpha: Float) {
        outsideAlpha = alpha
        val page = page ?: return
        cells.forEachIndexed { index, cell ->
            cell.root.alpha = alphaFor(page.firstDay.plusDays(index.toLong()), page)
        }
    }

    private fun bindDay(cell: ItemCalendarDayBinding, date: LocalDate, page: CalendarPage) {
        val context = cell.root.context
        val ui = page.ui
        val selected = date == ui.selected
        val hasWorkout = date in ui.workoutDays
        val selectable = CalendarMath.isSelectable(date, ui.today)

        cell.dayNumber.text = String.format(locale, "%d", date.dayOfMonth)
        cell.root.setBackgroundResource(
            when {
                selected -> R.drawable.bg_calendar_day_selected
                date == ui.today -> R.drawable.bg_calendar_day_today
                else -> 0
            }
        )
        cell.dayNumber.setTextColor(
            ContextCompat.getColor(context, if (selected) R.color.text_on_dark else R.color.text_primary)
        )
        cell.dayDot.isInvisible = !hasWorkout
        // Lime only on the dark pill — a lime dot on white is close to invisible.
        cell.dayDot.backgroundTintList = ContextCompat.getColorStateList(
            context, if (selected) R.color.lime_accent else R.color.text_primary,
        )
        cell.root.alpha = alphaFor(date, page)
        cell.root.isEnabled = selectable
        cell.root.isSelected = selected
        cell.root.contentDescription = date.format(cellDescription) +
            if (hasWorkout) context.getString(R.string.home_week_strip_active_suffix) else ""
        cell.root.setOnClickListener { onDayTap(date) }
    }

    private fun alphaFor(date: LocalDate, page: CalendarPage): Float = when {
        !CalendarMath.isSelectable(date, page.ui.today) -> MUTED_ALPHA
        page.month != null && YearMonth.from(date) != page.month -> outsideAlpha
        else -> 1f
    }

    companion object {
        /** Days that can't be picked, and other months' days in a month grid. */
        const val MUTED_ALPHA = 0.35f
    }
}
