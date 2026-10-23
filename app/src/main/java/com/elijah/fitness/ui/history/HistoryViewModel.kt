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
import com.elijah.fitness.data.model.DistanceUnit
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.WorkoutDeleteCoordinator
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * State holder for the workout history screen.
 *
 * Step 9 additions:
 * - [momentum] — data for the dark "Weekly momentum" card (this week's
 *   total distance, per-day bar values, trend vs the same point last week).
 * - [sectioned] — `HistoryItem`s split into "This week" + "Earlier" so
 *   the adapter can render section headers with per-week summaries.
 */
class HistoryViewModel(
    private val repository: WorkoutRepository,
    private val deleteCoordinator: WorkoutDeleteCoordinator,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** Chip filter, in the order the chips show. `ALL` has no activity. */
    enum class Filter(val activityType: ActivityType?) {
        ALL(null),
        RUNNING(ActivityType.RUNNING),
        CYCLING(ActivityType.CYCLING),
        SWIMMING(ActivityType.SWIMMING),
        ROWING(ActivityType.ROWING),
        WEIGHTLIFTING(ActivityType.WEIGHTLIFTING),
    }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    /**
     * All workouts (unfiltered), minus any that are currently in the
     * "pending delete" window. The hidden-ids filter runs here so **both**
     * the momentum card and the sectioned list drop a swiped workout the
     * moment the user swipes, long before the server-side DELETE fires.
     */
    private val allWorkouts: StateFlow<List<Workout>> = combine(
        repository.observeWorkouts(),
        deleteCoordinator.hiddenIds,
    ) { workouts, hidden -> workouts.filterNot { it.id in hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Filtered list (respects the chip filter) used to build [sectioned]. */
    private val filteredWorkouts: StateFlow<List<Workout>> = combine(
        allWorkouts,
        _filter,
    ) { workouts, filter ->
        val type = filter.activityType
        if (type == null) workouts else workouts.filter { it.type == type }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Backwards-compat alias — old tests + fragment consumed `workouts`. */
    val workouts: StateFlow<List<Workout>> get() = filteredWorkouts

    val momentum: StateFlow<Momentum> = allWorkouts
        .map { computeMomentum(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Momentum.EMPTY)

    /**
     * Sectioned view of the current filter — a flat list of `HistoryItem`s
     * the RecyclerView adapter can render row-by-row. Two sections in
     * phase 2: "This week" and "Earlier".
     */
    val sectioned: StateFlow<List<HistoryItem>> = combine(
        filteredWorkouts,
        repository.observeUnsyncedIds(),
    ) { workouts, unsynced -> buildSections(workouts, unsynced) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init { refresh() }

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    /**
     * Swipe-to-delete: hide the workout immediately and fire the real
     * DELETE after 5 s unless [undoDelete] is called first.
     */
    fun deleteWithUndo(workout: Workout) {
        deleteCoordinator.scheduleDelete(workout)
        viewModelScope.launch {
            _errorEvents.send(ErrorEvent.DeletedWithUndo(workout.id))
        }
    }

    /** Cancel a pending delete; the workout reappears on the next emission. */
    fun undoDelete(workoutId: String) {
        deleteCoordinator.undo(workoutId)
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            when (repository.refresh()) {
                is ApiResult.Success -> Unit
                ApiResult.NetworkError ->
                    _errorEvents.send(ErrorEvent.Network(R.string.workouts_error_network))
                is ApiResult.Error ->
                    _errorEvents.send(ErrorEvent.Generic(R.string.workouts_error_load_failed))
            }
            _isLoading.value = false
        }
    }

    // ---------- momentum + sectioning helpers ----------

    private fun computeMomentum(workouts: List<Workout>): Momentum {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevMonday = monday.minusWeeks(1)
        val startOfWeek = monday.atStartOfDay(zone).toInstant()
        val startOfPrevWeek = prevMonday.atStartOfDay(zone).toInstant()
        // Like-for-like trend: this week so far vs the same stretch of last
        // week (Monday up to this moment, 7 days ago). Comparing against
        // ALL of last week would read close to -100% every Monday.
        val sameTimeLastWeek = now.atZone(zone).minusWeeks(1).toInstant()

        val perDay = DoubleArray(7)
        var thisWeekMeters = 0.0
        var lastWeekMeters = 0.0

        workouts.forEach { workout ->
            val distanceMeters = (workout as? DistanceWorkout)?.distanceMeters ?: 0.0
            when {
                !workout.startTime.isBefore(startOfWeek) -> {
                    val dayIndex = workout.startTime.atZone(zone).toLocalDate()
                        .dayOfWeek.value - 1  // Mon = 0
                    if (dayIndex in 0..6) perDay[dayIndex] += distanceMeters
                    thisWeekMeters += distanceMeters
                }
                !workout.startTime.isBefore(startOfPrevWeek) &&
                    workout.startTime.isBefore(sameTimeLastWeek) -> {
                    lastWeekMeters += distanceMeters
                }
            }
        }

        val trend = if (lastWeekMeters > 0.0) {
            ((thisWeekMeters - lastWeekMeters) / lastWeekMeters * 100.0).toInt()
        } else null

        return Momentum(
            totalDistanceMeters = thisWeekMeters,
            perDayMeters = perDay,
            trendPercent = trend,
            activeDayIndex = today.dayOfWeek.value - 1,
        )
    }

    private fun buildSections(workouts: List<Workout>, unsynced: Set<String>): List<HistoryItem> {
        if (workouts.isEmpty()) return emptyList()
        val today = clock.instant().atZone(zone).toLocalDate()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant()
        val (thisWeek, earlier) = workouts.partition { !it.startTime.isBefore(startOfWeek) }

        val items = mutableListOf<HistoryItem>()
        if (thisWeek.isNotEmpty()) {
            items += sectionHeader(R.string.history_section_this_week, thisWeek)
            thisWeek.forEach { items += HistoryItem.Row(it, it.id in unsynced) }
        }
        if (earlier.isNotEmpty()) {
            items += sectionHeader(R.string.history_section_earlier, earlier)
            earlier.forEach { items += HistoryItem.Row(it, it.id in unsynced) }
        }
        return items
    }

    /**
     * The pill row above a section: how many workouts and how far. A
     * section of only swims totals in metres, like its swim rows.
     */
    private fun sectionHeader(@StringRes labelRes: Int, workouts: List<Workout>): HistoryItem.SectionHeader {
        val distanceWorkouts = workouts.filterIsInstance<DistanceWorkout>()
        return HistoryItem.SectionHeader(
            labelRes = labelRes,
            count = workouts.size,
            distanceMeters = distanceWorkouts.sumOf { it.distanceMeters },
            distanceUnit = DistanceUnit.forTotal(distanceWorkouts),
        )
    }

    // ---------- events + data classes ----------

    sealed interface ErrorEvent {
        data class Network(@StringRes val messageRes: Int) : ErrorEvent
        data class Generic(@StringRes val messageRes: Int) : ErrorEvent
        /**
         * The swipe just hid a workout; the server-side DELETE is already
         * scheduled on the app-level scope. Carries the id so the
         * fragment's Snackbar can offer Undo.
         */
        data class DeletedWithUndo(val workoutId: String) : ErrorEvent
    }

    data class Momentum(
        val totalDistanceMeters: Double,
        val perDayMeters: DoubleArray,
        /** null when there was no prior-week data to compare against. */
        val trendPercent: Int?,
        val activeDayIndex: Int,
    ) {
        companion object {
            val EMPTY = Momentum(0.0, DoubleArray(7), null, -1)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    repository = container.workoutRepository,
                    deleteCoordinator = container.workoutDeleteCoordinator,
                )
            }
        }
    }
}
