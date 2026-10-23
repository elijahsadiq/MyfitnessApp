package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.remote.mapper.activityTypeOrNull
import com.elijah.fitness.databinding.FragmentLogDistanceWorkoutBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.navigateSafe
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.elijah.fitness.util.Fonts
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.MetricText
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Manual logging screen for running, cycling and rowing (phase 8c step 5;
 * rowing added in phase 8d with its pace per 500 m).
 * Rebuilt from `LogRun.dc.html`: dark live-summary card at the top,
 * Date + Time cards ([LogDateTimePickers], shared with every log form),
 * the shared h / min / s duration cells
 * ([com.elijah.fitness.ui.components.DurationInputView], phase 8d), one
 * big km field, optional notes, pinned Save button.
 *
 * The header is the Log a swim one (phase 8d): a round back button and
 * "Log a run" / "Log a ride" / "Log a row"; the shared toolbar is hidden.
 *
 * Validation is enforced in the ViewModel: distance above 0 and at most
 * 300 km, plus the rules every log form shares ([LogFormRules]: start
 * not in the future, minutes and seconds 0–59, duration above zero and
 * at most 24 h). Save success navigates straight to the workout-detail
 * screen for the new workout; a failed save keeps the form and offers
 * Retry.
 */
class LogDistanceWorkoutFragment : BaseFragment<FragmentLogDistanceWorkoutBinding>() {

    /**
     * What this screen logs: runs, rides and rows only. A missing or
     * unknown argument, or a type with its own screen (swims, lifts),
     * becomes Running, so the screen's wording and the workout it saves
     * always agree — never a crash or a mislabelled save.
     */
    private val activityType: ActivityType by lazy {
        when (val requested = activityTypeOrNull(
            arguments?.getString(ChooseActivityBottomSheet.ARG_ACTIVITY_TYPE)
        )) {
            ActivityType.RUNNING,
            ActivityType.CYCLING,
            ActivityType.ROWING -> requested
            ActivityType.SWIMMING,
            ActivityType.WEIGHTLIFTING,
            null -> ActivityType.RUNNING
        }
    }

    private val viewModel: LogDistanceWorkoutViewModel by viewModels {
        LogDistanceWorkoutViewModel.factory(
            activityType = activityType,
            container = (requireContext().applicationContext as FitnessApp).container,
        )
    }

    /** Lighter weight for the small unit next to a big number ("4.12 km"). */
    private val unitFont: android.graphics.Typeface
        get() = Fonts.medium(requireContext())

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLogDistanceWorkoutBinding =
        FragmentLogDistanceWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // A date / time picker that was open when the screen was rebuilt still answers.
        LogDateTimePickers.reattach(this, ::onDatePicked, ::onTimePicked)

        // The screen draws its own header (like Log a swim), so the status
        // bar is cleared here; the pinned Save button sits above the
        // gesture bar, or the keyboard when a field is focused.
        applyInsets(binding.root, top = true, sides = true)
        applyBottomNavInset(binding.logSaveContainer)

        bindHeader()
        bindActivityChrome()
        bindDateTimeFields()
        bindDurationFields()
        bindDistanceField()
        bindSaveButton()

        collectOnStart(viewModel.date) { renderDate(it) }
        collectOnStart(viewModel.time) { renderTime(it) }
        collectOnStart(viewModel.isSaving) { saving ->
            binding.saveButton.isEnabled = !saving
        }
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

    private fun bindHeader() {
        ViewCompat.setAccessibilityHeading(binding.logTitle, true)
        binding.logBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun bindActivityChrome() {
        when (activityType) {
            ActivityType.ROWING -> {
                binding.logSummaryActivityIcon.setImageResource(R.drawable.ic_row_24)
                binding.logSummaryActivityLabel.setText(R.string.log_summary_eyebrow_rowing)
                binding.logSummaryPrimaryLabel.setText(R.string.log_summary_metric_pace)
                binding.logSummarySecondaryLabel.setText(R.string.log_summary_metric_speed)
                binding.saveButton.setText(R.string.log_save_row_button)
                binding.logTitle.setText(R.string.log_row_title)
            }
            ActivityType.CYCLING -> {
                binding.logSummaryActivityIcon.setImageResource(R.drawable.ic_bike_24)
                binding.logSummaryActivityLabel.setText(R.string.log_summary_eyebrow_cycling)
                binding.logSummaryPrimaryLabel.setText(R.string.log_summary_metric_speed)
                binding.logSummarySecondaryLabel.setText(R.string.log_summary_metric_pace)
                binding.saveButton.setText(R.string.log_save_ride_button)
                binding.logTitle.setText(R.string.log_ride_title)
            }
            // Swims and lifts never get here: [activityType] turns them
            // into Running.
            ActivityType.RUNNING,
            ActivityType.SWIMMING,
            ActivityType.WEIGHTLIFTING -> {
                binding.logSummaryActivityIcon.setImageResource(R.drawable.ic_run_24)
                binding.logSummaryActivityLabel.setText(R.string.log_summary_eyebrow_running)
                binding.logSummaryPrimaryLabel.setText(R.string.log_summary_metric_pace)
                binding.logSummarySecondaryLabel.setText(R.string.log_summary_metric_speed)
                binding.saveButton.setText(R.string.log_save_run_button)
                binding.logTitle.setText(R.string.log_run_title)
            }
        }
    }

    private fun bindDateTimeFields() {
        binding.logDateCard.setOnClickListener { showDatePicker() }
        binding.logTimeCard.setOnClickListener { showTimePicker() }
    }

    private fun bindDurationFields() {
        binding.durationInput.doOnDurationChanged {
            val b = bindingOrNull ?: return@doOnDurationChanged
            updateLiveSummary()
            b.logDurationError.isVisible = false
        }
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
        binding.logDateValue.text = date.format(LogDateTimePickers.dateFormatter())
    }

    private fun renderTime(time: LocalTime) {
        // "14:31" or "2:31 PM", following the phone's 24-hour setting.
        binding.logTimeValue.text = time.format(LogDateTimePickers.timeFormatter(requireContext()))
    }

    private fun updateLiveSummary() {
        val km = readDistance()
        val durationSeconds = binding.durationInput.let {
            LogFormRules.durationSeconds(it.hours, it.minutes, it.seconds)
        }

        val weightKg = (requireContext().applicationContext as FitnessApp)
            .container.userSettings.bodyWeightKg.value

        val summary = LogDistanceSummaryCalculator.calculate(
            durationSeconds = durationSeconds,
            distanceKm = km,
            activity = activityType,
            bodyWeightKg = weightKg,
        )

        val muted = ContextCompat.getColor(requireContext(), R.color.goal_main_muted)
        val dash = getString(R.string.log_summary_metric_dash)
        binding.logSummaryIncomplete.isVisible = summary == null
        val pace = MetricText.of(
            summary?.let { formatPace(it.paceSecondsPerKm) } ?: dash,
            getString(R.string.log_summary_unit_pace), muted,
            unitTypeface = unitFont,
        )
        val speed = MetricText.of(
            summary?.let { String.format(Locale.getDefault(), "%.1f", it.speedKmh) } ?: dash,
            getString(R.string.log_summary_unit_speed), muted,
            unitTypeface = unitFont,
        )
        binding.logSummaryCaloriesValue.text = MetricText.of(
            summary?.let { Formatters.formatKcalValue(it.calories) } ?: dash,
            getString(R.string.log_summary_unit_kcal), muted,
            unitTypeface = unitFont,
        )
        when (activityType) {
            ActivityType.CYCLING -> {
                binding.logSummaryPrimaryValue.text = speed
                binding.logSummarySecondaryValue.text = pace
            }
            // Rowers read pace per 500 m, the standard split.
            ActivityType.ROWING -> {
                binding.logSummaryPrimaryValue.text = MetricText.of(
                    summary?.let { formatPace(it.splitPaceSeconds) } ?: dash,
                    getString(R.string.log_summary_unit_pace_500m), muted,
                    unitTypeface = unitFont,
                )
                binding.logSummarySecondaryValue.text = speed
            }
            ActivityType.RUNNING,
            ActivityType.SWIMMING,
            ActivityType.WEIGHTLIFTING -> {
                binding.logSummaryPrimaryValue.text = pace
                binding.logSummarySecondaryValue.text = speed
            }
        }
    }

    private fun formatPace(secondsPerKm: Int): String {
        val mm = secondsPerKm / 60
        val ss = secondsPerKm % 60
        return String.format(Locale.getDefault(), "%d:%02d", mm, ss)
    }

    // ---------- pickers ----------

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

    // ---------- save ----------

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
            hours = binding.durationInput.hours,
            minutes = binding.durationInput.minutes,
            seconds = binding.durationInput.seconds,
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
        findNavController().navigateSafe(
            R.id.action_log_distance_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    private fun showSaveRetry(messageRes: Int) {
        snackbar(messageRes, anchor = binding.logSaveContainer, kind = SnackKind.ERROR)
            .setAction(R.string.log_error_save_retry) { attemptSave() }
            .show()
    }

    private companion object {
        val WEEKDAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    }
}
