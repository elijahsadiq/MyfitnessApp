package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalDeleteCoordinator
import com.elijah.fitness.data.repository.GoalRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the Goals screen (phase-8b).
 *
 * ### Data flow
 * `GoalProgressCalculator.observe()` emits a snapshot of every goal with
 * its current progress + status + Manage-sheet stats. This VM combines
 * that stream with [GoalDeleteCoordinator.hiddenIds] so a goal in the
 * middle of a pending-delete Undo window disappears from the UI without
 * waiting for the server round-trip.
 *
 * The output is a flat `List<GoalListItem>`:
 *
 *   Featured (dark-card main goal)
 *   Section header "ACTIVE GOALS" (+ count pill)
 *   Active rows
 *   Section header "COMPLETED"
 *   Completed rows
 *   Section header "ENDED"
 *   Ended rows
 */
class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val calculator: GoalProgressCalculator,
    private val deleteCoordinator: GoalDeleteCoordinator,
) : ViewModel() {

    val items: StateFlow<List<GoalListItem>> = combine(
        calculator.observe(),
        deleteCoordinator.hiddenIds,
    ) { snapshots, hidden ->
        buildItems(snapshots.filterNot { it.goal.id in hidden })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    private val celebratedThisSession: MutableSet<String> = mutableSetOf()
    private var completionsSeeded = false

    init {
        refresh()
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
     * Phase-8b delete: hide the goal immediately via the coordinator;
     * the real DELETE fires 5 s later on an app-level scope, unless
     * [undoDelete] is called first. The fragment shows the Snackbar.
     */
    fun deleteWithUndo(goal: Goal) {
        deleteCoordinator.scheduleDelete(goal)
        viewModelScope.launch {
            _errorEvents.send(ErrorEvent.DeletedWithUndo(goal.id, goal.displayTitle()))
        }
    }

    /** Cancel a pending delete; the goal reappears on the next emission. */
    fun undoDelete(goalId: String) {
        deleteCoordinator.undo(goalId)
    }

    /**
     * Called when the Snackbar fully dismisses (either timed out OR
     * actively undone). No-op here — the coordinator already fired or
     * cancelled based on which action happened; the Snackbar lifecycle
     * is purely visual.
     */
    private fun detectCompletions(snapshots: List<GoalWithProgress>) {
        if (!completionsSeeded) {
            snapshots.filter { it.isCompleted && !it.goal.completedNotified }
                .forEach { announceCompletion(it) }
            snapshots.filter { it.isCompleted }.forEach { celebratedThisSession.add(it.goal.id) }
            completionsSeeded = true
            return
        }
        snapshots.filter {
            it.isCompleted && it.goal.id !in celebratedThisSession
        }.forEach { announceCompletion(it) }
    }

    private fun announceCompletion(gwp: GoalWithProgress) {
        celebratedThisSession.add(gwp.goal.id)
        viewModelScope.launch {
            _errorEvents.send(ErrorEvent.GoalCompleted(gwp.goal.displayTitle()))
            goalRepository.markCompletedNotified(gwp.goal.id)
        }
    }

    /**
     * Call `makeMainGoal` on the repository and let `observeGoals()`
     * re-emit with the updated `isMain`.
     */
    fun makeMainGoal(id: String) {
        viewModelScope.launch {
            when (goalRepository.makeMainGoal(id)) {
                is ApiResult.Success -> _errorEvents.send(ErrorEvent.MainGoalChanged)
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.auth_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.goals_error_load_failed))
            }
        }
    }

    fun extendGoal(id: String, days: Int) {
        viewModelScope.launch {
            when (goalRepository.extendGoal(id, days)) {
                is ApiResult.Success -> _errorEvents.send(ErrorEvent.Extended)
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.auth_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.goals_error_load_failed))
            }
        }
    }

    private fun buildItems(all: List<GoalWithProgress>): List<GoalListItem> {
        if (all.isEmpty()) {
            // Phase-8b step 8: Header + dark hero card + "Quick start"
            // section with three pre-filled suggestions. The list carries
            // the full empty-state composition so the fragment is a single
            // RecyclerView — no visibility-toggled sibling view.
            return buildList {
                add(GoalListItem.Header(activeCount = 0, completedCount = 0))
                add(GoalListItem.EmptyHeroCard)
                add(GoalListItem.SectionHeader(
                    labelRes = R.string.goals_quick_start_title,
                ))
                GoalSuggestion.DEFAULTS.forEach { add(GoalListItem.QuickStart(it)) }
            }
        }

        val active = all.filter { it.isActive }
        val completed = all.filter { it.isCompleted }
        val ended = all.filter { it.isEnded }

        val out = mutableListOf<GoalListItem>()
        out += GoalListItem.Header(
            activeCount = active.size,
            completedCount = completed.size,
        )

        // Dark-card (MainGoalCard) selection — Phase 8b:
        //   1. The user's main goal if it's active.
        //   2. Otherwise the active goal with the fewest days left,
        //      tie-breaking on higher progress then earlier createdAt.
        //   3. If there are no active goals, no dark card is shown
        //      (completed-only / ended-only lists never feature).
        val featured = active.firstOrNull { it.isMain }
            ?: active.minWithOrNull(
                compareBy<GoalWithProgress> { it.daysLeft }
                    .thenByDescending { it.progressFraction }
                    .thenBy { it.goal.createdAt }
            )
        if (featured != null) {
            out += GoalListItem.MainGoalCard(featured)
        }

        val otherActive = active.filter { it !== featured }
        if (otherActive.isNotEmpty()) {
            out += GoalListItem.SectionHeader(
                labelRes = R.string.goals_active_title,
                countPill = otherActive.size.toString(),
            )
            otherActive.forEach { out += GoalListItem.Active(it) }
        }
        if (completed.isNotEmpty()) {
            out += GoalListItem.SectionHeader(
                labelRes = R.string.goals_completed_title,
                countPill = completed.size.toString(),
            )
            completed.forEach { out += GoalListItem.Completed(it) }
        }
        if (ended.isNotEmpty()) {
            out += GoalListItem.SectionHeader(
                labelRes = R.string.goals_ended_title,
                countPill = ended.size.toString(),
            )
            ended.forEach { out += GoalListItem.Ended(it) }
        }
        return out
    }

    sealed interface ErrorEvent {
        data class Network(@StringRes val messageRes: Int) : ErrorEvent
        data class Generic(@StringRes val messageRes: Int) : ErrorEvent
        /**
         * Phase-8b: carries the goal's id (for Undo) and title (for the
         * Snackbar copy). The actual DELETE is already scheduled on the
         * app-level scope.
         */
        data class DeletedWithUndo(val goalId: String, val title: String) : ErrorEvent
        data class GoalCompleted(val title: String) : ErrorEvent
        /** The server confirmed a new main goal. */
        data object MainGoalChanged : ErrorEvent
        /** The server confirmed "Add 7 more days". */
        data object Extended : ErrorEvent
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoalsViewModel(
                    goalRepository = container.goalRepository,
                    calculator = container.goalProgressCalculator,
                    deleteCoordinator = container.goalDeleteCoordinator,
                )
            }
        }
    }
}
