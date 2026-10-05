package com.elijah.fitness.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fetches a workout + route for the detail screen. Reruns on explicit
 * [retry] so a transient network error doesn't need a navigation round-trip.
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

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

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

    companion object {
        fun factory(container: AppContainer, clientUuid: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutDetailViewModel(container, clientUuid) as T
            }
    }
}
