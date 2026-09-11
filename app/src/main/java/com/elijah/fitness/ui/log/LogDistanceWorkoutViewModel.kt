package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.WorkoutRepository
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * State holder for the distance-log form. Owns the date and time chosen via
 * pickers (these must survive rotation) and validates + persists the workout
 * on save. Everything else (duration, distance, notes) is read from the
 * fragment at save time — those live inside EditTexts and don't benefit from
 * a bidirectional binding.
 */
class LogDistanceWorkoutViewModel(
    private val activityType: ActivityType,
    private val repository: WorkoutRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now(clock))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _time = MutableStateFlow(LocalTime.now(clock).withSecond(0).withNano(0))
    val time: StateFlow<LocalTime> = _time.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    sealed interface Event {
        data object Saved : Event
        data class ValidationFailed(val errors: List<FieldError>) : Event
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
     * success. Emits either [Event.Saved] or [Event.ValidationFailed] on
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
        val startInstant = LocalDateTime.of(_date.value, _time.value)
            .atZone(zone)
            .toInstant()
        val duration = Duration.ofSeconds(
            hours * 3_600L + minutes * 60L + seconds.toLong()
        )
        val errors = validate(startInstant, duration, distanceKm)
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.send(Event.ValidationFailed(errors)) }
            return
        }
        val workout = buildWorkout(startInstant, duration, distanceKm!!, notes)
        viewModelScope.launch {
            repository.addWorkout(workout)
            _events.send(Event.Saved)
        }
    }

    private fun validate(
        startInstant: Instant,
        duration: Duration,
        distanceKm: Double?
    ): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (startInstant.isAfter(clock.instant())) {
            errors += FieldError(Field.DATE, R.string.log_error_date_in_future)
        }
        if (duration.isZero || duration.isNegative) {
            errors += FieldError(Field.DURATION, R.string.log_error_duration_zero)
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
        notes: String
    ): Workout {
        val id = UUID.randomUUID().toString()
        val distanceMeters = distanceKm * 1_000.0
        val trimmedNotes = notes.trim().takeIf { it.isNotEmpty() }
        return when (activityType) {
            ActivityType.RUNNING -> RunningWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes
            )
            ActivityType.CYCLING -> CyclingWorkout(
                id = id,
                startTime = startInstant,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = trimmedNotes
            )
            ActivityType.WEIGHTLIFTING ->
                error("LogDistanceWorkoutViewModel called with WEIGHTLIFTING")
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
                    zone = zone
                )
            }
        }
    }
}
