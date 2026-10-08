package com.elijah.fitness.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.tracking.TrackPoint
import com.elijah.fitness.tracking.TrackingSession
import com.elijah.fitness.tracking.TrackingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.UUID

/**
 * Owns the save path for a tracked workout. The *state* is produced by
 * [TrackingSession] (held on `AppContainer`) and consumed directly by the
 * fragment; this VM only steps in once the user hits Save or Discard, so
 * a rotation while tracking never touches it.
 */
class LiveTrackingViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = container.trackingSession.state

    private val _isSaving: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events: Channel<Event> = Channel(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    sealed interface Event {
        /**
         * The workout went to the server; the fragment navigates straight
         * to the finished-workout screen for [clientUuid] (phase 8c step
         * 6 — notes are edited there, not here).
         */
        data class Saved(val clientUuid: String) : Event
        /** Save failed; the fragment keeps the data on screen with a Retry. */
        data object SaveFailed : Event
        /** The user cancelled a stopped session; discard the state and pop. */
        data object Discarded : Event
    }

    fun save(notes: String = "") {
        if (_isSaving.value) return
        val state = trackingState.value
        _isSaving.value = true
        viewModelScope.launch {
            val workout = buildWorkout(state, notes.trim().ifEmpty { null })
            val result = container.workoutRepository.addWorkout(workout, state.points)
            _isSaving.value = false
            when (result) {
                is ApiResult.Success -> {
                    container.trackingSession.reset()
                    _events.send(Event.Saved(workout.id))
                }
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.SaveFailed)
            }
        }
    }

    fun discard() {
        container.trackingSession.reset()
        viewModelScope.launch { _events.send(Event.Discarded) }
    }

    /**
     * If the user navigates away while a session is active, reset the
     * session so the next entry starts clean. Called from the fragment's
     * confirmed-back / discard path, never on a plain rotation.
     */
    private fun buildWorkout(state: TrackingState, notes: String?): Workout {
        val id = UUID.randomUUID().toString()
        val duration = Duration.ofMillis(state.elapsedMs.coerceAtLeast(1_000L))
        val distanceMeters = state.distanceMeters.coerceAtLeast(1.0)
        val startedAt = state.startedAt ?: java.time.Instant.now()
        return when (state.activityType) {
            ActivityType.RUNNING -> RunningWorkout(
                id = id,
                startTime = startedAt,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = notes,
            )
            ActivityType.CYCLING -> CyclingWorkout(
                id = id,
                startTime = startedAt,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = notes,
            )
            // Weightlifting is never routed through live tracking (the
            // choose-activity sheet skips the "track" option for it), but
            // falling back to a Running shell keeps the type signature
            // total — the server validator would reject it anyway.
            else -> RunningWorkout(
                id = id,
                startTime = startedAt,
                duration = duration,
                distanceMeters = distanceMeters,
                notes = notes,
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun unused(points: List<TrackPoint>) {}

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LiveTrackingViewModel(container) as T
            }
    }
}
