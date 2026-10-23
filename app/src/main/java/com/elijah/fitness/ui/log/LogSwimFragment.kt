package com.elijah.fitness.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentLogSwimBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.navigateSafe
import com.elijah.fitness.ui.history.WorkoutDetailFragment
import com.elijah.fitness.util.Formatters
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Log a swim (phase 8d, LogSwim.dc.html), opened from the Log a workout
 * sheet's Swimming card. Extends [BaseFragment] like every other screen.
 *
 * - **Header:** round back button + "Log a swim" (the shared toolbar
 *   is hidden on this screen, like the bottom navigation).
 * - **Live summary:** pace per 100 m, distance and calories, worked out
 *   by [LogSwimViewModel] as the inputs change.
 * - **Inputs:** the same Date / Start time pickers as the run form
 *   ([LogDateTimePickers]), a 25 m / 50 m pool control, a laps
 *   [com.elijah.fitness.ui.components.StepperView] and the shared
 *   [com.elijah.fitness.ui.components.DurationInputView].
 * - **Save swim:** pinned under the scrolling column, above the gesture
 *   bar or the keyboard. Errors show inline under their field (and the
 *   column scrolls to the first one); a saved swim opens its detail
 *   screen, exactly like a saved run.
 */
class LogSwimFragment : BaseFragment<FragmentLogSwimBinding>() {

    private val viewModel: LogSwimViewModel by viewModels {
        LogSwimViewModel.factory((requireContext().applicationContext as FitnessApp).container)
    }

    private val text by lazy {
        (requireContext().applicationContext as FitnessApp).container.text
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLogSwimBinding = FragmentLogSwimBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Status bar above the header; the pinned Save bar clears the
        // gesture bar, or the keyboard while it's open.
        applyInsets(binding.root, top = true, sides = true)
        applyBottomNavInset(binding.logSwimSaveBar)

        bindHeader()
        bindAccessibility()
        bindInputs()

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.isSaving) { saving ->
            binding.logSwimSaveButton.isEnabled = !saving
        }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                is LogSwimViewModel.Event.Saved -> onSaved(event.clientUuid)
                is LogSwimViewModel.Event.ValidationFailed -> showValidation(event.errors)
                is LogSwimViewModel.Event.SaveFailed -> showSaveRetry(event.messageRes)
            }
        }
    }

    // ---------- bindings ----------

    private fun bindHeader() {
        ViewCompat.setAccessibilityHeading(binding.logSwimTitle, true)
        binding.logSwimBack.setOnClickListener { findNavController().navigateUp() }
    }

    /** TalkBack reads "2:09 /100 m" and "Distance, 1,500 m" as one item each. */
    private fun bindAccessibility() {
        listOf(
            binding.logSwimPaceRow,
            binding.logSwimDistanceTile,
            binding.logSwimCaloriesTile,
        ).forEach { ViewCompat.setScreenReaderFocusable(it, true) }
    }

    private fun bindInputs() {
        binding.logSwimDateCard.setOnClickListener {
            LogDateTimePickers.showDatePicker(this, viewModel.uiState.value.date, ::onDatePicked)
        }
        binding.logSwimTimeCard.setOnClickListener {
            LogDateTimePickers.showTimePicker(this, viewModel.uiState.value.time, ::onTimePicked)
        }
        // A picker that was open when the screen was rebuilt still answers.
        LogDateTimePickers.reattach(this, ::onDatePicked, ::onTimePicked)

        binding.logSwimPoolControl.contentDescription = getString(R.string.log_swim_pool_length)
        binding.logSwimPoolControl.setOptions(
            LogSwimCalculator.POOL_LENGTHS_M.map { getString(R.string.log_swim_pool_option, it) },
            selected = viewModel.uiState.value.poolIndex,
        )
        binding.logSwimPoolControl.setOnSegmentSelectedListener { index ->
            LogSwimCalculator.POOL_LENGTHS_M.getOrNull(index)?.let(viewModel::setPoolLength)
        }

        binding.logSwimLapsStepper.setOnStepListener { direction ->
            viewModel.stepLaps(direction)
            bindingOrNull?.logSwimLapsError?.isVisible = false
        }

        binding.logSwimDuration.doOnDurationChanged {
            val b = bindingOrNull ?: return@doOnDurationChanged
            viewModel.setDuration(b.logSwimDuration.hours, b.logSwimDuration.minutes, b.logSwimDuration.seconds)
            b.logSwimDurationError.isVisible = false
        }

        binding.logSwimSaveButton.setOnClickListener { attemptSave() }
    }

    private fun onDatePicked(date: LocalDate) {
        viewModel.setDate(date)
        bindingOrNull?.logSwimDateError?.isVisible = false
    }

    private fun onTimePicked(time: LocalTime) {
        viewModel.setTime(time)
        bindingOrNull?.logSwimDateError?.isVisible = false
    }

    // ---------- rendering ----------

    private fun render(state: LogSwimUiState) {
        val b = bindingOrNull ?: return
        val summary = state.summary

        b.logSwimPaceValue.text = LogSwimCalculator.paceText(summary.pacePer100mSeconds)
        b.logSwimDistanceValue.text = getString(
            R.string.measure_metres,
            Formatters.formatMetresValue(summary.distanceMeters.toDouble()),
        )
        b.logSwimCaloriesValue.text = getString(
            R.string.log_swim_kcal_value,
            Formatters.formatKcalValue(summary.kcal),
        )

        b.logSwimDateValue.text = LogSwimCalculator.dateText(
            text = text,
            date = state.date,
            today = LocalDate.now(ZoneId.systemDefault()),
        )
        b.logSwimTimeValue.text = state.time.format(LogDateTimePickers.timeFormatter(requireContext()))

        if (b.logSwimPoolControl.selectedIndex != state.poolIndex) {
            b.logSwimPoolControl.setSelectedIndex(state.poolIndex)
        }

        b.logSwimLapsHint.text = LogSwimCalculator.lapsHint(text, state.laps, state.poolLengthM)
        b.logSwimLapsStepper.valueText = state.laps.toString()
        b.logSwimLapsStepper.setStepEnabled(
            canDecrement = state.canRemoveLap,
            canIncrement = state.canAddLap,
        )
    }

    // ---------- save ----------

    private fun attemptSave() {
        val b = bindingOrNull ?: return
        viewModel.save(
            hours = b.logSwimDuration.hours,
            minutes = b.logSwimDuration.minutes,
            seconds = b.logSwimDuration.seconds,
            notes = b.logSwimNotesInput.text?.toString().orEmpty(),
        )
    }

    private fun showValidation(errors: List<LogSwimViewModel.FieldError>) {
        val b = bindingOrNull ?: return
        val targets: Map<LogSwimViewModel.Field, TextView> = mapOf(
            LogSwimViewModel.Field.DATE to b.logSwimDateError,
            LogSwimViewModel.Field.LAPS to b.logSwimLapsError,
            LogSwimViewModel.Field.DURATION to b.logSwimDurationError,
        )
        targets.values.forEach { it.isVisible = false }
        errors.forEach { err ->
            targets[err.field]?.apply {
                text = getString(err.messageRes)
                isVisible = true
            }
        }
        // Save is pinned, so the first error may be scrolled out of view.
        val first = LogSwimViewModel.Field.entries
            .firstOrNull { field -> errors.any { it.field == field } }
            ?: return
        b.logSwimScroll.post { bindingOrNull?.let { scrollToError(it, first) } }
    }

    /**
     * Scrolls the column so [field]'s section and its error message are
     * in view (only when they aren't already).
     */
    private fun scrollToError(b: FragmentLogSwimBinding, field: LogSwimViewModel.Field) {
        // Direct children of the scrolling column, so their tops are scroll positions.
        val (section: View, error: View) = when (field) {
            LogSwimViewModel.Field.DATE ->
                (b.logSwimDateCard.parent as? View ?: return) to b.logSwimDateError
            LogSwimViewModel.Field.LAPS -> b.logSwimLapsCard to b.logSwimLapsError
            LogSwimViewModel.Field.DURATION -> b.logSwimDuration to b.logSwimDurationError
        }
        val scroll = b.logSwimScroll
        val visibleTop = scroll.scrollY
        val visibleBottom = visibleTop + scroll.height
        if (section.top >= visibleTop && error.bottom <= visibleBottom) return
        // Leave room for the section label above the field.
        val labelRoom = 2 * resources.getDimensionPixelSize(R.dimen.log_swim_gap)
        scroll.smoothScrollTo(0, (section.top - labelRoom).coerceAtLeast(0))
    }

    private fun onSaved(clientUuid: String) {
        // Safe against a second Saved event after the screen has already
        // moved on (the action only exists on this destination).
        findNavController().navigateSafe(
            R.id.action_log_swim_to_detail,
            bundleOf(WorkoutDetailFragment.ARG_CLIENT_UUID to clientUuid),
        )
    }

    private fun showSaveRetry(messageRes: Int) {
        snackbar(messageRes, anchor = binding.logSwimSaveBar, kind = SnackKind.ERROR)
            .setAction(R.string.log_error_save_retry) { attemptSave() }
            .show()
    }
}
