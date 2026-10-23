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
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * State holder for the weightlifting form. Same date/time model as the
 * distance form (pickers need something that survives rotation) plus the
 * sets list, which the fragment mutates through [addSet] and
 * [removeSetAt] because sets are added via a dialog, not typed into a
 * RecyclerView row.
 */
class LogStrengthWorkoutViewModel(
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

    private val _sets = MutableStateFlow<List<StrengthSet>>(emptyList())
    val sets: StateFlow<List<StrengthSet>> = _sets.asStateFlow()

    /** True while a save is in flight — the Save button disables itself. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** One id per form, reused on retry — the server upserts on it. */
    private val workoutId: String = UUID.randomUUID().toString()

    sealed interface Event {
        /** Saved; the fragment opens the detail screen for [clientUuid]. */
        data class Saved(val clientUuid: String) : Event
        data class ValidationFailed(val errors: List<FieldError>) : Event
        data class SaveFailed(@StringRes val messageRes: Int) : Event
    }

    data class FieldError(val field: Field, @StringRes val messageRes: Int)
    enum class Field { DATE, DURATION, SETS }

    fun setDate(date: LocalDate) {
        _date.value = date
    }

    fun setTime(time: LocalTime) {
        _time.value = time
    }

    fun addSet(set: StrengthSet) {
        _sets.value = _sets.value + set
    }

    fun removeSetAt(index: Int) {
        val current = _sets.value
        if (index in current.indices) {
            _sets.value = current.toMutableList().also { it.removeAt(index) }
        }
    }

    /**
     * Validates the form and saves the workout: the shared [LogFormRules]
     * (start not in the future; minutes and seconds 0–59; duration above
     * zero and at most 24 h) and at least one set. Emits [Event.Saved],
     * [Event.ValidationFailed] or — when the save itself fails, including
     * a local database error — [Event.SaveFailed] on [events].
     */
    fun save(
        hours: Int,
        minutes: Int,
        seconds: Int,
        notes: String
    ) {
        if (_isSaving.value) return
        val startInstant = LogFormRules.startInstant(_date.value, _time.value, zone)
        val duration = Duration.ofSeconds(LogFormRules.durationSeconds(hours, minutes, seconds))
        val currentSets = _sets.value

        val errors = buildList {
            LogFormRules.startError(startInstant, clock.instant())?.let {
                add(FieldError(Field.DATE, it))
            }
            // Same value checks as every log form: 75 minutes is not a
            // valid "minutes" entry, it's 1 h 15 min.
            LogFormRules.durationError(hours, minutes, seconds)?.let {
                add(FieldError(Field.DURATION, it))
            }
            if (currentSets.isEmpty()) {
                add(FieldError(Field.SETS, R.string.log_error_sets_required))
            }
        }
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.send(Event.ValidationFailed(errors)) }
            return
        }

        val probe = StrengthWorkout(
            id = workoutId,
            startTime = startInstant,
            duration = duration,
            sets = currentSets.toList(),
            notes = notes.trim().takeIf { it.isNotEmpty() }
        )
        // Phase 8d: store the calories with the workout (none while the
        // profile weight isn't known — screens work them out later).
        val workout = StrengthWorkout(
            id = probe.id,
            startTime = probe.startTime,
            duration = probe.duration,
            sets = probe.sets,
            notes = probe.notes,
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
                    // The Log a workout sheet opens on Workout next time.
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

    companion object {
        fun factory(
            container: AppContainer,
            clock: Clock = Clock.systemDefaultZone(),
            zone: ZoneId = ZoneId.systemDefault()
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogStrengthWorkoutViewModel(
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
