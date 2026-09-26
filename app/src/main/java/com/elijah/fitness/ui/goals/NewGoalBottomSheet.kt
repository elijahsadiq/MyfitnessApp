package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalPeriodResolver
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.databinding.BottomSheetNewGoalBinding
import com.elijah.fitness.ui.auth.FieldError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "New goal" bottom sheet (phase 7 step 5). Doubles as the editor —
 * pass the id of the goal being edited via [newInstance] to seed the
 * fields; the submit button switches to "Save changes".
 */
class NewGoalBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNewGoalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewGoalViewModel by viewModels {
        val editingId = arguments?.getString(ARG_GOAL_ID)
        NewGoalViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container,
            editingGoalId = editingId,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetNewGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ------- input wiring -------

        binding.activityChips.setOnCheckedStateChangeListener { _, checked ->
            val id = checked.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val activity = when (id) {
                R.id.chipActivityRunning -> ActivityType.RUNNING
                R.id.chipActivityCycling -> ActivityType.CYCLING
                R.id.chipActivityWeightlifting -> ActivityType.WEIGHTLIFTING
                R.id.chipActivityAny -> null
                else -> return@setOnCheckedStateChangeListener
            }
            viewModel.setActivity(activity)
        }

        binding.goalTypeChips.setOnCheckedStateChangeListener { _, checked ->
            val id = checked.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val type = when (id) {
                R.id.chipTypeDistance -> GoalType.DISTANCE
                R.id.chipTypeCount -> GoalType.COUNT
                R.id.chipTypeDuration -> GoalType.DURATION
                R.id.chipTypeVolume -> GoalType.VOLUME
                else -> return@setOnCheckedStateChangeListener
            }
            viewModel.setGoalType(type)
        }

        binding.periodChips.setOnCheckedStateChangeListener { _, checked ->
            val id = checked.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val period = if (id == R.id.chipPeriodMonth) GoalPeriod.THIS_MONTH
            else GoalPeriod.THIS_WEEK
            viewModel.setPeriod(period)
        }

        binding.targetInput.doAfterTextChanged {
            val raw = it?.toString().orEmpty()
            if (raw != viewModel.uiState.value.targetRaw) {
                viewModel.setTarget(raw)
            }
        }

        binding.titleInput.doAfterTextChanged {
            val text = it?.toString().orEmpty()
            if (text != viewModel.uiState.value.title) {
                viewModel.setTitle(text)
            }
        }

        binding.submitButton.setOnClickListener { viewModel.submit() }

        // ------- state -> view -------

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                NewGoalViewModel.Event.Saved -> {
                    setFragmentResult(FRAGMENT_RESULT_KEY, Bundle().apply {
                        putBoolean(RESULT_SAVED_KEY, true)
                    })
                    dismiss()
                }
                is NewGoalViewModel.Event.ShowError -> {
                    Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- rendering ----------

    private fun render(state: NewGoalViewModel.UiState) {
        // Title + submit label switch between create / edit modes.
        binding.newGoalTitle.setText(
            if (state.editingGoalId != null) R.string.new_goal_title_edit
            else R.string.new_goal_title_create
        )
        binding.submitButton.text = getString(
            if (state.editingGoalId != null) R.string.new_goal_submit_edit
            else R.string.new_goal_submit_create
        )

        // Progress / disabled state on submit.
        binding.submitButton.isEnabled = state.isValid && !state.submitting
        binding.submitProgress.isVisible = state.submitting
        binding.submitButton.text = when {
            state.submitting -> ""
            state.editingGoalId != null -> getString(R.string.new_goal_submit_edit)
            else -> getString(R.string.new_goal_submit_create)
        }

        // Activity chip selection (skip if listener would fire same value).
        syncChip(binding.chipActivityRunning, state.activityType == ActivityType.RUNNING && state.activityChosen)
        syncChip(binding.chipActivityCycling, state.activityType == ActivityType.CYCLING && state.activityChosen)
        syncChip(binding.chipActivityWeightlifting,
            state.activityType == ActivityType.WEIGHTLIFTING && state.activityChosen)
        syncChip(binding.chipActivityAny, state.activityType == null && state.activityChosen)

        // Filter goal-type chips based on activity.
        val available = state.availableGoalTypes.toSet()
        binding.chipTypeDistance.isVisible = GoalType.DISTANCE in available
        binding.chipTypeCount.isVisible = GoalType.COUNT in available
        binding.chipTypeDuration.isVisible = GoalType.DURATION in available
        binding.chipTypeVolume.isVisible = GoalType.VOLUME in available

        syncChip(binding.chipTypeDistance, state.goalType == GoalType.DISTANCE)
        syncChip(binding.chipTypeCount, state.goalType == GoalType.COUNT)
        syncChip(binding.chipTypeDuration, state.goalType == GoalType.DURATION)
        syncChip(binding.chipTypeVolume, state.goalType == GoalType.VOLUME)

        syncChip(binding.chipPeriodWeek, state.period == GoalPeriod.THIS_WEEK)
        syncChip(binding.chipPeriodMonth, state.period == GoalPeriod.THIS_MONTH)

        // Suffix tracks the picked goal type.
        binding.targetFieldLayout.suffixText = getString(unitLabelResFor(state.goalType))

        // Sync text fields without re-triggering doAfterTextChanged loops.
        if (binding.targetInput.text?.toString().orEmpty() != state.targetRaw) {
            binding.targetInput.setText(state.targetRaw)
            binding.targetInput.setSelection(state.targetRaw.length)
        }
        if (binding.titleInput.text?.toString().orEmpty() != state.title) {
            binding.titleInput.setText(state.title)
            binding.titleInput.setSelection(state.title.length)
        }

        binding.targetFieldLayout.error = errorText(state.targetError)
        binding.titleFieldLayout.error = errorText(state.titleError)

        binding.previewLine.text = previewLineFor(state)
    }

    private fun syncChip(chip: Chip, shouldBeChecked: Boolean) {
        if (chip.isChecked != shouldBeChecked) chip.isChecked = shouldBeChecked
    }

    private fun errorText(fieldError: FieldError?): CharSequence? = when (fieldError) {
        null -> null
        is FieldError.Res -> getString(fieldError.messageRes)
        is FieldError.Text -> fieldError.message
    }

    private fun unitLabelResFor(type: GoalType?): Int = when (type) {
        GoalType.DISTANCE -> R.string.goal_unit_km
        GoalType.COUNT -> R.string.goal_unit_workouts
        GoalType.DURATION -> R.string.goal_unit_min
        GoalType.VOLUME -> R.string.goal_unit_kg
        null -> R.string.goal_unit_km
    }

    private fun previewLineFor(state: NewGoalViewModel.UiState): String? {
        if (!state.isValid) return null
        val zone = ZoneId.systemDefault()
        val bounds = GoalPeriodResolver.bounds(state.period, zone)
        val start = LocalDate.ofInstant(bounds.start, zone)
        // endExclusive is the day AFTER the goal window — for the preview
        // we display the last valid day (start of endExclusive - 1 day).
        val displayEnd = LocalDate.ofInstant(bounds.endExclusive, zone).minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("d MMMM")
        return getString(
            R.string.new_goal_preview,
            state.title,
            start.format(formatter),
            displayEnd.format(formatter),
        )
    }

    /**
     * Lifecycle-safe Flow collector — the sheet doesn't extend
     * `BaseFragment` (that class only supports `Fragment`, not
     * `BottomSheetDialogFragment`), so we inline the equivalent here.
     */
    private fun <T> collectOnStart(flow: Flow<T>, block: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(block)
            }
        }
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "new_goal_result"
        const val RESULT_SAVED_KEY = "saved"

        private const val ARG_GOAL_ID = "goal_id"

        fun forCreate(): NewGoalBottomSheet = NewGoalBottomSheet()

        fun forEdit(goalId: String): NewGoalBottomSheet = NewGoalBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
        }
    }
}
