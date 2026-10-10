package com.elijah.fitness.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the finished-workout screen. Fetches the workout + route for a
 * given `clientUuid`, exposes it as [state], and handles the two write
 * actions the detail screen offers:
 *
 * - [updateNotes] — edit the user-visible notes string
 * - [deleteWorkout] — remove the workout and its route entirely
 *
 * Transient side effects (snackbars, navigation back on delete success)
 * are published through the [events] channel; multiple consecutive events
 * of the same type are preserved so a quick double-tap can't drop one.
 */
class WorkoutDetailViewModel(
    private val container: AppContainer,
    private val clientUuid: String,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Loaded(val detail: WorkoutDetail) : State
        data object Error : State
    }

    sealed interface Event {
        data object NotesUpdated : Event
        data object UpdateNotesFailed : Event
        data object Deleted : Event
        data object DeleteFailed : Event
    }

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** True while this workout is saved on the phone but not on the server. */
    val isUnsynced: StateFlow<Boolean> = container.workoutRepository.observeUnsyncedIds()
        .map { clientUuid in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init { load() }

    fun retry() { load() }

    private fun load() {
        _state.value = State.Loading
        viewModelScope.launch {
            when (val result = container.workoutRepository.getWorkoutDetail(clientUuid)) {
                is ApiResult.Success -> _state.value = State.Loaded(result.data)
                is ApiResult.Error,
                ApiResult.NetworkError -> _state.value = State.Error
            }
        }
    }

    fun updateNotes(notes: String?) {
        viewModelScope.launch {
            when (val result = container.workoutRepository.updateNotes(clientUuid, notes)) {
                is ApiResult.Success -> {
                    // Rebuild the loaded detail locally — route points
                    // haven't changed, only the workout fields have.
                    val currentDetail = (state.value as? State.Loaded)?.detail
                    if (currentDetail != null) {
                        _state.value = State.Loaded(
                            currentDetail.copy(workout = result.data),
                        )
                    }
                    _events.send(Event.NotesUpdated)
                }
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.UpdateNotesFailed)
            }
        }
    }

    fun deleteWorkout() {
        viewModelScope.launch {
            when (container.workoutRepository.deleteWorkout(clientUuid)) {
                is ApiResult.Success -> _events.send(Event.Deleted)
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.DeleteFailed)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer, clientUuid: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutDetailViewModel(container, clientUuid) as T
            }
    }
}
