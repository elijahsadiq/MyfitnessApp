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
 * State holder for the full-screen New Goal / Edit Goal screen
 * (phase 8b step 6).
 *
 * ### Model
 *
 * The design talks about four "activities" (Run / Ride / Workout / Any)
 * and four "metrics" (Distance / Time / Sessions / Weight). The server
 * only knows [ActivityType] (`RUNNING, CYCLING, WEIGHTLIFTING`, or null)
 * and [GoalType] (`DISTANCE, COUNT, DURATION, VOLUME`). Mapping is
 * one-to-one, done lazily on submit.
 *
 * ### Allowed matrix
 *
 *   Run / Ride → Distance (km), Time (min), Sessions
 *   Workout    → Time (min), Sessions, Weight (kg)
 *   Any        → Time (min), Sessions
 *
 * Changing activity keeps the metric when the new activity still
 * supports it, otherwise falls back to the first allowed metric and
 * resets the target to that metric's default.
 */
class NewGoalViewModel(
    private val repository: GoalRepository,
    editingGoalId: String? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** UI-level activity. One-to-one with [ActivityType] (ANY = null). */
    enum class ActivityOption {
        RUN, RIDE, LIFT, ANY;

        fun toApi(): ActivityType? = when (this) {
            RUN -> ActivityType.RUNNING
            RIDE -> ActivityType.CYCLING
            LIFT -> ActivityType.WEIGHTLIFTING
            ANY -> null
        }

        companion object {
            fun fromApi(type: ActivityType?): ActivityOption = when (type) {
                ActivityType.RUNNING -> RUN
                ActivityType.CYCLING -> RIDE
                ActivityType.WEIGHTLIFTING -> LIFT
                null -> ANY
            }
        }
    }

    /** Per-metric step / bounds / default. See the design doc for values. */
    data class MetricSpec(
        val step: Int,
        val min: Int,
        val max: Int,
        val default: Int,
    )

    data class UiState(
        val editingGoalId: String? = null,
        /**
         * Start date to send on submit. `null` while creating (defaults to
         * server-side "today"); set when editing so changing the number of
         * days keeps the goal's **original** start date.
         */
        val startDate: LocalDate? = null,
        val activity: ActivityOption = ActivityOption.RUN,
        val metric: GoalType = GoalType.DISTANCE,
        val target: Int = metricSpec(GoalType.DISTANCE).default,
        val days: Int = DAYS_DEFAULT,
        val submitting: Boolean = false,
        val activityError: FieldError? = null,
        val targetError: FieldError? = null,
        val daysError: FieldError? = null,
    ) {
        /** Metrics the user can pick given the current [activity]. */
        val allowedMetrics: List<GoalType>
            get() = allowedMetricsFor(activity)
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
        if (editingGoalId != null) {
            viewModelScope.launch {
                val goal = repository.observeGoals().first()
                    .firstOrNull { it.id == editingGoalId } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    startDate = goal.startDate,
                    activity = ActivityOption.fromApi(goal.activityType),
                    metric = goal.goalType(),
                    target = goal.targetValue.toInt().coerceAtLeast(1),
                    days = goal.durationDays,
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // State updates
    // ---------------------------------------------------------------------

    /**
     * Switch activity. Keeps the current metric if still allowed;
     * otherwise falls back to the first allowed metric and resets the
     * target to that metric's default.
     */
    fun setActivity(option: ActivityOption) {
        _uiState.update { s ->
            if (s.activity == option) return@update s
            val allowed = allowedMetricsFor(option)
            val nextMetric = if (s.metric in allowed) s.metric else allowed.first()
            val nextTarget = if (nextMetric == s.metric) s.target else metricSpec(nextMetric).default
            s.copy(
                activity = option,
                metric = nextMetric,
                target = nextTarget,
                activityError = null,
                targetError = null,
            )
        }
    }

    /** Switch metric; resets the target to that metric's default. */
    fun setMetric(metric: GoalType) {
        _uiState.update { s ->
            if (s.metric == metric) return@update s
            s.copy(
                metric = metric,
                target = metricSpec(metric).default,
                targetError = null,
            )
        }
    }

    /** Clamp + apply a new target value from the type-in dialog. */
    fun setTarget(value: Int) {
        _uiState.update { s ->
            val spec = metricSpec(s.metric)
            s.copy(target = value.coerceIn(spec.min, spec.max), targetError = null)
        }
    }

    /** `+1 step` on tap or hold. Caps at max; no wrap. */
    fun incrementTarget() = stepTarget(+1)

    /** `-1 step` on tap or hold. Floors at min. */
    fun decrementTarget() = stepTarget(-1)

    private fun stepTarget(direction: Int) {
        _uiState.update { s ->
            val spec = metricSpec(s.metric)
            val next = (s.target + direction * spec.step).coerceIn(spec.min, spec.max)
            s.copy(target = next, targetError = null)
        }
    }

    fun setDays(value: Int) {
        _uiState.update { s ->
            s.copy(days = value.coerceIn(DAYS_MIN, DAYS_MAX), daysError = null)
        }
    }

    fun incrementDays() = stepDays(+1)
    fun decrementDays() = stepDays(-1)

    private fun stepDays(direction: Int) {
        _uiState.update { s ->
            val next = (s.days + direction).coerceIn(DAYS_MIN, DAYS_MAX)
            s.copy(days = next, daysError = null)
        }
    }

    // ---------------------------------------------------------------------
    // Submit
    // ---------------------------------------------------------------------

    fun submit() {
        val s = _uiState.value
        if (s.submitting) return

        val editingId = s.editingGoalId
        val activity = s.activity.toApi()
        val metric = s.metric
        val target = s.target.toDouble()

        _uiState.value = s.copy(submitting = true)
        viewModelScope.launch {
            val result = if (editingId != null) {
                repository.updateGoal(
                    id = editingId,
                    title = autoTitle(s),
                    activityType = activity,
                    goalType = metric,
                    targetValue = target,
                    durationDays = s.days,
                )
            } else {
                // Fresh goal: start today in the client zone; server rejects
                // if it drifts more than ±1 day from server-UTC today.
                val startDate = s.startDate ?: LocalDate.ofInstant(clock.instant(), zone)
                repository.createGoal(
                    title = autoTitle(s),
                    activityType = activity,
                    goalType = metric,
                    targetValue = target,
                    startDate = startDate,
                    durationDays = s.days,
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(submitting = false)
                    _events.send(Event.Saved)
                }
                ApiResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(submitting = false)
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
        val daysErr = f["durationDays"]?.let(FieldError::Text)
            ?: f["startDate"]?.let(FieldError::Text)

        if (activityErr != null || targetErr != null || daysErr != null) {
            _uiState.value = _uiState.value.copy(
                submitting = false,
                activityError = activityErr,
                targetError = targetErr,
                daysError = daysErr,
            )
        } else {
            _uiState.value = _uiState.value.copy(submitting = false)
            _events.send(Event.ShowError(R.string.auth_error_generic))
        }
    }

    companion object {
        const val DAYS_MIN = 1
        const val DAYS_MAX = 365
        const val DAYS_DEFAULT = 30

        /** Phase-8b stepper values straight from the brief. */
        fun metricSpec(type: GoalType): MetricSpec = when (type) {
            GoalType.DISTANCE -> MetricSpec(step = 5, min = 1, max = 1_000, default = 50)
            GoalType.DURATION -> MetricSpec(step = 15, min = 15, max = 10_000, default = 150)
            GoalType.COUNT -> MetricSpec(step = 1, min = 1, max = 100, default = 4)
            GoalType.VOLUME -> MetricSpec(step = 1_000, min = 1_000, max = 500_000, default = 10_000)
        }

        /** The design's activity × metric matrix. */
        fun allowedMetricsFor(option: ActivityOption): List<GoalType> = when (option) {
            ActivityOption.RUN, ActivityOption.RIDE ->
                listOf(GoalType.DISTANCE, GoalType.DURATION, GoalType.COUNT)
            ActivityOption.LIFT ->
                listOf(GoalType.DURATION, GoalType.COUNT, GoalType.VOLUME)
            ActivityOption.ANY ->
                listOf(GoalType.DURATION, GoalType.COUNT)
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
 * Pure-Kotlin auto-title helper. Builds the headline sentence that
 * also becomes the stored goal title ("Run 50 km in 30 days", etc.).
 * Formatting is locale-aware for the thousands separator.
 */
fun autoTitle(state: NewGoalViewModel.UiState): String =
    autoTitle(state.activity, state.metric, state.target, state.days)

internal fun autoTitle(
    activity: NewGoalViewModel.ActivityOption,
    metric: GoalType,
    target: Int,
    days: Int,
): String {
    val fmt = java.text.NumberFormat.getIntegerInstance().format(target.toLong())
    val dayWord = if (days == 1) "day" else "days"
    return when {
        metric == GoalType.DISTANCE && activity == NewGoalViewModel.ActivityOption.RUN ->
            "Run $fmt km in $days $dayWord"
        metric == GoalType.DISTANCE && activity == NewGoalViewModel.ActivityOption.RIDE ->
            "Ride $fmt km in $days $dayWord"
        metric == GoalType.DURATION && activity == NewGoalViewModel.ActivityOption.RUN ->
            "Run for $fmt min in $days $dayWord"
        metric == GoalType.DURATION && activity == NewGoalViewModel.ActivityOption.RIDE ->
            "Ride for $fmt min in $days $dayWord"
        metric == GoalType.DURATION && activity == NewGoalViewModel.ActivityOption.LIFT ->
            "Train for $fmt min in $days $dayWord"
        metric == GoalType.DURATION && activity == NewGoalViewModel.ActivityOption.ANY ->
            "Be active for $fmt min in $days $dayWord"
        metric == GoalType.VOLUME ->
            "Lift $fmt kg in $days $dayWord"
        metric == GoalType.COUNT -> {
            val word = sessionWord(activity, target)
            "$fmt $word in $days $dayWord"
        }
        else -> "$fmt in $days $dayWord"
    }
}

internal fun sessionWord(activity: NewGoalViewModel.ActivityOption, target: Int): String {
    val plural = target != 1
    return when (activity) {
        NewGoalViewModel.ActivityOption.RUN -> if (plural) "runs" else "run"
        NewGoalViewModel.ActivityOption.RIDE -> if (plural) "rides" else "ride"
        NewGoalViewModel.ActivityOption.LIFT -> if (plural) "workouts" else "workout"
        NewGoalViewModel.ActivityOption.ANY -> if (plural) "sessions" else "session"
    }
}
