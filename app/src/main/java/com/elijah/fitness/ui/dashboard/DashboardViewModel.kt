package com.elijah.fitness.ui.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.UserSettings
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
import java.time.Clock
import java.time.ZoneId

/**
 * State holder for the dashboard. Recomputes the weekly summary whenever
 * either the workout list or the user's body weight changes — so a
 * freshly-logged workout is reflected instantly with no manual refresh.
 *
 * Step 8 adds [isLoading] driven by `repository.refresh()` and a
 * one-shot [errorEvents] channel the fragment consumes to render a
 * Snackbar-with-Retry when the refresh fails.
 */
class DashboardViewModel(
    private val repository: WorkoutRepository,
    userSettings: UserSettings,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    val summary: StateFlow<WeeklySummary> = combine(
        repository.observeWorkouts(),
        userSettings.bodyWeightKg
    ) { workouts, weight ->
        WeeklySummaryCalculator.compute(
            workouts = workouts,
            now = clock.instant(),
            zone = zone,
            bodyWeightKg = weight
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WeeklySummary.EMPTY
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    init { refresh() }

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

        fun factory(
            container: AppContainer,
            clock: Clock = Clock.systemDefaultZone(),
            zone: ZoneId = ZoneId.systemDefault()
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    repository = container.workoutRepository,
                    userSettings = container.userSettings,
                    clock = clock,
                    zone = zone
                )
            }
        }
    }
}
