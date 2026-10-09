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
import java.time.Instant
import java.util.UUID

/**
 * Drives the live tracking screen's controls. The *state* is produced by
 * [TrackingSession] (held on `AppContainer`, shared with the foreground
 * service), so a rotation or a trip back to Home never loses the run.
 *
 * - [pause] / [resume] / [finish] act on the session directly — no
 *   service round-trip, so they work instantly and can't be refused by
 *   Android's background-start rules.
 * - [save] uploads the finished session. The workout id is the session's
 *   own id, so a retry after a failed upload replaces rather than
 *   duplicates (the server upserts on `clientUuid`).
 */
class LiveTrackingViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val session: TrackingSession get() = container.trackingSession

    val trackingState: StateFlow<TrackingState> = session.state

    private val _isSaving: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events: Channel<Event> = Channel(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    sealed interface Event {
        /**
         * The workout went to the server; the fragment navigates straight
         * to the finished-workout screen for [clientUuid].
         */
        data class Saved(val clientUuid: String) : Event
        /** Save failed; the screen keeps the finished session with a Retry. */
        data object SaveFailed : Event
        /** The user threw the session away; the fragment pops back. */
        data object Discarded : Event
    }

    fun pause() = session.pause()

    fun resume() = session.resume()

    /**
     * `true` once the session has covered enough ground to be worth
     * saving. Below this the Finish flow offers Discard instead — a
     * "0.00 km" workout with an absurd pace isn't useful history.
     */
    fun hasEnoughDistance(): Boolean =
        trackingState.value.distanceMeters >= MIN_SAVE_DISTANCE_M

    /** Stop the clock (idempotent) and upload the workout. */
    fun finish() {
        session.stop()
        save()
    }

    fun save() {
        if (_isSaving.value) return
        val state = trackingState.value
        if (state.status != TrackingState.Status.Stopped) return
        _isSaving.value = true
        viewModelScope.launch {
            val workout = buildWorkout(state)
            val result = container.workoutRepository.addWorkout(workout, state.points)
            _isSaving.value = false
            when (result) {
                is ApiResult.Success -> {
                    session.reset()
                    _events.send(Event.Saved(workout.id))
                }
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.SaveFailed)
            }
        }
    }

    fun discard() {
        session.reset()
        viewModelScope.launch { _events.send(Event.Discarded) }
    }

    private fun buildWorkout(state: TrackingState): Workout {
        val id = state.sessionId ?: UUID.randomUUID().toString()
        val duration = Duration.ofMillis(state.elapsedMs.coerceAtLeast(1_000L))
        val distanceMeters = state.distanceMeters.coerceAtLeast(1.0)
        val startedAt = state.startedAt ?: Instant.now()
        return when (state.activityType) {
            ActivityType.CYCLING -> CyclingWorkout(
                id = id,
                startTime = startedAt,
                duration = duration,
                distanceMeters = distanceMeters,
            )
            // Weightlifting is never routed through live tracking (the
            // log sheet only offers GPS for runs and rides); falling back
            // to a run keeps the `when` total.
            else -> RunningWorkout(
                id = id,
                startTime = startedAt,
                duration = duration,
                distanceMeters = distanceMeters,
            )
        }
    }

    companion object {
        /** Below this a GPS workout is offered for discard, not saved. */
        const val MIN_SAVE_DISTANCE_M: Double = 10.0

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LiveTrackingViewModel(container) as T
            }
    }
}
