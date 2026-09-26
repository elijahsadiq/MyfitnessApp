package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalPeriodResolver
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.ui.auth.FieldError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * State holder for the New Goal bottom sheet. Handles both **create**
 * and **edit** modes — the same sheet opens pre-filled if
 * [editingGoalId] is non-null.
 *
 * ### Chip-driven form
 * Activity + Goal type + Period are chips (single selection); the target
 * is a decimal input; the title is a text input that auto-fills from the
 * choices but stays user-editable. The form validates as the user types
 * and disables the submit button until everything is legal.
 *
 * ### Available goal types depend on activity
 * The phase-7 brief mandates:
 *   - `WEIGHTLIFTING` ⇒ {Count, Duration, Volume}
 *   - `RUNNING` / `CYCLING` ⇒ {Distance, Count, Duration}
 *   - `Any` (null) ⇒ {Distance, Count, Duration, Volume}
 * The fragment reads [UiState.availableGoalTypes] and hides the chips
 * that aren't legal for the current activity choice.
 */
class NewGoalViewModel(
    private val repository: GoalRepository,
    editingGoalId: String? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    data class UiState(
        /** Non-null when the sheet was opened to edit an existing goal. */
        val editingGoalId: String? = null,
        val activityType: ActivityType? = null,
        val activityChosen: Boolean = false,     // distinguishes "user picked Any" from "no pick yet"
        val goalType: GoalType? = null,
        val period: GoalPeriod = GoalPeriod.THIS_WEEK,
        val targetRaw: String = "",
        val title: String = "",
        val titleUserEdited: Boolean = false,
        val submitting: Boolean = false,
        val activityError: FieldError? = null,
        val goalTypeError: FieldError? = null,
        val targetError: FieldError? = null,
        val titleError: FieldError? = null,
    ) {
        /** Goal types that make sense for the current [activityType]. */
        val availableGoalTypes: List<GoalType>
            get() = when (activityType) {
                ActivityType.WEIGHTLIFTING ->
                    listOf(GoalType.COUNT, GoalType.DURATION, GoalType.VOLUME)
                ActivityType.RUNNING, ActivityType.CYCLING ->
                    listOf(GoalType.DISTANCE, GoalType.COUNT, GoalType.DURATION)
                null -> GoalType.entries
            }

        /** True when every field is filled with something legal. */
        val isValid: Boolean
            get() = activityChosen && goalType != null &&
                targetRaw.toDoubleOrNull()?.let { it > 0.0 && it <= maxTargetFor(goalType) } == true &&
                title.isNotBlank() &&
                activityError == null && goalTypeError == null &&
                targetError == null && titleError == null
    }

    sealed interface Event {
        data object Saved : Event
        data class ShowError(@StringRes val messageRes: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState(editingGoalId = editingGoalId))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        // Load existing goal for edit mode.
        if (editingGoalId != null) {
            viewModelScope.launch {
                val goal = repository.observeGoals().first()
                    .firstOrNull { it.id == editingGoalId } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    activityType = goal.activityType,
                    activityChosen = true,
                    goalType = goal.goalType(),
                    period = goal.period,
                    targetRaw = formatTargetForEdit(goal),
                    title = goal.title,
                    titleUserEdited = true, // pre-filled title is treated as user-edited
                )
            }
        }
    }

    // ---- Setters wired to the chips + text inputs ------------------------

    fun setActivity(type: ActivityType?) {
        _uiState.update { s ->
            val nextGoalType = s.goalType.takeIf { it in availableGoalTypes(type) }
            val next = s.copy(
                activityType = type,
                activityChosen = true,
                goalType = nextGoalType,
                activityError = null,
            )
            // Refresh auto-title if the user hasn't manually typed one.
            if (!s.titleUserEdited) next.copy(title = autoTitle(next)) else next
        }
    }

    fun setGoalType(type: GoalType) {
        _uiState.update { s ->
            val next = s.copy(goalType = type, goalTypeError = null)
            if (!s.titleUserEdited) next.copy(title = autoTitle(next)) else next
        }
    }

    fun setPeriod(period: GoalPeriod) {
        _uiState.update { s ->
            val next = s.copy(period = period)
            if (!s.titleUserEdited) next.copy(title = autoTitle(next)) else next
        }
    }

    fun setTarget(raw: String) {
        _uiState.update { s ->
            val next = s.copy(targetRaw = raw, targetError = null)
            if (!s.titleUserEdited) next.copy(title = autoTitle(next)) else next
        }
    }

    /** Called from `doAfterTextChanged` on the title input. */
    fun setTitle(text: String) {
        _uiState.update {
            it.copy(title = text, titleUserEdited = true, titleError = null)
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.submitting) return
        val validated = validate(s)
        if (validated != null) {
            _uiState.value = validated
            return
        }

        val target = s.targetRaw.trim().toDouble()
        val (periodStart, periodEnd) = resolvePeriodDates(s.period)
        val goalType = s.goalType!!
        val editingId = s.editingGoalId

        _uiState.value = s.copy(submitting = true)
        viewModelScope.launch {
            val result = if (editingId != null) {
                repository.updateGoal(
                    id = editingId,
                    title = s.title.trim(),
                    activityType = s.activityType,
                    targetValue = target,
                )
            } else {
                repository.createGoal(
                    title = s.title.trim(),
                    activityType = s.activityType,
                    goalType = goalType,
                    period = s.period,
                    targetValue = target,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = s.copy(submitting = false)
                    _events.send(Event.Saved)
                }
                ApiResult.NetworkError -> {
                    _uiState.value = s.copy(submitting = false)
                    _events.send(Event.ShowError(R.string.auth_error_network))
                }
                is ApiResult.Error -> handleServerError(result)
            }
        }
    }

    private suspend fun handleServerError(result: ApiResult.Error) {
        val f = result.fieldErrors
        val activityErr = f["activityType"]?.let(FieldError::Text)
        val targetErr = f["targetValue"]?.let(FieldError::Text)
        val titleErr = f["title"]?.let(FieldError::Text)
        val periodErr = f["periodEnd"]?.let(FieldError::Text)

        if (activityErr != null || targetErr != null || titleErr != null || periodErr != null) {
            _uiState.value = _uiState.value.copy(
                submitting = false,
                activityError = activityErr,
                targetError = targetErr ?: periodErr, // periodEnd → target row is close enough
                titleError = titleErr,
            )
        } else {
            _uiState.value = _uiState.value.copy(submitting = false)
            _events.send(Event.ShowError(R.string.auth_error_generic))
        }
    }

    private fun validate(s: UiState): UiState? {
        val activityErr = if (!s.activityChosen)
            FieldError.Res(R.string.new_goal_error_activity_required) else null
        val goalTypeErr = if (s.goalType == null)
            FieldError.Res(R.string.new_goal_error_type_required) else null
        val targetErr = validateTarget(s.targetRaw, s.goalType)
        val titleErr = if (s.title.isBlank())
            FieldError.Res(R.string.new_goal_error_title_required) else null
        return if (activityErr != null || goalTypeErr != null ||
            targetErr != null || titleErr != null) {
            s.copy(
                activityError = activityErr,
                goalTypeError = goalTypeErr,
                targetError = targetErr,
                titleError = titleErr,
            )
        } else null
    }

    private fun validateTarget(raw: String, goalType: GoalType?): FieldError? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return FieldError.Res(R.string.new_goal_error_target_required)
        val value = trimmed.toDoubleOrNull()
            ?: return FieldError.Res(R.string.new_goal_error_target_invalid)
        if (goalType == null) return null // covered by goalType error
        val max = maxTargetFor(goalType)
        return if (value > 0.0 && value <= max) null
        else FieldError.Res(outOfRangeMessageFor(goalType))
    }

    private fun resolvePeriodDates(period: GoalPeriod): Pair<LocalDate, LocalDate> {
        val bounds = GoalPeriodResolver.bounds(period, zone, clock)
        val start = LocalDate.ofInstant(bounds.start, zone)
        // endExclusive is the day *after* the last valid day; the server
        // stores DATE strings, so we send the exclusive boundary as-is.
        val end = LocalDate.ofInstant(bounds.endExclusive, zone)
        return start to end
    }

    private fun availableGoalTypes(activityType: ActivityType?): List<GoalType> =
        UiState(activityType = activityType, activityChosen = true).availableGoalTypes

    companion object {
        /** Sane upper bounds mirroring the server. */
        fun maxTargetFor(goalType: GoalType?): Double = when (goalType) {
            GoalType.DISTANCE -> 1_000.0
            GoalType.COUNT -> 1_000.0
            GoalType.DURATION -> 100_000.0
            GoalType.VOLUME -> 500_000.0
            null -> Double.MAX_VALUE
        }

        fun outOfRangeMessageFor(goalType: GoalType): Int = when (goalType) {
            GoalType.DISTANCE -> R.string.new_goal_error_target_out_of_range_distance
            GoalType.COUNT -> R.string.new_goal_error_target_out_of_range_count
            GoalType.DURATION -> R.string.new_goal_error_target_out_of_range_duration
            GoalType.VOLUME -> R.string.new_goal_error_target_out_of_range_volume
        }

        /**
         * Formatter for pre-filling the target field in edit mode. Whole
         * numbers show without a decimal, others get one decimal.
         */
        fun formatTargetForEdit(goal: Goal): String {
            val v = goal.targetValue
            return if (v == v.toLong().toDouble()) v.toLong().toString()
            else "%.1f".format(v)
        }

        fun factory(
            container: AppContainer,
            editingGoalId: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewGoalViewModel(
                    repository = container.goalRepository,
                    editingGoalId = editingGoalId,
                )
            }
        }
    }
}

/**
 * Pure-Kotlin helpers for the auto-generated title. Kept as free
 * functions so unit tests don't need to touch Android.
 */
fun autoTitle(state: NewGoalViewModel.UiState): String {
    val type = state.goalType ?: return ""
    val target = state.targetRaw.trim().toDoubleOrNull()?.let { formatValue(it, type) } ?: ""
    val periodWord = when (state.period) {
        GoalPeriod.THIS_WEEK -> autoPeriodWeek()
        GoalPeriod.THIS_MONTH -> autoPeriodMonth()
    }
    val activity = state.activityType
    return when {
        type == GoalType.DISTANCE && activity == ActivityType.RUNNING ->
            "Run $target km $periodWord"
        type == GoalType.DISTANCE && activity == ActivityType.CYCLING ->
            "Ride $target km $periodWord"
        type == GoalType.DISTANCE && activity == null ->
            "Cover $target km $periodWord"
        type == GoalType.VOLUME ->
            "Lift $target kg $periodWord"
        type == GoalType.COUNT ->
            "$target workouts $periodWord"
        type == GoalType.DURATION ->
            "$target active minutes $periodWord"
        else -> "$target ${type.name.lowercase()} $periodWord"
    }.replace("  ", " ").trim()
}

private fun formatValue(value: Double, type: GoalType): String = when (type) {
    GoalType.COUNT, GoalType.DURATION -> value.toLong().toString()
    GoalType.DISTANCE, GoalType.VOLUME ->
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else "%.1f".format(value)
}

private fun autoPeriodWeek(): String = "this week"
private fun autoPeriodMonth(): String = "this month"
