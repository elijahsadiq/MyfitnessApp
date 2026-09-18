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
import com.elijah.fitness.data.model.DistanceWorkout
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
 *   total distance, per-day bar values, trend vs previous week).
 * - [sectioned] — `HistoryItem`s split into "This week" + "Earlier" so
 *   the adapter can render section headers with per-week summaries.
 */
class HistoryViewModel(
    private val repository: WorkoutRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    enum class Filter { ALL, RUNNING, CYCLING, WEIGHTLIFTING }

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    /** All workouts (unfiltered) — used by the momentum card. */
    private val allWorkouts: StateFlow<List<Workout>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Filtered list (respects the chip filter) used to build [sectioned]. */
    private val filteredWorkouts: StateFlow<List<Workout>> = combine(
        allWorkouts,
        _filter,
    ) { workouts, filter ->
        when (filter) {
            Filter.ALL -> workouts
            Filter.RUNNING -> workouts.filter { it.type == ActivityType.RUNNING }
            Filter.CYCLING -> workouts.filter { it.type == ActivityType.CYCLING }
            Filter.WEIGHTLIFTING -> workouts.filter { it.type == ActivityType.WEIGHTLIFTING }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Backwards-compat alias — old tests + fragment consumed `workouts`. */
    val workouts: StateFlow<List<Workout>> get() = filteredWorkouts

    /** Count of workouts overall (badge on the "All" chip). */
    val totalCount: StateFlow<Int> = allWorkouts
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val momentum: StateFlow<Momentum> = allWorkouts
        .map { computeMomentum(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Momentum.EMPTY)

    /**
     * Sectioned view of the current filter — a flat list of `HistoryItem`s
     * the RecyclerView adapter can render row-by-row. Two sections in
     * phase 2: "This week" and "Earlier".
     */
    val sectioned: StateFlow<List<HistoryItem>> = filteredWorkouts
        .map { workouts -> buildSections(workouts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init { refresh() }

    fun setFilter(filter: Filter) {
        _filter.value = filter
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
        val today = LocalDate.ofInstant(clock.instant(), zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevMonday = monday.minusWeeks(1)
        val startOfWeek = monday.atStartOfDay(zone).toInstant()
        val startOfPrevWeek = prevMonday.atStartOfDay(zone).toInstant()

        val perDay = DoubleArray(7)
        var thisWeekMeters = 0.0
        var lastWeekMeters = 0.0

        workouts.forEach { workout ->
            val distanceMeters = (workout as? DistanceWorkout)?.distanceMeters ?: 0.0
            when {
                !workout.startTime.isBefore(startOfWeek) -> {
                    val dayIndex = LocalDate.ofInstant(workout.startTime, zone)
                        .dayOfWeek.value - 1  // Mon = 0
                    if (dayIndex in 0..6) perDay[dayIndex] += distanceMeters
                    thisWeekMeters += distanceMeters
                }
                !workout.startTime.isBefore(startOfPrevWeek) -> {
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

    private fun buildSections(workouts: List<Workout>): List<HistoryItem> {
        if (workouts.isEmpty()) return emptyList()
        val today = LocalDate.ofInstant(clock.instant(), zone)
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant()
        val (thisWeek, earlier) = workouts.partition { !it.startTime.isBefore(startOfWeek) }

        val items = mutableListOf<HistoryItem>()
        if (thisWeek.isNotEmpty()) {
            items += HistoryItem.SectionHeader(
                labelRes = R.string.history_section_this_week,
                count = thisWeek.size,
                distanceMeters = thisWeek.filterIsInstance<DistanceWorkout>().sumOf { it.distanceMeters },
            )
            thisWeek.forEach { items += HistoryItem.Row(it) }
        }
        if (earlier.isNotEmpty()) {
            items += HistoryItem.SectionHeader(
                labelRes = R.string.history_section_earlier,
                count = earlier.size,
                distanceMeters = earlier.filterIsInstance<DistanceWorkout>().sumOf { it.distanceMeters },
            )
            earlier.forEach { items += HistoryItem.Row(it) }
        }
        return items
    }

    // ---------- events + data classes ----------

    sealed interface ErrorEvent {
        data class Network(@StringRes val messageRes: Int) : ErrorEvent
        data class Generic(@StringRes val messageRes: Int) : ErrorEvent
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
                HistoryViewModel(container.workoutRepository)
            }
        }
    }
}
