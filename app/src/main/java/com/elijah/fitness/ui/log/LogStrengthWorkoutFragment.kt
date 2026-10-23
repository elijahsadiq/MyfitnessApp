package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
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
import com.elijah.fitness.ui.base.navigateSafe
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Weightlifting log form ("Log a workout"). Same look and flow as the
 * run / ride / row form: the Log a swim header (round back button +
 * title), When (date + time), How long?, Sets, Notes, and a Save button
 * pinned to the bottom. Saving checks the [LogFormRules] every form
 * shares (start not in the future, minutes and seconds 0–59, duration
 * above zero and at most 24 h) and needs at least one set. Sets are added through a dialog and stored in the
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
        // A date / time picker that was open when the screen was rebuilt still answers.
        LogDateTimePickers.reattach(this, ::onDatePicked, ::onTimePicked)

        // The screen draws its own header (like Log a swim), so the status
        // bar is cleared here; the pinned Save bar clears the gesture bar,
        // or the keyboard while it's open.
        applyInsets(binding.root, top = true, sides = true)
        applyBottomNavInset(binding.logSaveContainer)

        ViewCompat.setAccessibilityHeading(binding.logTitle, true)
        binding.logBack.setOnClickListener { findNavController().navigateUp() }

        binding.setsList.adapter = setsAdapter
        binding.setsList.layoutManager = LinearLayoutManager(requireContext())

        binding.logDateCard.setOnClickListener { showDatePicker() }
        binding.logTimeCard.setOnClickListener { showTimePicker() }
        binding.addSetButton.setOnClickListener { showAddSetDialog() }
        binding.saveButton.setOnClickListener { attemptSave() }

        binding.durationInput.doOnDurationChanged {
            bindingOrNull?.durationError?.isVisible = false
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
        binding.logDateValue.text = date.format(LogDateTimePickers.dateFormatter())
    }

    private fun renderTime(time: LocalTime) {
        // "14:31" or "2:31 PM", following the phone's 24-hour setting.
        binding.logTimeValue.text = time.format(LogDateTimePickers.timeFormatter(requireContext()))
    }

    private fun showDatePicker() {
        LogDateTimePickers.showDatePicker(this, viewModel.date.value, ::onDatePicked)
    }

    private fun showTimePicker() {
        LogDateTimePickers.showTimePicker(this, viewModel.time.value, ::onTimePicked)
    }

    private fun onDatePicked(date: LocalDate) {
        viewModel.setDate(date)
        bindingOrNull?.logDateError?.isVisible = false
    }

    private fun onTimePicked(time: LocalTime) {
        viewModel.setTime(time)
        bindingOrNull?.logDateError?.isVisible = false
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

    private fun attemptSave() {
        viewModel.save(
            hours = binding.durationInput.hours,
            minutes = binding.durationInput.minutes,
            seconds = binding.durationInput.seconds,
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
        findNavController().navigateSafe(
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

        private val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    }
}
