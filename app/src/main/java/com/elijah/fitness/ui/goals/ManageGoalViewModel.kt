package com.elijah.fitness.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalWithProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the Manage goal bottom sheet (phase 8b step 7).
 *
 * The sheet is a thin render on top of [GoalProgressCalculator] — it
 * picks the one [GoalWithProgress] whose id matches [goalId] and exposes
 * it as a nullable `StateFlow` so the sheet can show a brief empty state
 * while the first emission is in flight (or if the goal was deleted
 * from another device).
 */
class ManageGoalViewModel(
    private val goalId: String,
    calculator: GoalProgressCalculator,
) : ViewModel() {

    val goal: StateFlow<GoalWithProgress?> = calculator.observe()
        .map { list -> list.firstOrNull { it.goal.id == goalId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer, goalId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ManageGoalViewModel(
                        goalId = goalId,
                        calculator = container.goalProgressCalculator,
                    )
                }
            }
    }
}
