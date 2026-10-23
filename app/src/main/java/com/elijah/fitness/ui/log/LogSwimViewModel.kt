package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
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
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.util.CalorieEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * State holder for Log a swim (phase 8d, LogSwim.dc.html).
 *
 * It owns everything the screen shows — date, start time, pool length,
 * laps and the typed duration — in one [StateFlow] ([uiState]), so the
 * live summary (pace per 100 m, distance, calories) is worked out here
 * and survives rotation. The fragment only forwards input.
 *
 * The date, start time, pool length and laps are also mirrored into a
 * [SavedStateHandle] (a small key–value store Android keeps when it
 * kills the app in the background), so a swim half-filled in before a
 * process death comes back exactly as it was — the typed duration and
 * notes restore themselves from their text fields.
 *
 * Save validates (laps 1–400, duration above zero and at most 24 h with
 * minutes and seconds 0–59, start not in the future: [LogFormRules], the
 * same rules as the run form) and stores a [SwimmingWorkout] through the
 * offline-first repository with its distance (laps × pool length), laps,
 * pool length, notes and calories worked out now at the profile weight
 * ([CalorieEstimator.storedWorkoutKcal]).
 */
class LogSwimViewModel(
    private val repository: WorkoutRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Profile weight for the live calories ([UserSettings.bodyWeightKg]). */
    bodyWeightKg: StateFlow<Double> = MutableStateFlow(UserSettings.DEFAULT_BODY_WEIGHT_KG),
    /** [UserSettings.weightForSavingKgOrNull] at save time. */
    private val weightForSavingKg: () -> Double? = { null },
    /** Remembers the activity for the Log a workout sheet (on the app scope). */
    private val rememberActivity: (ActivityType) -> Unit = {},
    /** Keeps the picked values across a process death (see the class docs). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /** The raw form; [uiState] adds the worked-out summary. */
    private data class Form(
        val date: LocalDate,
        val time: LocalTime,
        val poolLengthM: Int = LogSwimCalculator.DEFAULT_POOL_LENGTH_M,
        val laps: Int = LogSwimCalculator.DEFAULT_LAPS,
        val hours: Int = 0,
        val minutes: Int = 0,
        val seconds: Int = 0,
    ) {
        val durationSeconds: Long
            get() = LogFormRules.durationSeconds(hours, minutes, seconds).coerceAtLeast(0L)
    }

    private val form = MutableStateFlow(restoredForm())

    val uiState: StateFlow<LogSwimUiState> = combine(form, bodyWeightKg) { f, weight ->
        f.toUi(weight)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = form.value.toUi(bodyWeightKg.value),
    )

    private val _isSaving = MutableStateFlow(false)
    /** True while a save is in flight; the Save button disables itself. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /**
     * One id per form, reused by every save attempt, so Retry can't store
     * the swim twice (kept across a process death too).
     */
    private val workoutId: String =
        (savedState.get<Any?>(KEY_WORKOUT_ID) as? String)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { savedState[KEY_WORKOUT_ID] = it }

    sealed interface Event {
        /** Saved; the fragment opens the new swim's detail screen. */
        data class Saved(val clientUuid: String) : Event
        data class ValidationFailed(val errors: List<FieldError>) : Event
        /** The save failed; the form keeps its values and offers Retry. */
        data class SaveFailed(@StringRes val messageRes: Int) : Event
    }

    /** Field-scoped validation error; [messageRes] is shown under that field. */
    data class FieldError(val field: Field, @StringRes val messageRes: Int)

    enum class Field { DATE, LAPS, DURATION }

    fun setDate(date: LocalDate) = updateForm { it.copy(date = date) }

    fun setTime(time: LocalTime) = updateForm { it.copy(time = time) }

    /** Picks a pool length; anything but 25 or 50 m is ignored. */
    fun setPoolLength(metres: Int) {
        if (metres !in LogSwimCalculator.POOL_LENGTHS_M) return
        updateForm { it.copy(poolLengthM = metres) }
    }

    /** One stepper step: -1 or +1 lap, kept inside 1–400. */
    fun stepLaps(direction: Int) {
        updateForm { it.copy(laps = LogSwimCalculator.stepLaps(it.laps, direction)) }
    }

    /** The duration cells changed (raw values; validated on save). */
    fun setDuration(hours: Int, minutes: Int, seconds: Int) {
        form.update { it.copy(hours = hours, minutes = minutes, seconds = seconds) }
    }

    /**
     * Validates the form and saves the swim. Emits [Event.Saved],
     * [Event.ValidationFailed] or [Event.SaveFailed] on [events].
     */
    fun save(hours: Int, minutes: Int, seconds: Int, notes: String) {
        // A double tap must not send twice.
        if (_isSaving.value) return
        setDuration(hours, minutes, seconds)
        val f = form.value
        val start = LogFormRules.startInstant(f.date, f.time, zone)

        val errors = buildList {
            LogFormRules.startError(start, clock.instant())?.let { add(FieldError(Field.DATE, it)) }
            if (f.laps !in LogSwimCalculator.LAPS_RANGE) {
                add(FieldError(Field.LAPS, R.string.log_swim_error_laps))
            }
            LogFormRules.durationError(f.hours, f.minutes, f.seconds)
                ?.let { add(FieldError(Field.DURATION, it)) }
        }
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.send(Event.ValidationFailed(errors)) }
            return
        }

        val duration = Duration.ofSeconds(f.durationSeconds)
        val metres = LogSwimCalculator.distanceMeters(f.laps, f.poolLengthM).toDouble()
        val trimmedNotes = notes.trim().takeIf { it.isNotEmpty() }
        // Built once to ask the class for its calories (its MET), then
        // again with the value stored.
        val probe = buildSwim(f, start, duration, metres, trimmedNotes, caloriesKcal = null)
        val swim = buildSwim(
            f, start, duration, metres, trimmedNotes,
            caloriesKcal = CalorieEstimator.storedWorkoutKcal(probe, weightForSavingKg()),
        )

        _isSaving.value = true
        viewModelScope.launch {
            val result = try {
                repository.addWorkout(swim)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ApiResult.Error(message = "", status = 0)
            }
            _isSaving.value = false
            when (result) {
                is ApiResult.Success -> {
                    rememberActivity(ActivityType.SWIMMING)
                    _events.send(Event.Saved(swim.id))
                }
                ApiResult.NetworkError -> _events.send(Event.SaveFailed(R.string.log_error_save_network))
                is ApiResult.Error -> _events.send(Event.SaveFailed(R.string.log_error_save_generic))
            }
        }
    }

    /** Changes the form and mirrors the picked values into [savedState]. */
    private fun updateForm(transform: (Form) -> Form) {
        val next = form.updateAndGet(transform)
        savedState[KEY_DATE] = next.date.toEpochDay()
        savedState[KEY_TIME] = next.time.toSecondOfDay()
        savedState[KEY_POOL] = next.poolLengthM
        savedState[KEY_LAPS] = next.laps
    }

    /**
     * The form as it was before a process death, or a fresh one (today,
     * now, a 25 m pool, 60 laps). A stored value that is missing or out
     * of range falls back to its default, never crashes.
     */
    private fun restoredForm(): Form {
        val now = clock.withZone(zone)
        val date = (savedState.get<Any?>(KEY_DATE) as? Long)
            ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
            ?: LocalDate.now(now)
        val time = (savedState.get<Any?>(KEY_TIME) as? Int)
            ?.let { runCatching { LocalTime.ofSecondOfDay(it.toLong()) }.getOrNull() }
            ?: LocalTime.now(now).withSecond(0).withNano(0)
        val pool = (savedState.get<Any?>(KEY_POOL) as? Int)
            ?.takeIf { it in LogSwimCalculator.POOL_LENGTHS_M }
            ?: LogSwimCalculator.DEFAULT_POOL_LENGTH_M
        val laps = (savedState.get<Any?>(KEY_LAPS) as? Int)
            ?.takeIf { it in LogSwimCalculator.LAPS_RANGE }
            ?: LogSwimCalculator.DEFAULT_LAPS
        return Form(date = date, time = time, poolLengthM = pool, laps = laps)
    }

    private fun buildSwim(
        f: Form,
        start: Instant,
        duration: Duration,
        metres: Double,
        notes: String?,
        caloriesKcal: Int?,
    ): SwimmingWorkout = SwimmingWorkout(
        id = workoutId,
        startTime = start,
        duration = duration,
        distanceMeters = metres,
        notes = notes,
        poolLengthM = f.poolLengthM,
        laps = f.laps,
        caloriesKcal = caloriesKcal,
    )

    private fun Form.toUi(weightKg: Double): LogSwimUiState = LogSwimUiState(
        date = date,
        time = time,
        poolLengthM = poolLengthM,
        laps = laps,
        summary = LogSwimCalculator.summary(laps, poolLengthM, durationSeconds, weightKg),
    )

    companion object {
        private const val KEY_DATE = "log_swim_date_epoch_day"
        private const val KEY_TIME = "log_swim_time_second_of_day"
        private const val KEY_POOL = "log_swim_pool_length_m"
        private const val KEY_LAPS = "log_swim_laps"
        private const val KEY_WORKOUT_ID = "log_swim_workout_id"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogSwimViewModel(
                    repository = container.workoutRepository,
                    bodyWeightKg = container.userSettings.bodyWeightKg,
                    weightForSavingKg = { container.userSettings.weightForSavingKgOrNull() },
                    rememberActivity = container::rememberLoggedActivity,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}

/** Everything the Log a swim screen draws. */
data class LogSwimUiState(
    val date: LocalDate,
    val time: LocalTime,
    val poolLengthM: Int,
    val laps: Int,
    val summary: LogSwimCalculator.Summary,
) {
    /** Index of [poolLengthM] in the segmented control. */
    val poolIndex: Int get() = LogSwimCalculator.POOL_LENGTHS_M.indexOf(poolLengthM).coerceAtLeast(0)
    val canRemoveLap: Boolean get() = laps > LogSwimCalculator.LAPS_RANGE.first
    val canAddLap: Boolean get() = laps < LogSwimCalculator.LAPS_RANGE.last
}
