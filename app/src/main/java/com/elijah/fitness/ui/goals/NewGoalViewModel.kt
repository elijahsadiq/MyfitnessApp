package com.elijah.fitness.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
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
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.ui.auth.FieldError
import com.elijah.fitness.util.TextResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * State holder for the New goal / Edit goal bottom sheet (phase 8d,
 * NewGoal.dc.html).
 *
 * The user picks an activity (swipeable cards), what to measure it by,
 * a target and a number of days. [uiState] adds everything worked out
 * from those — the goal sentence, Per week / Per day, the end date, the
 * calorie estimate and the stepper texts — with the rules and wording
 * in [NewGoalPlan]. All text comes from strings.xml through [text].
 *
 * The calorie estimate follows the profile weight live
 * ([UserSettings.bodyWeightKg]); while that is the 70 kg fallback
 * ([UserSettings.weightIsFallback]) the sheet shows a hint to add one.
 *
 * ### Modes
 * - **Create** (no [editingGoalId]): Running, Distance, 50 km, 30 days,
 *   or a [Prefill] from Quick Start / "Try again". Starts today.
 * - **Edit**: loads the stored goal (everything can change; the start
 *   date stays the goal's own) and saves with `updateGoal`. The sheet
 *   stays blank until the goal is found; a goal that can't be found
 *   closes the sheet ([Event.GoalUnavailable]) rather than offering the
 *   create defaults, which saving would write over the stored goal.
 *   The Days stepper never goes below the days that have already
 *   passed ([NewGoalPlan.lowestDays]), so saving can't end a running
 *   goal before today.
 *
 * ### Surviving process death
 * The four picks (activity, metric, target, days) are mirrored into
 * [savedState] (a small key–value store Android keeps when it stops the
 * app in the background). When the sheet comes back they win over the
 * arguments' defaults or prefill, and in Edit over the stored goal's
 * values (its start date still comes from the goal).
 *
 * The server only knows [ActivityType] (null = any activity) and
 * [GoalType]; [ActivityOption] maps one-to-one onto them.
 */
class NewGoalViewModel(
    private val repository: GoalRepository,
    /** Reads the sheet's wording from strings.xml / plurals.xml. */
    private val text: TextResolver,
    editingGoalId: String? = null,
    /** Optional seed for a create flow (ignored when editing). */
    prefill: Prefill? = null,
    /** Profile weight for the burn estimate ([UserSettings.bodyWeightKg]). */
    bodyWeightKg: StateFlow<Double> = MutableStateFlow(UserSettings.DEFAULT_BODY_WEIGHT_KG),
    /** [UserSettings.weightIsFallback]: shows the "Add your weight" hint. */
    weightIsFallback: StateFlow<Boolean> = MutableStateFlow(false),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    /** Keeps the picks across process death (see the class notes). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /**
     * UI-level activity, in card order. One-to-one with [ActivityType]
     * (ANY = null).
     */
    enum class ActivityOption {
        RUN, RIDE, SWIM, ROW, LIFT, ANY;

        fun toApi(): ActivityType? = when (this) {
            RUN -> ActivityType.RUNNING
            RIDE -> ActivityType.CYCLING
            SWIM -> ActivityType.SWIMMING
            ROW -> ActivityType.ROWING
            LIFT -> ActivityType.WEIGHTLIFTING
            ANY -> null
        }

        companion object {
            fun fromApi(type: ActivityType?): ActivityOption = when (type) {
                ActivityType.RUNNING -> RUN
                ActivityType.CYCLING -> RIDE
                ActivityType.SWIMMING -> SWIM
                ActivityType.ROWING -> ROW
                ActivityType.WEIGHTLIFTING -> LIFT
                null -> ANY
            }
        }
    }

    /** Fields a create flow can start from (Quick Start rows, "Try again"). */
    data class Prefill(
        val activity: ActivityOption,
        val metric: GoalType,
        val target: Double,
        val days: Int,
    )

    sealed interface Event {
        /** Saved on the server: the sheet closes and Goals says so. */
        data object Saved : Event

        /** Couldn't save; the sheet stays open with this message. */
        data class ShowError(val error: FieldError) : Event

        /**
         * The goal being edited isn't in the list, even after asking the
         * server (deleted on another device, or no connection after the
         * app was restored): the sheet closes and Goals says so.
         */
        data object GoalUnavailable : Event
    }

    /** What the user has picked; [uiState] adds what is worked out from it. */
    private data class Form(
        val editingGoalId: String?,
        /** The stored goal's start when editing; `null` = starts today. */
        val startDate: LocalDate? = null,
        /** False while an edited goal is still loading. */
        val loaded: Boolean = true,
        val activity: ActivityOption = ActivityOption.RUN,
        val metric: GoalType = GoalType.DISTANCE,
        val target: Double = NewGoalPlan.spec(ActivityOption.RUN, GoalType.DISTANCE).default,
        val days: Int = NewGoalPlan.DAYS_DEFAULT,
        val submitting: Boolean = false,
    )

    /** The four values the user picks, as kept in [savedState]. */
    private data class Picks(
        val activity: ActivityOption,
        val metric: GoalType,
        val target: Double,
        val days: Int,
    )

    /** What the user had picked before a process death, or `null`. */
    private val restored: Picks? = restoredPicks()

    private val form = MutableStateFlow(
        initialForm(editingGoalId, prefill).let { f ->
            // Create: the restored picks win over the defaults / prefill.
            // Edit: they're applied once the stored goal has loaded.
            if (restored != null && editingGoalId == null) f.withPicks(restored) else f
        },
    )

    val uiState: StateFlow<NewGoalUiState> =
        combine(form, bodyWeightKg, weightIsFallback) { f, weight, fallback ->
            f.toUi(weight, fallback)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = form.value.toUi(bodyWeightKg.value, weightIsFallback.value),
        )

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        if (editingGoalId != null) loadGoal(editingGoalId)
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    /**
     * Another activity card. Keeps the metric when the new activity
     * offers it, otherwise takes its first one; the target goes back to
     * that metric's default either way (distance steps differ per
     * activity).
     */
    fun setActivity(option: ActivityOption) {
        updatePicks { f ->
            if (f.activity == option) return@updatePicks f
            val metric = NewGoalPlan.metricAfterActivityChange(option, f.metric)
            f.copy(
                activity = option,
                metric = metric,
                target = NewGoalPlan.spec(option, metric).default,
            )
        }
    }

    /** Another "Measure by" segment; the target goes back to its default. */
    fun setMetric(metric: GoalType) {
        updatePicks { f ->
            if (f.metric == metric || metric !in NewGoalPlan.metricsFor(f.activity)) return@updatePicks f
            f.copy(metric = metric, target = NewGoalPlan.spec(f.activity, metric).default)
        }
    }

    /** One step of the target stepper: −1 or +1. */
    fun stepTarget(direction: Int) {
        updatePicks { f ->
            f.copy(target = NewGoalPlan.stepTarget(f.target, direction, NewGoalPlan.spec(f.activity, f.metric)))
        }
    }

    fun increaseTarget() = stepTarget(+1)

    fun decreaseTarget() = stepTarget(-1)

    /**
     * One step of the Days stepper: −1 or +1, kept in 1..365 (and, when
     * editing, never ending the goal before today). Only the days change,
     * never the target, as in the design's script.
     */
    fun stepDays(direction: Int) {
        updatePicks { f ->
            f.copy(days = NewGoalPlan.stepDays(f.days, direction, NewGoalPlan.lowestDays(f.startDate, today())))
        }
    }

    fun increaseDays() = stepDays(+1)

    fun decreaseDays() = stepDays(-1)

    // ---------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------

    fun submit() {
        val f = form.value
        if (f.submitting || !f.loaded) return
        val spec = NewGoalPlan.spec(f.activity, f.metric)
        val target = NewGoalPlan.sanitizeTarget(f.target, spec)
        val title = autoTitle(text, f.activity, f.metric, target, f.days, locale)
        form.update { it.copy(submitting = true) }

        viewModelScope.launch {
            val result = try {
                val editingId = f.editingGoalId
                if (editingId != null) {
                    repository.updateGoal(
                        id = editingId,
                        title = title,
                        activityType = f.activity.toApi(),
                        goalType = f.metric,
                        targetValue = target,
                        durationDays = f.days,
                    )
                } else {
                    // A new goal starts today in this zone; the server
                    // accepts ±1 day from its own today.
                    repository.createGoal(
                        title = title,
                        activityType = f.activity.toApi(),
                        goalType = f.metric,
                        targetValue = target,
                        startDate = f.startDate ?: today(),
                        durationDays = f.days,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ApiResult.NetworkError
            }
            form.update { it.copy(submitting = false) }
            when (result) {
                is ApiResult.Success -> _events.send(Event.Saved)
                ApiResult.NetworkError ->
                    _events.send(Event.ShowError(FieldError.Res(R.string.auth_error_network)))
                is ApiResult.Error -> _events.send(Event.ShowError(errorFor(result)))
            }
        }
    }

    /** The server's own words for a rejected field, else a generic message. */
    private fun errorFor(result: ApiResult.Error): FieldError {
        val message = FIELD_ORDER.firstNotNullOfOrNull { key ->
            result.fieldErrors[key]?.takeIf { it.isNotBlank() }
        }
        return if (message != null) FieldError.Text(message) else FieldError.Res(R.string.auth_error_generic)
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private fun loadGoal(id: String) {
        viewModelScope.launch {
            val goal = try {
                findGoal(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (goal != null) {
                // Picks kept from before a process death win over the stored values.
                form.update { f ->
                    val stored = f.withGoal(goal)
                    restored?.let { stored.withPicks(it) } ?: stored
                }
            } else {
                // Never show the create defaults as this goal: "Save
                // changes" would overwrite the stored goal with them. The
                // form stays unloaded (submit refuses) and the sheet closes.
                _events.send(Event.GoalUnavailable)
            }
        }
    }

    /**
     * The goal with [id]. Usually already in the list. Straight after the
     * app is restored the list can still be empty for a moment (the Goals
     * screen is reading its Room copy), so wait briefly for the goal to
     * appear, then ask the server once.
     */
    private suspend fun findGoal(id: String): Goal? {
        val listed = withTimeoutOrNull(EDIT_LOAD_WAIT_MS) {
            repository.observeGoals().first { goals -> goals.any { it.id == id } }
        }
        listed?.firstOrNull { it.id == id }?.let { return it }
        repository.refresh()
        return repository.observeGoals().first().firstOrNull { it.id == id }
    }

    /**
     * The form showing [goal] as stored. Its target is only cleaned up
     * (finite, at most the server's limit), never otherwise changed, so
     * "Save changes" without touching anything sends the goal back as is.
     */
    private fun Form.withGoal(goal: Goal): Form {
        val activity = ActivityOption.fromApi(goal.activityType)
        val stored = goal.goalType()
        val metric = NewGoalPlan.metricAfterActivityChange(activity, stored)
        val days = goal.durationDays.coerceIn(NewGoalPlan.DAYS_MIN, NewGoalPlan.DAYS_MAX)
        val spec = NewGoalPlan.spec(activity, metric)
        return copy(
            loaded = true,
            startDate = goal.startDate,
            activity = activity,
            metric = metric,
            target = if (metric == stored) NewGoalPlan.sanitizeTarget(goal.targetValue, spec) else spec.default,
            days = days,
        )
    }

    private fun Form.withPicks(picks: Picks): Form = copy(
        activity = picks.activity,
        metric = picks.metric,
        target = picks.target,
        days = picks.days,
    )

    /** Changes the form and mirrors the picks into [savedState]. */
    private fun updatePicks(transform: (Form) -> Form) {
        val next = form.updateAndGet(transform)
        if (!next.loaded) return
        savedState[KEY_ACTIVITY] = next.activity.name
        savedState[KEY_METRIC] = next.metric.name
        savedState[KEY_TARGET] = next.target
        savedState[KEY_DAYS] = next.days
    }

    /**
     * The picks saved before a process death, or `null` when there are
     * none. Anything missing or out of range means "none", never a crash.
     */
    private fun restoredPicks(): Picks? {
        val activity = (savedState.get<Any?>(KEY_ACTIVITY) as? String)
            ?.let { runCatching { ActivityOption.valueOf(it) }.getOrNull() }
            ?: return null
        val metric = (savedState.get<Any?>(KEY_METRIC) as? String)
            ?.let { runCatching { GoalType.valueOf(it) }.getOrNull() }
            ?.takeIf { it in NewGoalPlan.metricsFor(activity) }
            ?: return null
        val target = (savedState.get<Any?>(KEY_TARGET) as? Double)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        val days = (savedState.get<Any?>(KEY_DAYS) as? Int)
            ?.takeIf { it in NewGoalPlan.DAYS_MIN..NewGoalPlan.DAYS_MAX }
            ?: return null
        return Picks(
            activity = activity,
            metric = metric,
            target = NewGoalPlan.sanitizeTarget(target, NewGoalPlan.spec(activity, metric)),
            days = days,
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun Form.toUi(weightKg: Double, weightFallback: Boolean): NewGoalUiState {
        val today = today()
        val spec = NewGoalPlan.spec(activity, metric)
        val kcal = NewGoalPlan.estimatedKcal(activity, metric, target, weightKg)
        val editing = editingGoalId != null
        return NewGoalUiState(
            loaded = loaded,
            isEditing = editing,
            title = text.string(if (editing) R.string.new_goal_title_edit else R.string.new_goal_title_create),
            submitLabel = text.string(
                when {
                    submitting -> R.string.new_goal_submit_saving
                    editing -> R.string.new_goal_submit_edit
                    else -> R.string.new_goal_submit_create
                },
            ),
            submitting = submitting,
            activity = activity,
            metric = metric,
            metrics = NewGoalPlan.metricsFor(activity),
            target = target,
            days = days,
            sentence = NewGoalPlan.sentence(text, activity, metric, target, days, locale),
            perLabel = text.string(NewGoalPlan.perLabelRes(days)),
            perValue = NewGoalPlan.perValueText(text, activity, metric, target, days, locale),
            endDate = NewGoalPlan.endDate(startDate ?: today, days),
            endsText = NewGoalPlan.shortDate(NewGoalPlan.endDate(startDate ?: today, days), locale),
            kcal = kcal,
            kcalText = NewGoalPlan.kcalText(text, kcal, locale),
            showWeightHint = weightFallback,
            targetLabel = text.string(NewGoalPlan.targetLabelRes(metric)),
            targetHint = NewGoalPlan.targetHint(text, metric, target, days, locale),
            targetValueText = NewGoalPlan.targetValueText(text, activity, metric, target, locale),
            canDecreaseTarget = NewGoalPlan.canDecreaseTarget(target, spec),
            canIncreaseTarget = NewGoalPlan.canIncreaseTarget(target, spec),
            daysValueText = NewGoalPlan.daysValueText(text, days),
            daysHint = NewGoalPlan.daysHint(text, startDate, today, locale),
            canDecreaseDays = days > NewGoalPlan.lowestDays(startDate, today),
            canIncreaseDays = days < NewGoalPlan.DAYS_MAX,
        )
    }

    companion object {
        /** How long Edit waits for the goal to show up in the list before asking the server. */
        internal const val EDIT_LOAD_WAIT_MS: Long = 1_500L

        /** Which server field error to show first. */
        private val FIELD_ORDER = listOf("targetValue", "durationDays", "startDate", "activityType", "goalType", "title")

        private const val KEY_ACTIVITY = "new_goal_activity"
        private const val KEY_METRIC = "new_goal_metric"
        private const val KEY_TARGET = "new_goal_target"
        private const val KEY_DAYS = "new_goal_days"

        /** The starting form: a goal being edited loads first; a prefill is cleaned up. */
        private fun initialForm(editingGoalId: String?, prefill: Prefill?): Form {
            if (editingGoalId != null) return Form(editingGoalId = editingGoalId, loaded = false)
            if (prefill == null) return Form(editingGoalId = null)
            val metric = NewGoalPlan.metricAfterActivityChange(prefill.activity, prefill.metric)
            val days = prefill.days.coerceIn(NewGoalPlan.DAYS_MIN, NewGoalPlan.DAYS_MAX)
            val spec = NewGoalPlan.spec(prefill.activity, metric)
            return Form(
                editingGoalId = null,
                activity = prefill.activity,
                metric = metric,
                target = if (metric == prefill.metric) NewGoalPlan.sanitizeTarget(prefill.target, spec) else spec.default,
                days = days,
            )
        }

        fun factory(
            container: AppContainer,
            editingGoalId: String?,
            prefill: Prefill? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewGoalViewModel(
                    repository = container.goalRepository,
                    text = container.text,
                    editingGoalId = editingGoalId,
                    prefill = prefill,
                    bodyWeightKg = container.userSettings.bodyWeightKg,
                    weightIsFallback = container.userSettings.weightIsFallback,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}

/**
 * Everything the New goal sheet shows, worked out by [NewGoalViewModel].
 * Every string is ready to display.
 */
data class NewGoalUiState(
    /** False while an edited goal is loading (the sheet stays blank). */
    val loaded: Boolean,
    val isEditing: Boolean,
    /** "New goal" / "Edit goal". */
    val title: String,
    /** "Create goal" / "Save changes"; "Saving…" while [submitting]. */
    val submitLabel: String,
    val submitting: Boolean,
    val activity: NewGoalViewModel.ActivityOption,
    val metric: GoalType,
    /** The "Measure by" segments for [activity]. */
    val metrics: List<GoalType>,
    val target: Double,
    val days: Int,
    /** "Run 50 km in 30 days" (also the stored title). */
    val sentence: String,
    /** "Per week" or "Per day". */
    val perLabel: String,
    /** "11.7 km". */
    val perValue: String,
    val endDate: LocalDate,
    /** "17 Nov". */
    val endsText: String,
    val kcal: Int,
    /** "3,530 kcal". */
    val kcalText: String,
    /** The profile has no weight yet: show "Add your weight in Profile …". */
    val showWeightHint: Boolean,
    /** "Distance", "Total time", "Sessions", "Weight lifted". */
    val targetLabel: String,
    /** "About 1.7 km a day". */
    val targetHint: String,
    /** "50 km", "2 h 30 min", "12 runs", "20,000 kg". */
    val targetValueText: String,
    val canDecreaseTarget: Boolean,
    val canIncreaseTarget: Boolean,
    /** "30 days". */
    val daysValueText: String,
    /** "Starts today". */
    val daysHint: String,
    val canDecreaseDays: Boolean,
    val canIncreaseDays: Boolean,
) {
    /** The selected card. */
    val activityIndex: Int get() = activity.ordinal

    /** The selected "Measure by" segment (-1 never happens: [metric] is always offered). */
    val metricIndex: Int get() = metrics.indexOf(metric)
}
