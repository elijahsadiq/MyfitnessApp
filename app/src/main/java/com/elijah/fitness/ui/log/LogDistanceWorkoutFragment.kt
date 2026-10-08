package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentLogDistanceWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Manual logging screen for running + cycling (phase 8c step 5).
 * Rebuilt from `LogRun.dc.html`: dark live-summary card at the top,
 * Date + Time cards, three big HH:MM:SS fields, one big km field,
 * optional notes, pinned Save button.
 *
 * Validation (duration > 0, distance 0..300 km, date/time not future,
 * minutes/seconds in 0..59) is enforced in the ViewModel. Save success
 * navigates straight to the workout-detail screen for the new workout.
 */
class LogDistanceWorkoutFragment : BaseFragment<FragmentLogDistanceWorkoutBinding>() {

    private val activityType: ActivityType by lazy {
        arguments?.getString(ChooseActivityBottomSheet.ARG_ACTIVITY_TYPE)
            ?.let(ActivityType::valueOf)
            ?: ActivityType.RUNNING
    }

    private val viewModel: LogDistanceWorkoutViewModel by viewModels {
        LogDistanceWorkoutViewModel.factory(
            activityType = activityType,
            container = (requireContext().applicationContext as FitnessApp).container,
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLogDistanceWorkoutBinding =
        FragmentLogDistanceWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge: push the pinned Save button above the gesture bar
        // and above the keyboard when a field is focused.
        applyBottomNavInset(binding.logSaveContainer)

        bindActivityChrome()
        bindDateTimeFields()
        bindDurationFields()
        bindDistanceField()
        bindSaveButton()

        collectOnStart(viewModel.date) { renderDate(it) }
        collectOnStart(viewModel.time) { renderTime(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                is LogDistanceWorkoutViewModel.Event.Saved -> onSaved(event.clientUuid)
                is LogDistanceWorkoutViewModel.Event.ValidationFailed ->
                    showValidation(event.errors)
                is LogDistanceWorkoutViewModel.Event.SaveFailed ->
                    showSaveRetry(event.messageRes)
            }
        }

        updateLiveSummary()
    }

    // ---------- bindings ----------

    private fun bindActivityChrome() {
        when (activityType) {
            ActivityType.CYCLING -> {
                binding.logSummaryActivityIcon.setImageResource(R.drawable.ic_bike_24)
                binding.logSummaryActivityLabel.setText(R.string.log_summary_eyebrow_cycling)
                binding.logSummaryPrimaryLabel.setText(R.string.log_summary_metric_speed)
                binding.logSummaryPrimaryUnit.setText(R.string.log_summary_unit_speed)
                binding.logSummarySecondaryLabel.setText(R.string.log_summary_metric_pace)
                binding.logSummarySecondaryUnit.setText(R.string.log_summary_unit_pace)
                binding.saveButton.setText(R.string.log_save_ride_button)
            }
            else -> {
                binding.logSummaryActivityIcon.setImageResource(R.drawable.ic_run_24)
                binding.logSummaryActivityLabel.setText(R.string.log_summary_eyebrow_running)
                binding.logSummaryPrimaryLabel.setText(R.string.log_summary_metric_pace)
                binding.logSummaryPrimaryUnit.setText(R.string.log_summary_unit_pace)
                binding.logSummarySecondaryLabel.setText(R.string.log_summary_metric_speed)
                binding.logSummarySecondaryUnit.setText(R.string.log_summary_unit_speed)
                binding.saveButton.setText(R.string.log_save_run_button)
            }
        }
    }

    private fun bindDateTimeFields() {
        binding.logDateCard.setOnClickListener { showDatePicker() }
        binding.logTimeCard.setOnClickListener { showTimePicker() }
    }

    private fun bindDurationFields() {
        val update: (Any?) -> Unit = {
            updateLiveSummary()
            binding.logDurationError.isVisible = false
        }
        binding.hoursInput.doAfterTextChanged(update)
        binding.minutesInput.doAfterTextChanged(update)
        binding.secondsInput.doAfterTextChanged(update)
    }

    private fun bindDistanceField() {
        binding.distanceInput.doAfterTextChanged {
            updateLiveSummary()
            binding.logDistanceError.isVisible = false
        }
        binding.distanceInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSave()
                true
            } else false
        }
    }

    private fun bindSaveButton() {
        binding.saveButton.setOnClickListener { attemptSave() }
    }

    // ---------- rendering ----------

    private fun renderDate(date: LocalDate) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val eyebrow = when (date) {
            today -> getString(R.string.log_date_eyebrow_today)
            today.minusDays(1) -> getString(R.string.log_date_eyebrow_yesterday)
            else -> date.format(WEEKDAY_FORMATTER)
        }
        binding.logDateEyebrow.text = eyebrow
        binding.logDateValue.text = date.format(DATE_FORMATTER)
    }

    private fun renderTime(time: LocalTime) {
        binding.logTimeValue.text = time.format(TIME_FORMATTER)
    }

    private fun updateLiveSummary() {
        val hours = readInt(binding.hoursInput)
        val minutes = readInt(binding.minutesInput)
        val seconds = readInt(binding.secondsInput)
        val km = readDistance()
        val durationSeconds = hours * 3_600L + minutes * 60L + seconds.toLong()

        val weightKg = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value

        val summary = LogDistanceSummaryCalculator.calculate(
            durationSeconds = durationSeconds,
            distanceKm = km,
            activity = activityType,
            bodyWeightKg = weightKg,
        )

        if (summary == null) {
            binding.logSummaryPrimaryValue.setText(R.string.log_summary_metric_dash)
            binding.logSummarySecondaryValue.setText(R.string.log_summary_metric_dash)
            binding.logSummaryCaloriesValue.setText(R.string.log_summary_metric_dash)
            binding.logSummaryIncomplete.isVisible = true
            return
        }
        binding.logSummaryIncomplete.isVisible = false

        val paceText = formatPace(summary.paceSecondsPerKm)
        val speedText = String.format(Locale.getDefault(), "%.1f", summary.speedKmh)
        val kcalText = summary.calories.toString()

        when (activityType) {
            ActivityType.CYCLING -> {
                binding.logSummaryPrimaryValue.text = speedText
                binding.logSummarySecondaryValue.text = paceText
            }
            else -> {
                binding.logSummaryPrimaryValue.text = paceText
                binding.logSummarySecondaryValue.text = speedText
            }
        }
        binding.logSummaryCaloriesValue.text = kcalText
    }

    private fun formatPace(secondsPerKm: Int): String {
        val mm = secondsPerKm / 60
        val ss = secondsPerKm % 60
        return String.format(Locale.getDefault(), "%d:%02d", mm, ss)
    }

    // ---------- pickers ----------

    private fun showDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()
        val selectionMillis = viewModel.date.value
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.log_date_picker_title)
            .setSelection(selectionMillis)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
            viewModel.setDate(picked)
            binding.logDateError.isVisible = false
        }
        picker.show(parentFragmentManager, "date-picker")
    }

    private fun showTimePicker() {
        val current = viewModel.time.value
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.log_time_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            viewModel.setTime(LocalTime.of(picker.hour, picker.minute))
            binding.logDateError.isVisible = false
        }
        picker.show(parentFragmentManager, "time-picker")
    }

    // ---------- save ----------

    private fun readInt(input: android.widget.EditText): Int =
        input.text?.toString()?.trim()?.toIntOrNull() ?: 0

    /**
     * Parse the distance field, accepting either `.` or `,` as the
     * decimal separator so a locale that uses one isn't broken.
     */
    private fun readDistance(): Double? {
        val raw = binding.distanceInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.replace(',', '.').toDoubleOrNull()
    }

    private fun attemptSave() {
        viewModel.save(
            hours = readInt(binding.hoursInput),
            minutes = readInt(binding.minutesInput),
            seconds = readInt(binding.secondsInput),
            distanceKm = readDistance(),
            notes = binding.notesInput.text?.toString().orEmpty(),
        )
    }

    private fun showValidation(errors: List<LogDistanceWorkoutViewModel.FieldError>) {
        binding.logDateError.isVisible = false
        binding.logDurationError.isVisible = false
        binding.logDistanceError.isVisible = false

        errors.forEach { err ->
            val msg = getString(err.messageRes)
            when (err.field) {
                LogDistanceWorkoutViewModel.Field.DATE -> {
                    binding.logDateError.text = msg
                    binding.logDateError.isVisible = true
                }
                LogDistanceWorkoutViewModel.Field.DURATION -> {
                    binding.logDurationError.text = msg
                    binding.logDurationError.isVisible = true
                }
                LogDistanceWorkoutViewModel.Field.DISTANCE -> {
                    binding.logDistanceError.text = msg
                    binding.logDistanceError.isVisible = true
                }
            }
        }
    }

    private fun onSaved(clientUuid: String) {
        findNavController().navigate(
            R.id.action_log_distance_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    private fun showSaveRetry(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.log_error_save_retry) { attemptSave() }
            .show()
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
        val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }
}
