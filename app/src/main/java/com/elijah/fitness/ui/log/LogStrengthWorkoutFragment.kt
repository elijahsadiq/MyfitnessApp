package com.elijah.fitness.ui.log

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
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
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.history.WorkoutDetailFragment
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
import java.util.Locale

/**
 * Weightlifting log form. Same look and flow as the run / ride form:
 * When (date + time), How long?, Sets, Notes, and a Save button pinned
 * to the bottom. Sets are added through a dialog and stored in the
 * ViewModel; the RecyclerView only displays them, so the classic
 * "EditTexts inside recycled rows" trap is avoided. Saving opens the new
 * workout's detail screen, exactly like a saved run.
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

        // The shared toolbar handles the top; the pinned Save bar clears
        // the gesture bar, or the keyboard while it's open.
        applyBottomNavInset(binding.logSaveContainer)

        binding.setsList.adapter = setsAdapter
        binding.setsList.layoutManager = LinearLayoutManager(requireContext())

        binding.logDateCard.setOnClickListener { showDatePicker() }
        binding.logTimeCard.setOnClickListener { showTimePicker() }
        binding.addSetButton.setOnClickListener { showAddSetDialog() }
        binding.saveButton.setOnClickListener { attemptSave() }

        listOf(binding.hoursInput, binding.minutesInput, binding.secondsInput).forEach { input ->
            input.doAfterTextChanged { binding.durationError.isVisible = false }
        }

        collectOnStart(viewModel.date) { renderDate(it) }
        collectOnStart(viewModel.time) { renderTime(it) }
        collectOnStart(viewModel.isSaving) { saving -> binding.saveButton.isEnabled = !saving }
        collectOnStart(viewModel.sets) { sets ->
            setsAdapter.submitList(sets)
            binding.setsEmptyHint.isVisible = sets.isEmpty()
            binding.setsList.isVisible = sets.isNotEmpty()
            if (sets.isNotEmpty()) binding.setsError.isVisible = false
        }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                is LogStrengthWorkoutViewModel.Event.Saved -> onSaved(event.clientUuid)
                is LogStrengthWorkoutViewModel.Event.ValidationFailed ->
                    showValidation(event.errors)
                is LogStrengthWorkoutViewModel.Event.SaveFailed ->
                    showSaveRetry(event.messageRes)
            }
        }
    }

    override fun onDestroyView() {
        binding.setsList.adapter = null
        super.onDestroyView()
    }

    private fun renderDate(date: LocalDate) {
        val today = LocalDate.now(ZoneId.systemDefault())
        binding.logDateEyebrow.text = when (date) {
            today -> getString(R.string.log_date_eyebrow_today)
            today.minusDays(1) -> getString(R.string.log_date_eyebrow_yesterday)
            else -> date.format(WEEKDAY_FORMATTER)
        }
        binding.logDateValue.text = date.format(DATE_FORMATTER)
    }

    private fun renderTime(time: LocalTime) {
        // "14:31" or "2:31 PM", following the phone's 24-hour setting.
        binding.logTimeValue.text = time.format(
            DateTimeFormatter.ofPattern(
                if (DateFormat.is24HourFormat(requireContext())) "HH:mm" else "h:mm a",
                Locale.getDefault(),
            )
        )
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
            binding.logDateError.isVisible = false
        }
        picker.show(parentFragmentManager, "date-picker")
    }

    private fun showTimePicker() {
        val current = viewModel.time.value
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H
                else TimeFormat.CLOCK_12H
            )
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

    /**
     * Add-a-set dialog. Each field shows its own error and the dialog
     * stays open until the set is valid, so nothing typed is lost.
     */
    private fun showAddSetDialog() {
        val form = DialogAddSetBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.log_strength_add_set_title)
            .setView(form.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.log_strength_add_set_confirm, null)
            .create()

        form.exerciseInput.doAfterTextChanged { form.exerciseLayout.error = null }
        form.repsInput.doAfterTextChanged { form.repsLayout.error = null }
        form.weightInput.doAfterTextChanged { form.weightLayout.error = null }

        fun tryAdd() {
            val exercise = form.exerciseInput.text?.toString()?.trim().orEmpty()
            val reps = form.repsInput.text?.toString()?.trim()?.toIntOrNull()
            val weight = form.weightInput.text?.toString()?.trim()
                ?.replace(',', '.')?.toDoubleOrNull()
            form.exerciseLayout.error =
                if (exercise.isBlank()) getString(R.string.log_strength_error_exercise) else null
            form.repsLayout.error =
                if (reps == null || reps !in 1..MAX_REPS) getString(R.string.log_strength_error_reps) else null
            form.weightLayout.error =
                if (weight == null || weight !in 0.0..MAX_WEIGHT_KG) getString(R.string.log_strength_error_weight) else null
            if (form.exerciseLayout.error == null && form.repsLayout.error == null &&
                form.weightLayout.error == null
            ) {
                viewModel.addSet(StrengthSet(exercise, reps!!, weight!!))
                dialog.dismiss()
            }
        }

        form.weightInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { tryAdd(); true } else false
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { tryAdd() }
        }
        dialog.show()
        form.exerciseInput.requestFocus()
    }

    private fun readInt(input: EditText): Int =
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
        binding.logDateError.isVisible = false
        binding.durationError.isVisible = false
        binding.setsError.isVisible = false

        errors.forEach { err ->
            val target = when (err.field) {
                LogStrengthWorkoutViewModel.Field.DATE -> binding.logDateError
                LogStrengthWorkoutViewModel.Field.DURATION -> binding.durationError
                LogStrengthWorkoutViewModel.Field.SETS -> binding.setsError
            }
            target.text = getString(err.messageRes)
            target.isVisible = true
        }
    }

    private fun onSaved(clientUuid: String) {
        findNavController().navigate(
            R.id.action_log_strength_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    private fun showSaveRetry(messageRes: Int) {
        snackbar(messageRes, anchor = binding.logSaveContainer, kind = SnackKind.ERROR)
            .setAction(R.string.log_error_save_retry) { attemptSave() }
            .show()
    }

    companion object {
        private const val MAX_REPS = 100
        private const val MAX_WEIGHT_KG = 500.0

        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        private val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    }
}
