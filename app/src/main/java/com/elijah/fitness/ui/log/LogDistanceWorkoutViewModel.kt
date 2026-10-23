package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.util.CalorieEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * State holder for the distance-log form. Owns the date and time chosen via
 * pickers (these must survive rotation) and validates + persists the workout
 * on save. Everything else (duration, distance, notes) is read from the
 * fragment at save time — those live inside EditTexts and don't benefit from
 * a bidirectional binding.
 *
 * Phase 8d: also used for manual rows, and the saved workout carries its
 * calories ([CalorieEstimator.storedWorkoutKcal] at [weightForSavingKg]) so
 * history doesn't change if the profile weight is edited later. While the
 * profile hasn't loaded yet nothing is stored and screens work it out later.
 */
class LogDistanceWorkoutViewModel(
    private val activityType: ActivityType,
    private val repository: WorkoutRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** [com.elijah.fitness.data.repository.UserSettings.weightForSavingKgOrNull] at save time. */
    private val weightForSavingKg: () -> Double? = { null },
    /** [AppContainer.rememberLoggedActivity]: called once the save worked. */
    private val rememberActivity: (ActivityType) -> Unit = {},
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now(clock))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _time = MutableStateFlow(LocalTime.now(clock).withSecond(0).withNano(0))
    val time: StateFlow<LocalTime> = _time.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** True while a save is in flight — the Save button disables itself. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * One id for this form, reused by every save attempt. The server
     * upserts on it, so "Retry" after a timeout can never store the run
     * twice.
     */
    private val workoutId: String = UUID.randomUUID().toString()

    sealed interface Event {
        /**
         * Save succeeded. [clientUuid] is the id of the just-saved workout
         * — the fragment navigates straight to its detail screen rather
         * than popping back to the list (phase 8c step 5).
         */
        data class Saved(val clientUuid: String) : Event
        data class ValidationFailed(val errors: List<FieldError>) : Event
        /**
         * Save failed on the network layer. The fragment keeps the user's
         * typed values on screen and offers a Retry Snackbar — never
         * discards input.
         */
        data class SaveFailed(@StringRes val messageRes: Int) : Event
    }

    /** Field-scoped validation error. [messageRes] is what the fragment shows inline. */
    data class FieldError(val field: Field, @StringRes val messageRes: Int)

    enum class Field { DATE, DURATION, DISTANCE }

    fun setDate(date: LocalDate) {
        _date.value = date
    }

    fun setTime(time: LocalTime) {
        _time.value = time
    }

    /**
     * Validate the current form inputs and persist a distance workout on
     * success. The checks are the shared [LogFormRules] (start not in the
     * future; minutes and seconds 0–59; duration above zero and at most
     * 24 h) plus a distance above 0 and at most [MAX_DISTANCE_KM]. Emits
     * [Event.Saved], [Event.ValidationFailed] or — when the save itself
     * fails, including a local database error — [Event.SaveFailed] on
     * [events]. All numeric inputs are provided by the fragment because
     * they live inside `EditText`s.
     */
    fun save(
        hours: Int,
        minutes: Int,
        seconds: Int,
        distanceKm: Double?,
        notes: String
    ) {
        // A double tap (or Save + the keyboard's Done) must not send twice.
        if (_isSaving.value) return
        val startInstant = LogFormRules.startInstant(_date.value, _time.value, zone)
        val duration = Duration.ofSeconds(LogFormRules.durationSeconds(hours, minutes, seconds))
        val errors = validate(
            startInstant = startInstant,
            hours = hours, minutes = minutes, seconds = seconds,
            distanceKm = distanceKm,
        )
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.send(Event.ValidationFailed(errors)) }
            return
        }
        val km = distanceKm ?: return
        // Build once to ask the class for its calories (polymorphic MET),
        // then again with the value stored.
        val probe = buildWorkout(startInstant, duration, km, notes, caloriesKcal = null)
        val workout = buildWorkout(
            startInstant, duration, km, notes,
            caloriesKcal = CalorieEstimator.storedWorkoutKcal(probe, weightForSavingKg()),
        )
        _isSaving.value = true
        viewModelScope.launch {
            // A Room / disk failure must show an error with Retry, never
            // crash the app (same as Log a swim).
            val result = try {
                repository.addWorkout(workout)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ApiResult.Error(message = "", status = 0)
            }
            _isSaving.value = false
            when (result) {
                is ApiResult.Success -> {
                    // The Log a workout sheet opens on this activity next time.
                    rememberActivity(workout.type)
                    _events.send(Event.Saved(workout.id))
                }
                ApiResult.NetworkError ->
                    _events.send(Event.SaveFailed(R.string.log_error_save_network))
                is ApiResult.Error ->
                    _events.send(Event.SaveFailed(R.string.log_error_save_generic))
            }
        }
    }

    private fun validate(
        startInstant: Instant,
        hours: Int,
        minutes: Int,
        seconds: Int,
        distanceKm: Double?,
    ): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        LogFormRules.startError(startInstant, clock.instant())?.let {
            errors += FieldError(Field.DATE, it)
        }
        // Phase 8c step 5 user instruction: validate the VALUES, don't
        // just cap maxLength on the EditText. Negative or over-flowed
        // digits must be an inline error, not a silent clamp.
        LogFormRules.durationError(hours, minutes, seconds)?.let {
            errors += FieldError(Field.DURATION, it)
        }
        val km = distanceKm
        when {
            km == null || km <= 0.0 ->
                errors += FieldError(Field.DISTANCE, R.string.log_error_distance_positive)
            km > MAX_DISTANCE_KM ->
                errors += FieldError(Field.DISTANCE, R.string.log_error_distance_too_far)
        }
        return errors
    }

    private fun buildWorkout(
        startInstant: Instant,
        duration: Duration,
        distanceKm: Double,
        notes: String,
        caloriesKcal: Int?,
    ): Workout {
        val id = workoutId
        val distanceMeters = distanceKm * 1_000.0
        val trimmedNotes = notes.trim().takeIf { it.isNotEmpty() }
        return when (activityType) {
            ActivityType.CYCLING -> CyclingWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes,
                caloriesKcal = caloriesKcal,
            )
            ActivityType.ROWING -> RowingWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes,
                caloriesKcal = caloriesKcal,
            )
            ActivityType.SWIMMING -> SwimmingWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes,
                caloriesKcal = caloriesKcal,
            )
            // This screen is never opened for a lift (the log sheet sends
            // those to the sets form); a run keeps the `when` total
            // instead of crashing.
            ActivityType.RUNNING,
            ActivityType.WEIGHTLIFTING -> RunningWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes,
                caloriesKcal = caloriesKcal,
            )
        }
    }

    companion object {
        const val MAX_DISTANCE_KM: Double = 300.0

        fun factory(
            activityType: ActivityType,
            container: AppContainer,
            clock: Clock = Clock.systemDefaultZone(),
            zone: ZoneId = ZoneId.systemDefault()
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogDistanceWorkoutViewModel(
                    activityType = activityType,
                    repository = container.workoutRepository,
                    clock = clock,
                    zone = zone,
                    weightForSavingKg = { container.userSettings.weightForSavingKgOrNull() },
                    rememberActivity = container::rememberLoggedActivity,
                )
            }
        }
    }
}
