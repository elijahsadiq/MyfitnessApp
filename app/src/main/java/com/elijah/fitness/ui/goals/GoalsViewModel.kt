package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.data.repository.GoalRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * State holder for the Goals screen.
 *
 * ### Data flow
 * `GoalProgressCalculator.observe()` emits a list of every goal + its
 * live progress. This ViewModel splits that stream into a rendered list
 * of [GoalListItem]s:
 *
 *   Featured (single, most-urgent incomplete)
 *   Section header "ACTIVE GOALS" + count pill
 *   Active rows (incomplete, minus the featured one)
 *   Section header "COMPLETED"
 *   Completed rows
 *
 * Progress recomputes automatically whenever the underlying goals or
 * workouts change — see `GoalProgressCalculator`.
 */
class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val calculator: GoalProgressCalculator,
) : ViewModel() {

    val items: StateFlow<List<GoalListItem>> = calculator.observe()
        .map { snapshots -> buildItems(snapshots) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    /**
     * Ids of goals whose completion we've already celebrated during
     * this ViewModel's lifetime. Combined with the server-side
     * `completedNotified` flag on `Goal`, it prevents the Snackbar from
     * firing twice — the local set covers the "just now, don't fire
     * again in this session" case, and the server flag covers cold
     * starts of a later session.
     */
    private val celebratedThisSession: MutableSet<String> = mutableSetOf()
    /** True once we've processed the very first snapshot. */
    private var completionsSeeded = false

    init {
        refresh()
        // Observe the calculator once to detect new completions.
        calculator.observe()
            .onEach { snapshots -> detectCompletions(snapshots) }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            when (goalRepository.refresh()) {
                is ApiResult.Success -> Unit
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.auth_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.goals_error_load_failed))
            }
            _isLoading.value = false
        }
    }

    /**
     * Delete a goal, then keep a snapshot of it in memory for an Undo. On
     * undo, we recreate it via the repository (a new server id will be
     * assigned — that's fine, the delete + re-create is atomic from the
     * user's perspective).
     */
    fun deleteWithUndo(goal: Goal) {
        viewModelScope.launch {
            val snapshot = GoalSnapshot(
                title = goal.title,
                activityType = goal.activityType,
                goalType = goal.goalType(),
                period = goal.period,
                targetValue = goal.targetValue,
                periodStart = LocalDate.ofInstant(goal.periodStart,
                    java.time.ZoneId.systemDefault()),
                periodEnd = LocalDate.ofInstant(goal.periodEnd,
                    java.time.ZoneId.systemDefault()),
            )
            when (goalRepository.deleteGoal(goal.id)) {
                is ApiResult.Success ->
                    _errorEvents.send(ErrorEvent.DeletedWithUndo(snapshot))
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.auth_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.goals_error_load_failed))
            }
        }
    }

    /**
     * On every emission from the calculator, detect goals that have
     * newly reached 100% (were incomplete last time OR this is the
     * seed pass and their server-side `completedNotified` flag is
     * still false), emit a celebration event, then persist the flag
     * on the server so a next-session refresh won't re-fire.
     */
    private fun detectCompletions(snapshots: List<GoalWithProgress>) {
        if (!completionsSeeded) {
            // First snapshot: goals that are already complete + already
            // notified are just history. Goals that are complete but not
            // yet notified belong to a past session that never got to
            // fire the Snackbar — celebrate them now.
            snapshots.filter { it.isComplete && !it.goal.completedNotified }
                .forEach { announceCompletion(it) }
            // Seed the "already celebrated" set with everything currently
            // complete, so we don't double-fire on the next emission when
            // markCompletedNotified() writes back through the flow.
            snapshots.filter { it.isComplete }.forEach { celebratedThisSession.add(it.goal.id) }
            completionsSeeded = true
            return
        }
        snapshots.filter {
            it.isComplete && it.goal.id !in celebratedThisSession
        }.forEach { announceCompletion(it) }
    }

    private fun announceCompletion(gwp: GoalWithProgress) {
        celebratedThisSession.add(gwp.goal.id)
        viewModelScope.launch {
            _errorEvents.send(ErrorEvent.GoalCompleted(gwp.goal.title))
            // Persist the flag; silent on failure — worst case is the
            // Snackbar fires once more on next cold start, which is fine.
            goalRepository.markCompletedNotified(gwp.goal.id)
        }
    }

    /** Undo a delete by recreating the same goal from its captured snapshot. */
    fun undoDelete(snapshot: GoalSnapshot) {
        viewModelScope.launch {
            goalRepository.createGoal(
                title = snapshot.title,
                activityType = snapshot.activityType,
                goalType = snapshot.goalType,
                period = snapshot.period,
                targetValue = snapshot.targetValue,
                periodStart = snapshot.periodStart,
                periodEnd = snapshot.periodEnd,
            )
        }
    }

    private fun buildItems(all: List<GoalWithProgress>): List<GoalListItem> {
        if (all.isEmpty()) return emptyList()

        val (active, completed) = all.partition { !it.isComplete }
        val out = mutableListOf<GoalListItem>()

        // "Most urgent" = smallest daysRemaining, ties broken by higher
        // progress fraction (closer to completion) then earlier createdAt.
        val featured = active.minWithOrNull(
            compareBy<GoalWithProgress> { it.daysRemaining }
                .thenByDescending { it.progressFraction }
                .thenBy { it.goal.createdAt }
        )
        if (featured != null) {
            out += GoalListItem.Featured(featured)
        }

        val otherActive = active.filter { it !== featured }
        if (otherActive.isNotEmpty()) {
            out += GoalListItem.SectionHeader(
                labelRes = R.string.goals_active_title,
                countPill = "${otherActive.size} in progress",
            )
            otherActive.forEach { out += GoalListItem.Active(it) }
        }
        if (completed.isNotEmpty()) {
            out += GoalListItem.SectionHeader(labelRes = R.string.goals_completed_title)
            completed.forEach { out += GoalListItem.Completed(it) }
        }
        return out
    }

    /** Enough to re-create a deleted goal from Undo without another network trip. */
    data class GoalSnapshot(
        val title: String,
        val activityType: ActivityType?,
        val goalType: GoalType,
        val period: GoalPeriod,
        val targetValue: Double,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    sealed interface ErrorEvent {
        data class Network(@StringRes val messageRes: Int) : ErrorEvent
        data class Generic(@StringRes val messageRes: Int) : ErrorEvent
        data class DeletedWithUndo(val snapshot: GoalSnapshot) : ErrorEvent
        /** A goal just reached 100% — the fragment shows a celebration Snackbar. */
        data class GoalCompleted(val title: String) : ErrorEvent
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoalsViewModel(
                    goalRepository = container.goalRepository,
                    calculator = container.goalProgressCalculator,
                )
            }
        }
    }
}
