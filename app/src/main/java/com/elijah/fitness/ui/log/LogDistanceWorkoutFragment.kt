package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.databinding.FragmentLogDistanceWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.main.MainActivity
import com.elijah.fitness.util.Formatters
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Manual logging screen for running and cycling. Reads the chosen
 * `ActivityType` from arguments so it can select the right title, save it
 * as the correct workout subclass, and show pace vs speed in the live
 * preview.
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
            container = (requireContext().applicationContext as FitnessApp).container
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLogDistanceWorkoutBinding =
        FragmentLogDistanceWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dateField.setOnClickListener { showDatePicker() }
        binding.timeField.setOnClickListener { showTimePicker() }

        val previewWatcher: (Any?) -> Unit = { updateLivePreview() }
        binding.hoursInput.doAfterTextChanged { previewWatcher(it) }
        binding.minutesInput.doAfterTextChanged { previewWatcher(it) }
        binding.secondsInput.doAfterTextChanged { previewWatcher(it) }
        binding.distanceInput.doAfterTextChanged { previewWatcher(it) }

        // Clear inline errors as the user starts fixing them.
        binding.distanceInput.doAfterTextChanged {
            binding.distanceFieldLayout.error = null
        }
        listOf(binding.hoursInput, binding.minutesInput, binding.secondsInput).forEach { input ->
            input.doAfterTextChanged { binding.durationError.isVisible = false }
        }

        binding.saveButton.setOnClickListener { attemptSave() }

        collectOnStart(viewModel.date) { renderDate(it) }
        collectOnStart(viewModel.time) { renderTime(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                LogDistanceWorkoutViewModel.Event.Saved -> onSaved()
                is LogDistanceWorkoutViewModel.Event.ValidationFailed -> showValidation(event.errors)
            }
        }

        updateLivePreview()
    }

    private fun renderDate(date: LocalDate) {
        binding.dateField.setText(dateFormatter.format(date))
    }

    private fun renderTime(time: LocalTime) {
        binding.timeField.setText(timeFormatter.format(time))
    }

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
            binding.dateFieldLayout.error = null
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
            binding.dateFieldLayout.error = null
        }
        picker.show(parentFragmentManager, "time-picker")
    }

    private fun readInt(input: com.google.android.material.textfield.TextInputEditText): Int =
        input.text?.toString()?.trim()?.toIntOrNull() ?: 0

    private fun readDistance(): Double? =
        binding.distanceInput.text?.toString()?.trim()?.toDoubleOrNull()

    private fun attemptSave() {
        viewModel.save(
            hours = readInt(binding.hoursInput),
            minutes = readInt(binding.minutesInput),
            seconds = readInt(binding.secondsInput),
            distanceKm = readDistance(),
            notes = binding.notesInput.text?.toString().orEmpty()
        )
    }

    private fun showValidation(errors: List<LogDistanceWorkoutViewModel.FieldError>) {
        // Clear previous
        binding.dateFieldLayout.error = null
        binding.distanceFieldLayout.error = null
        binding.durationError.isVisible = false

        errors.forEach { err ->
            val msg = getString(err.messageRes)
            when (err.field) {
                LogDistanceWorkoutViewModel.Field.DATE ->
                    binding.dateFieldLayout.error = msg
                LogDistanceWorkoutViewModel.Field.DISTANCE ->
                    binding.distanceFieldLayout.error = msg
                LogDistanceWorkoutViewModel.Field.DURATION -> {
                    binding.durationError.text = msg
                    binding.durationError.isVisible = true
                }
            }
        }
    }

    private fun onSaved() {
        val res = when (activityType) {
            ActivityType.CYCLING -> R.string.log_saved_ride
            else -> R.string.log_saved_run
        }
        (requireActivity() as? MainActivity)?.showSnackbar(getString(res))
        findNavController().popBackStack()
    }

    private fun updateLivePreview() {
        val hours = readInt(binding.hoursInput)
        val minutes = readInt(binding.minutesInput)
        val seconds = readInt(binding.secondsInput)
        val km = readDistance()
        val durationSeconds = hours * 3_600L + minutes * 60L + seconds.toLong()

        if (km == null || km <= 0.0 || durationSeconds <= 0L) {
            binding.livePreview.text = getString(R.string.log_live_preview_pending)
            return
        }
        val duration = Duration.ofSeconds(durationSeconds)
        val hoursDecimal = durationSeconds / 3_600.0
        val kmh = km / hoursDecimal
        val paceSecondsPerKm = (durationSeconds.toDouble() / km).toLong()

        binding.livePreview.text = when (activityType) {
            ActivityType.CYCLING -> getString(
                R.string.log_live_preview_speed,
                Formatters.formatSpeed(kmh)
            )
            else -> getString(
                R.string.log_live_preview_pace,
                Formatters.formatPace(paceSecondsPerKm)
            )
        }
        @Suppress("UNUSED_VARIABLE") val unused = duration // silences K2 "unused"
    }

    companion object {
        private val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM")
        private val timeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm")
    }
}
