package com.elijah.fitness.ui.log

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.remote.ApiResult
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
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val zone: ZoneId = ZoneId.systemDefault()
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

    fun save(
        hours: Int,
        minutes: Int,
        seconds: Int,
        notes: String
    ) {
        if (_isSaving.value) return
        val startInstant = LocalDateTime.of(_date.value, _time.value)
            .atZone(zone)
            .toInstant()
        val duration = Duration.ofSeconds(
            hours * 3_600L + minutes * 60L + seconds.toLong()
        )
        val currentSets = _sets.value

        val errors = buildList {
            if (startInstant.isAfter(clock.instant())) {
                add(FieldError(Field.DATE, R.string.log_error_date_in_future))
            }
            // Same value checks as the run / ride form: 75 minutes is not
            // a valid "minutes" entry, it's 1 h 15 min.
            if (minutes !in 0..59 || seconds !in 0..59 || hours < 0) {
                add(FieldError(Field.DURATION, R.string.log_error_duration_out_of_range))
            } else if (duration.isZero || duration.isNegative) {
                add(FieldError(Field.DURATION, R.string.log_error_duration_zero))
            }
            if (currentSets.isEmpty()) {
                add(FieldError(Field.SETS, R.string.log_error_sets_required))
            }
        }
        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.send(Event.ValidationFailed(errors)) }
            return
        }

        val workout = StrengthWorkout(
            id = workoutId,
            startTime = startInstant,
            duration = duration,
            sets = currentSets.toList(),
            notes = notes.trim().takeIf { it.isNotEmpty() }
        )
        _isSaving.value = true
        viewModelScope.launch {
            val result = repository.addWorkout(workout)
            _isSaving.value = false
            when (result) {
                is ApiResult.Success -> _events.send(Event.Saved(workout.id))
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
                    zone = zone
                )
            }
        }
    }
}
