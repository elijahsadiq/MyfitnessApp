package com.elijah.fitness.ui.log

import android.content.Context
import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.elijah.fitness.R
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Date and Start time pickers of every log form (run / ride / row,
 * swim, workout), so they all behave the same:
 *
 * - the date picker can't pick a day after today (the ViewModel still
 *   rejects a start time later today, see [LogFormRules.startError]);
 * - the time picker and the time text follow the phone's 24-hour setting;
 * - a quick double tap opens one picker, not two stacked ones;
 * - a picker left open while the screen is rebuilt (font size or
 *   language change, rotation on a tablet) still delivers its answer:
 *   the pickers live in the form's own child fragment manager and the
 *   form calls [reattach] from `onViewCreated` to hook the restored
 *   picker back up.
 */
object LogDateTimePickers {

    /**
     * "Mon 19 Oct" — the date on the log forms' Date cards, in the
     * phone's current language (built per call, so a language change
     * while the app is open is picked up).
     */
    fun dateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    /** "14:31" or "2:31 PM", following the phone's 24-hour setting. */
    fun timeFormatter(context: Context): DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        Locale.getDefault(),
    )

    /**
     * Material date picker on [current]; days after today are greyed out.
     * Does nothing while a date picker is already showing.
     */
    fun showDatePicker(fragment: Fragment, current: LocalDate, onPicked: (LocalDate) -> Unit) {
        val manager = fragment.childFragmentManager
        if (!canShow(manager, DATE_PICKER_TAG)) return
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()
        // The picker works in UTC midnights.
        val selectionMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.log_date_picker_title)
            .setSelection(selectionMillis)
            .setCalendarConstraints(constraints)
            .build()
        listenToDate(picker, onPicked)
        // showNow: added at once, so a second tap in the same frame finds it.
        picker.showNow(manager, DATE_PICKER_TAG)
    }

    /**
     * Material time picker on [current], 12- or 24-hour like the phone.
     * Does nothing while a time picker is already showing.
     */
    fun showTimePicker(fragment: Fragment, current: LocalTime, onPicked: (LocalTime) -> Unit) {
        val manager = fragment.childFragmentManager
        if (!canShow(manager, TIME_PICKER_TAG)) return
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (DateFormat.is24HourFormat(fragment.requireContext())) TimeFormat.CLOCK_24H
                else TimeFormat.CLOCK_12H
            )
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.log_time_picker_title)
            .build()
        listenToTime(picker, onPicked)
        picker.showNow(manager, TIME_PICKER_TAG)
    }

    /**
     * Hooks a picker that Android restored with the screen back up to
     * the new screen's callbacks (listeners aren't saved with it). Call
     * from `onViewCreated` with the same callbacks the cards use.
     */
    fun reattach(
        fragment: Fragment,
        onDatePicked: (LocalDate) -> Unit,
        onTimePicked: (LocalTime) -> Unit,
    ) {
        val manager = fragment.childFragmentManager
        @Suppress("UNCHECKED_CAST")
        (manager.findFragmentByTag(DATE_PICKER_TAG) as? MaterialDatePicker<Long>)
            ?.let { listenToDate(it, onDatePicked) }
        (manager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)
            ?.let { listenToTime(it, onTimePicked) }
    }

    /** False while that picker is already up, or once the screen's state is saved. */
    private fun canShow(manager: FragmentManager, tag: String): Boolean =
        !manager.isStateSaved && !manager.isDestroyed && manager.findFragmentByTag(tag) == null

    private fun listenToDate(picker: MaterialDatePicker<Long>, onPicked: (LocalDate) -> Unit) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener { millis ->
            // The selection is never null on a positive click; guard anyway.
            val picked = millis ?: return@addOnPositiveButtonClickListener
            onPicked(Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    private fun listenToTime(picker: MaterialTimePicker, onPicked: (LocalTime) -> Unit) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener {
            onPicked(LocalTime.of(picker.hour, picker.minute))
        }
    }

    private const val DATE_PICKER_TAG = "date-picker"
    private const val TIME_PICKER_TAG = "time-picker"
}
