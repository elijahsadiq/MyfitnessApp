package com.elijah.fitness.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the workout history screen. Owns the filter and exposes
 * the pre-filtered workout list as a `StateFlow`. Survives configuration
 * changes so rotating the device keeps the current filter.
 */
class HistoryViewModel(
    repository: WorkoutRepository
) : ViewModel() {

    enum class Filter { ALL, RUNNING, CYCLING, WEIGHTLIFTING }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

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

    fun setFilter(filter: Filter) {
        _filter.value = filter
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
