package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.databinding.DialogAddSetBinding
import com.elijah.fitness.databinding.FragmentLogStrengthWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.main.MainActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Strength-log screen. Sets are added through a dialog and stored in the
 * ViewModel; the RecyclerView is a display-only view of that list, so the
 * classic "EditTexts inside recycled rows" trap is avoided.
 */
class LogStrengthWorkoutFragment : BaseFragment<FragmentLogStrengthWorkoutBinding>() {

    private val viewModel: LogStrengthWorkoutViewModel by viewModels {
        LogStrengthWorkoutViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container
        )
    }

    private val setsAdapter = StrengthSetAdapter(onDelete = { index ->
        viewModel.removeSetAt(index)
    })

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLogStrengthWorkoutBinding =
        FragmentLogStrengthWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.setsList.adapter = setsAdapter
        binding.setsList.layoutManager = LinearLayoutManager(requireContext())

        binding.dateField.setOnClickListener { showDatePicker() }
        binding.timeField.setOnClickListener { showTimePicker() }
        binding.addSetButton.setOnClickListener { showAddSetDialog() }
        binding.saveButton.setOnClickListener { attemptSave() }

        listOf(binding.hoursInput, binding.minutesInput, binding.secondsInput).forEach { input ->
            input.doAfterTextChanged { binding.durationError.isVisible = false }
        }

        collectOnStart(viewModel.date) { renderDate(it) }
        collectOnStart(viewModel.time) { renderTime(it) }
        collectOnStart(viewModel.sets) { sets ->
            setsAdapter.submitList(sets)
            binding.setsEmptyHint.isVisible = sets.isEmpty()
            binding.setsList.isVisible = sets.isNotEmpty()
            if (sets.isNotEmpty()) binding.setsError.isVisible = false
        }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                LogStrengthWorkoutViewModel.Event.Saved -> onSaved()
                is LogStrengthWorkoutViewModel.Event.ValidationFailed ->
                    showValidation(event.errors)
            }
        }
    }

    override fun onDestroyView() {
        binding.setsList.adapter = null
        super.onDestroyView()
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

    private fun showAddSetDialog() {
        val dialogBinding = DialogAddSetBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.log_strength_add_set_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.log_strength_add_set_confirm) { _, _ ->
                val exercise = dialogBinding.exerciseInput.text?.toString()?.trim().orEmpty()
                val reps = dialogBinding.repsInput.text?.toString()?.toIntOrNull()
                val weight = dialogBinding.weightInput.text?.toString()?.toDoubleOrNull()
                val valid =
                    exercise.isNotBlank() &&
                        reps != null && reps in 1..MAX_REPS &&
                        weight != null && weight in 0.0..MAX_WEIGHT_KG
                if (!valid) {
                    showError(getString(R.string.log_strength_invalid_set))
                    return@setPositiveButton
                }
                viewModel.addSet(StrengthSet(exercise, reps!!, weight!!))
            }
            .show()
    }

    private fun readInt(input: com.google.android.material.textfield.TextInputEditText): Int =
        input.text?.toString()?.trim()?.toIntOrNull() ?: 0

    private fun attemptSave() {
        viewModel.save(
            hours = readInt(binding.hoursInput),
            minutes = readInt(binding.minutesInput),
            seconds = readInt(binding.secondsInput),
            notes = binding.notesInput.text?.toString().orEmpty()
        )
    }

    private fun showValidation(errors: List<LogStrengthWorkoutViewModel.FieldError>) {
        binding.dateFieldLayout.error = null
        binding.durationError.isVisible = false
        binding.setsError.isVisible = false

        errors.forEach { err ->
            val msg = getString(err.messageRes)
            when (err.field) {
                LogStrengthWorkoutViewModel.Field.DATE ->
                    binding.dateFieldLayout.error = msg
                LogStrengthWorkoutViewModel.Field.DURATION -> {
                    binding.durationError.text = msg
                    binding.durationError.isVisible = true
                }
                LogStrengthWorkoutViewModel.Field.SETS -> {
                    binding.setsError.text = msg
                    binding.setsError.isVisible = true
                }
            }
        }
    }

    private fun onSaved() {
        (requireActivity() as? MainActivity)
            ?.showSnackbar(getString(R.string.log_saved_strength))
        findNavController().popBackStack()
    }

    companion object {
        private const val MAX_REPS = 100
        private const val MAX_WEIGHT_KG = 500.0

        private val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM")
        private val timeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm")
    }
}
