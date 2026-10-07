package com.elijah.fitness.ui.goals

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.databinding.FragmentNewGoalBinding
import com.elijah.fitness.databinding.ViewGoalActivityTileBinding
import com.elijah.fitness.databinding.ViewGoalMetricPillBinding
import com.elijah.fitness.ui.auth.FieldError
import com.elijah.fitness.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen New Goal / Edit Goal destination (phase 8b step 6).
 *
 * ### Layout parts
 * - A header strip with a close button and the "New goal" / "Edit goal"
 *   title.
 * - Four sections in a `NestedScrollView`: activity grid, tracking-option
 *   row, target stepper, days stepper, dark summary card.
 * - A lime pill button pinned to the bottom that submits.
 *
 * ### Steppers
 * Plus / minus each wire a `HoldToRepeat` touch listener (tap = single
 * step, long-press = accelerating repeat). Tapping the number itself
 * opens a numeric `AlertDialog` so the user can type a value directly.
 *
 * ### Navigation
 * Pass `goalId` to [newInstance] when editing — the ViewModel seeds the
 * form from the stored goal and keeps the original start date on save.
 */
class NewGoalFragment : BaseFragment<FragmentNewGoalBinding>() {

    private val viewModel: NewGoalViewModel by viewModels {
        NewGoalViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container,
            editingGoalId = arguments?.getString(ARG_GOAL_ID),
        )
    }

    private val runTile by lazy { ViewGoalActivityTileBinding.bind(binding.newGoalActivityRun.root) }
    private val rideTile by lazy { ViewGoalActivityTileBinding.bind(binding.newGoalActivityRide.root) }
    private val liftTile by lazy { ViewGoalActivityTileBinding.bind(binding.newGoalActivityLift.root) }
    private val anyTile by lazy { ViewGoalActivityTileBinding.bind(binding.newGoalActivityAny.root) }

    private val metricSlots: List<ViewGoalMetricPillBinding> by lazy {
        listOf(
            ViewGoalMetricPillBinding.bind(binding.newGoalMetric0.root),
            ViewGoalMetricPillBinding.bind(binding.newGoalMetric1.root),
            ViewGoalMetricPillBinding.bind(binding.newGoalMetric2.root),
        )
    }

    private val holdHandlers = mutableListOf<HoldToRepeat>()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentNewGoalBinding =
        FragmentNewGoalBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applySystemBarInsets(target = binding.root, applyBottom = false)
        applyBottomNavInset(binding.newGoalSubmitContainer)

        binding.newGoalCloseButton.setOnClickListener { findNavController().popBackStack() }

        wireActivityTiles()
        wireStepperControls()
        binding.newGoalSubmit.setOnClickListener { viewModel.submit() }

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                NewGoalViewModel.Event.Saved -> {
                    setFragmentResult(
                        FRAGMENT_RESULT_KEY,
                        Bundle().apply { putBoolean(RESULT_SAVED_KEY, true) },
                    )
                    findNavController().popBackStack()
                }
                is NewGoalViewModel.Event.ShowError -> {
                    Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        holdHandlers.forEach { it.stop() }
        holdHandlers.clear()
        super.onDestroyView()
    }

    // ---------------------------------------------------------------------
    // Wiring
    // ---------------------------------------------------------------------

    private fun wireActivityTiles() {
        bindActivityTile(runTile, R.drawable.ic_run_24, R.string.new_goal_activity_running,
            NewGoalViewModel.ActivityOption.RUN)
        bindActivityTile(rideTile, R.drawable.ic_bike_24, R.string.new_goal_activity_cycling,
            NewGoalViewModel.ActivityOption.RIDE)
        bindActivityTile(liftTile, R.drawable.ic_dumbbell_24, R.string.new_goal_activity_weightlifting,
            NewGoalViewModel.ActivityOption.LIFT)
        bindActivityTile(anyTile, R.drawable.ic_flag_24, R.string.new_goal_activity_any,
            NewGoalViewModel.ActivityOption.ANY)
    }

    private fun bindActivityTile(
        tile: ViewGoalActivityTileBinding,
        iconRes: Int,
        labelRes: Int,
        option: NewGoalViewModel.ActivityOption,
    ) {
        tile.goalActivityTileIcon.setImageResource(iconRes)
        tile.goalActivityTileLabel.setText(labelRes)
        tile.goalActivityTileRoot.setOnClickListener { viewModel.setActivity(option) }
    }

    private fun wireStepperControls() {
        holdHandlers += HoldToRepeat(binding.newGoalTargetMinus, viewModel::decrementTarget)
        holdHandlers += HoldToRepeat(binding.newGoalTargetPlus, viewModel::incrementTarget)
        holdHandlers += HoldToRepeat(binding.newGoalDaysMinus, viewModel::decrementDays)
        holdHandlers += HoldToRepeat(binding.newGoalDaysPlus, viewModel::incrementDays)

        binding.newGoalTargetValue.setOnClickListener { showTargetInputDialog() }
        binding.newGoalDaysValue.setOnClickListener { showDaysInputDialog() }
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private fun render(state: NewGoalViewModel.UiState) {
        // Title + submit label switch between create / edit modes.
        binding.newGoalTitle.setText(
            if (state.editingGoalId != null) R.string.new_goal_title_edit
            else R.string.new_goal_title_create
        )
        val submitLabelRes =
            if (state.editingGoalId != null) R.string.new_goal_submit_edit
            else R.string.new_goal_submit_create
        binding.newGoalSubmit.text = if (state.submitting) "" else getString(submitLabelRes)
        binding.newGoalSubmit.isEnabled = !state.submitting
        binding.newGoalSubmit.icon = if (state.submitting) null
            else androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_24)
        binding.newGoalSubmitProgress.isVisible = state.submitting

        // Activity tiles
        runTile.goalActivityTileRoot.isSelected = state.activity == NewGoalViewModel.ActivityOption.RUN
        rideTile.goalActivityTileRoot.isSelected = state.activity == NewGoalViewModel.ActivityOption.RIDE
        liftTile.goalActivityTileRoot.isSelected = state.activity == NewGoalViewModel.ActivityOption.LIFT
        anyTile.goalActivityTileRoot.isSelected = state.activity == NewGoalViewModel.ActivityOption.ANY

        // Metric row — hide the third slot (and its spacer) when "Any".
        bindMetricRow(state)

        // Hint beneath the metric heading.
        binding.newGoalMetricHint.setText(
            when (state.activity) {
                NewGoalViewModel.ActivityOption.RUN -> R.string.new_goal_metric_hint_run
                NewGoalViewModel.ActivityOption.RIDE -> R.string.new_goal_metric_hint_ride
                NewGoalViewModel.ActivityOption.LIFT -> R.string.new_goal_metric_hint_lift
                NewGoalViewModel.ActivityOption.ANY -> R.string.new_goal_metric_hint_any
            }
        )

        // Target stepper
        binding.newGoalTargetNumber.text = formatInt(state.target)
        binding.newGoalTargetUnit.text = targetUnitLabel(state)
        bindError(binding.newGoalTargetError, state.targetError)

        // Days stepper
        binding.newGoalDaysNumber.text = formatInt(state.days)
        binding.newGoalDaysUnit.text = resources.getQuantityString(
            R.plurals.new_goal_days_unit, state.days
        )
        binding.newGoalDateRange.text = getString(
            R.string.new_goal_date_range, formatDate(endsOn(state))
        )
        bindError(binding.newGoalDaysError, state.daysError)

        // Summary
        binding.newGoalSummaryTitle.text = summaryTitleFor(state)
        binding.newGoalPaceLabel.setText(
            if (state.metric == GoalType.COUNT) R.string.new_goal_pace_per_week
            else R.string.new_goal_pace_per_day
        )
        binding.newGoalPaceValue.text = paceValueFor(state)
        binding.newGoalDeadlineValue.text = formatDate(endsOn(state))
    }

    private fun bindMetricRow(state: NewGoalViewModel.UiState) {
        val allowed = state.allowedMetrics
        metricSlots.forEachIndexed { idx, slot ->
            val metric = allowed.getOrNull(idx)
            if (metric == null) {
                slot.goalMetricPillRoot.visibility = View.GONE
                slot.goalMetricPillRoot.setOnClickListener(null)
            } else {
                slot.goalMetricPillRoot.visibility = View.VISIBLE
                slot.goalMetricPillLabel.setText(metricLabelRes(metric))
                slot.goalMetricPillUnit.text = metricUnitLabel(metric, state)
                slot.goalMetricPillRoot.isSelected = metric == state.metric
                slot.goalMetricPillRoot.setOnClickListener { viewModel.setMetric(metric) }
            }
        }
        binding.newGoalMetricSpacer2.visibility =
            if (allowed.size >= 3) View.VISIBLE else View.GONE
    }

    private fun bindError(view: android.widget.TextView, error: FieldError?) {
        if (error == null) {
            view.visibility = View.GONE
            view.text = null
        } else {
            view.visibility = View.VISIBLE
            view.text = when (error) {
                is FieldError.Res -> getString(error.messageRes)
                is FieldError.Text -> error.message
            }
        }
    }

    // ---------------------------------------------------------------------
    // Input dialogs
    // ---------------------------------------------------------------------

    private fun showTargetInputDialog() {
        val state = viewModel.uiState.value
        val spec = NewGoalViewModel.metricSpec(state.metric)
        val outOfRangeRes = outOfRangeMessageFor(state.metric)
        showNumberDialog(
            titleRes = R.string.new_goal_target_dialog_title,
            initial = state.target,
            min = spec.min,
            max = spec.max,
            outOfRangeRes = outOfRangeRes,
            onPicked = viewModel::setTarget,
        )
    }

    private fun showDaysInputDialog() {
        val state = viewModel.uiState.value
        showNumberDialog(
            titleRes = R.string.new_goal_days_dialog_title,
            initial = state.days,
            min = NewGoalViewModel.DAYS_MIN,
            max = NewGoalViewModel.DAYS_MAX,
            outOfRangeRes = R.string.new_goal_error_days_out_of_range,
            onPicked = viewModel::setDays,
        )
    }

    private fun showNumberDialog(
        @StringRes titleRes: Int,
        initial: Int,
        min: Int,
        max: Int,
        @StringRes outOfRangeRes: Int,
        onPicked: (Int) -> Unit,
    ) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(max.toString().length))
            setText(initial.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.new_goal_dialog_ok) { dialog, _ ->
                val raw = input.text.toString().toIntOrNull()
                if (raw == null || raw < min || raw > max) {
                    Snackbar.make(binding.root, outOfRangeRes, Snackbar.LENGTH_SHORT).show()
                } else {
                    onPicked(raw)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.new_goal_dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ---------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------

    private fun targetUnitLabel(state: NewGoalViewModel.UiState): String = when (state.metric) {
        GoalType.DISTANCE -> getString(R.string.new_goal_metric_unit_distance)
        GoalType.DURATION -> getString(R.string.new_goal_metric_unit_time)
        GoalType.VOLUME -> getString(R.string.new_goal_metric_unit_weight)
        GoalType.COUNT -> metricUnitLabel(GoalType.COUNT, state)
    }

    private fun metricLabelRes(metric: GoalType): Int = when (metric) {
        GoalType.DISTANCE -> R.string.new_goal_metric_distance
        GoalType.DURATION -> R.string.new_goal_metric_time
        GoalType.COUNT -> R.string.new_goal_metric_sessions
        GoalType.VOLUME -> R.string.new_goal_metric_weight
    }

    private fun metricUnitLabel(metric: GoalType, state: NewGoalViewModel.UiState): String =
        when (metric) {
            GoalType.DISTANCE -> getString(R.string.new_goal_metric_unit_distance)
            GoalType.DURATION -> getString(R.string.new_goal_metric_unit_time)
            GoalType.VOLUME -> getString(R.string.new_goal_metric_unit_weight)
            GoalType.COUNT -> sessionUnit(state)
        }

    private fun sessionUnit(state: NewGoalViewModel.UiState): String {
        // Phase-8b: for the metric row and target unit the plural form is
        // the one that matches the current target. On the main pill row
        // the design always uses the plural, but target=1 shows "1 run" so
        // the unit is "run"; target>1 shows "4 runs" with unit "runs".
        val plural = resources.getQuantityString(
            when (state.activity) {
                NewGoalViewModel.ActivityOption.RUN -> R.plurals.new_goal_metric_unit_sessions_run
                NewGoalViewModel.ActivityOption.RIDE -> R.plurals.new_goal_metric_unit_sessions_ride
                NewGoalViewModel.ActivityOption.LIFT -> R.plurals.new_goal_metric_unit_sessions_lift
                NewGoalViewModel.ActivityOption.ANY -> R.plurals.new_goal_metric_unit_sessions_any
            },
            state.target,
        )
        return plural
    }

    private fun summaryTitleFor(state: NewGoalViewModel.UiState): String =
        autoTitle(state)

    private fun paceValueFor(state: NewGoalViewModel.UiState): String {
        val t = state.target
        val d = state.days.coerceAtLeast(1)
        return when (state.metric) {
            GoalType.DISTANCE ->
                getString(R.string.new_goal_pace_value_distance,
                    String.format(Locale.getDefault(), "%.1f", t.toDouble() / d))
            GoalType.DURATION ->
                getString(R.string.new_goal_pace_value_time,
                    kotlin.math.max(1, kotlin.math.round(t.toDouble() / d).toInt()))
            GoalType.VOLUME ->
                getString(R.string.new_goal_pace_value_volume,
                    formatInt(kotlin.math.round(t.toDouble() / d).toInt()))
            GoalType.COUNT -> {
                // Sessions pace shown per week.
                val perWeek = String.format(Locale.getDefault(), "%.1f", t.toDouble() * 7.0 / d)
                val template = when (state.activity) {
                    NewGoalViewModel.ActivityOption.RUN -> R.string.new_goal_pace_value_sessions_run
                    NewGoalViewModel.ActivityOption.RIDE -> R.string.new_goal_pace_value_sessions_ride
                    NewGoalViewModel.ActivityOption.LIFT -> R.string.new_goal_pace_value_sessions_lift
                    NewGoalViewModel.ActivityOption.ANY -> R.string.new_goal_pace_value_sessions_any
                }
                getString(template, perWeek)
            }
        }
    }

    private fun endsOn(state: NewGoalViewModel.UiState): LocalDate {
        val start = state.startDate ?: LocalDate.now(ZoneId.systemDefault())
        return start.plusDays((state.days - 1).toLong())
    }

    private fun formatInt(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.toLong())

    private fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

    private fun outOfRangeMessageFor(type: GoalType): Int = when (type) {
        GoalType.DISTANCE -> R.string.new_goal_error_target_out_of_range_distance
        GoalType.COUNT -> R.string.new_goal_error_target_out_of_range_count
        GoalType.DURATION -> R.string.new_goal_error_target_out_of_range_duration
        GoalType.VOLUME -> R.string.new_goal_error_target_out_of_range_volume
    }

    // ---------------------------------------------------------------------
    // Hold-to-repeat stepper
    // ---------------------------------------------------------------------

    /**
     * Fires [onStep] on tap, then — if the user keeps the button pressed
     * past [initialDelayMs] — fires it again every [intervalMs], halving
     * the interval twice to accelerate on long holds. Posts to the main
     * thread via a shared [Handler]; stops on `ACTION_UP` / `ACTION_CANCEL`.
     *
     * One per button; keep a reference so onDestroyView can stop any
     * runaway runnable before the fragment is torn down.
     */
    private class HoldToRepeat(
        private val view: View,
        private val onStep: () -> Unit,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var currentInterval = INITIAL_INTERVAL_MS
        private var holdCount = 0

        private val runnable: Runnable = object : Runnable {
            override fun run() {
                onStep()
                holdCount++
                if (holdCount == ACCELERATION_STEP_1) currentInterval = INTERVAL_MS_FAST
                if (holdCount == ACCELERATION_STEP_2) currentInterval = INTERVAL_MS_FASTER
                handler.postDelayed(this, currentInterval)
            }
        }

        init {
            @Suppress("ClickableViewAccessibility")
            view.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onStep()
                        currentInterval = INITIAL_INTERVAL_MS
                        holdCount = 0
                        handler.postDelayed(runnable, HOLD_THRESHOLD_MS)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.performClick() // for accessibility
                        handler.removeCallbacks(runnable)
                        true
                    }
                    else -> false
                }
            }
        }

        fun stop() = handler.removeCallbacks(runnable)

        companion object {
            // First repeat kicks in after a long press; subsequent repeats
            // accelerate so holding "+" on the Weight stepper (step=1000)
            // can race from 1,000 → 500,000 without the user tapping 500
            // times.
            private const val HOLD_THRESHOLD_MS = 350L
            private const val INITIAL_INTERVAL_MS = 160L
            private const val INTERVAL_MS_FAST = 80L
            private const val INTERVAL_MS_FASTER = 35L
            private const val ACCELERATION_STEP_1 = 6
            private const val ACCELERATION_STEP_2 = 14
        }
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "new_goal_result"
        const val RESULT_SAVED_KEY = "saved"

        internal const val ARG_GOAL_ID = "goal_id"

        /** Factory + argument plumbing so the fragment is driver-agnostic. */
        fun argsFor(goalId: String?): Bundle = Bundle().apply {
            goalId?.let { putString(ARG_GOAL_ID, it) }
        }
    }
}

