package com.elijah.fitness.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elijah.fitness.AppContainer
import com.elijah.fitness.analytics.ActivityFilter
import com.elijah.fitness.analytics.AnalyticsPeriod
import com.elijah.fitness.analytics.BarPoint
import com.elijah.fitness.analytics.PersonalRecords
import com.elijah.fitness.analytics.ProgressAnalytics
import com.elijah.fitness.analytics.ProgressStats
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
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
 * State holder for the Progress tab. Composes:
 *   - `period`      — current period selector (Week / Month / Year)
 *   - `filter`      — current activity chip (All / Running / Cycling / Weightlifting)
 *   - `workouts`    — the live stream from the repository
 *
 * …into a derived [State] that the fragment renders.
 */
class ProgressViewModel(
    private val container: AppContainer,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _period = MutableStateFlow(AnalyticsPeriod.WEEK)
    val period: StateFlow<AnalyticsPeriod> = _period.asStateFlow()

    private val _filter = MutableStateFlow(ActivityFilter.ALL)
    val filter: StateFlow<ActivityFilter> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** One-shot load failures — a Snackbar each, not a sticky flag. */
    private val _loadErrors = Channel<Unit>(Channel.CONFLATED)
    val loadErrors: Flow<Unit> = _loadErrors.receiveAsFlow()

    data class State(
        val periodBars: List<BarPoint>,
        /** Stats cards to show — only the chip's activity unless "All". */
        val perActivity: Map<ActivityType, ProgressStats>,
        val personalRecords: PersonalRecords,
        /** Nothing logged in the selected period. */
        val isEmpty: Boolean,
        /** Anything logged at all (picks the empty-state wording). */
        val hasAnyWorkouts: Boolean,
    ) {
        companion object {
            val EMPTY = State(
                periodBars = emptyList(),
                perActivity = emptyMap(),
                personalRecords = PersonalRecords.EMPTY,
                isEmpty = true,
                hasAnyWorkouts = false,
            )
        }
    }

    val state: StateFlow<State> = combine(
        container.workoutRepository.observeWorkouts(),
        _period,
        _filter,
    ) { workouts, period, filter ->
        buildState(workouts, period, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = State.EMPTY,
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            when (container.workoutRepository.refresh()) {
                is ApiResult.Success -> Unit
                is ApiResult.Error,
                ApiResult.NetworkError -> _loadErrors.trySend(Unit)
            }
            _isLoading.value = false
        }
    }

    fun setPeriod(period: AnalyticsPeriod) { _period.value = period }
    fun setFilter(filter: ActivityFilter) { _filter.value = filter }

    private fun buildState(
        all: List<Workout>,
        period: AnalyticsPeriod,
        filter: ActivityFilter,
    ): State {
        if (all.isEmpty()) return State.EMPTY

        val now = clock.instant()
        val inPeriod = ProgressAnalytics.periodWorkouts(all, period, now, zone)
        val activityType = filter.asActivityType()

        val periodBars = ProgressAnalytics.activityBars(
            workouts = inPeriod,
            period = period,
            activityType = activityType,
            now = now,
            zone = zone,
        )
        // The activity chip filters the stats cards as well as the chart.
        val perActivity = ActivityType.entries
            .filter { activityType == null || it == activityType }
            .associateWith { type -> ProgressAnalytics.statsFor(inPeriod, type) }
        val personalRecords = ProgressAnalytics.personalRecords(all)

        val isEmpty = inPeriod.isEmpty()
        return State(
            periodBars = periodBars,
            perActivity = perActivity,
            personalRecords = personalRecords,
            isEmpty = isEmpty,
            hasAnyWorkouts = true,
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProgressViewModel(container) as T
            }
    }
}
