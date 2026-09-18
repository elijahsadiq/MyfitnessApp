package com.elijah.fitness.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the workout history screen. Owns the filter and
 * exposes the pre-filtered workout list as a `StateFlow`. Survives
 * configuration changes so rotating the device keeps the current filter.
 *
 * Step 8 adds an [isLoading] flag driven by `repository.refresh()` and a
 * one-shot [errorEvents] channel the fragment consumes to show a
 * Snackbar-with-Retry on failure.
 */
class HistoryViewModel(
    private val repository: WorkoutRepository,
) : ViewModel() {

    enum class Filter { ALL, RUNNING, CYCLING, WEIGHTLIFTING }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    val workouts: StateFlow<List<Workout>> = combine(
        repository.observeWorkouts(),
        _filter
    ) { workouts, filter ->
        when (filter) {
            Filter.ALL -> workouts
            Filter.RUNNING -> workouts.filter { it.type == ActivityType.RUNNING }
            Filter.CYCLING -> workouts.filter { it.type == ActivityType.CYCLING }
            Filter.WEIGHTLIFTING -> workouts.filter { it.type == ActivityType.WEIGHTLIFTING }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    init { refresh() }

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.refresh()) {
                is ApiResult.Success -> Unit
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.workouts_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.workouts_error_load_failed))
            }
            _isLoading.value = false
        }
    }

    sealed interface ErrorEvent {
        data class Network(@StringRes val messageRes: Int) : ErrorEvent
        data class Generic(@StringRes val messageRes: Int) : ErrorEvent
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.workoutRepository)
            }
        }
    }
}
