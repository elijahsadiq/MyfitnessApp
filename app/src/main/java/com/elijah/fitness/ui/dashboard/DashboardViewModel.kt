package com.elijah.fitness.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZoneId

/**
 * State holder for the dashboard. Recomputes the weekly summary whenever
 * either the workout list or the user's body weight changes — so a
 * freshly-logged workout is reflected instantly with no manual refresh.
 */
class DashboardViewModel(
    repository: WorkoutRepository,
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
