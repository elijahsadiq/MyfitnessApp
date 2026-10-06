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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    data class State(
        val periodBars: List<BarPoint>,
        val trendBars: List<BarPoint>,
        val perActivity: Map<ActivityType, ProgressStats>,
        val personalRecords: PersonalRecords,
        val isEmpty: Boolean,
    ) {
        companion object {
            val EMPTY = State(
                periodBars = emptyList(),
                trendBars = emptyList(),
                perActivity = emptyMap(),
                personalRecords = PersonalRecords.EMPTY,
                isEmpty = true,
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
                is ApiResult.Success -> _loadError.value = false
                is ApiResult.Error,
                ApiResult.NetworkError -> _loadError.value = true
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
        val trendBars = ProgressAnalytics.trendBars(
            workouts = if (activityType != null) inPeriod.filter { it.type == activityType } else inPeriod,
            activityType = activityType,
        )
        val perActivity = ActivityType.entries.associateWith { type ->
            ProgressAnalytics.statsFor(inPeriod, type)
        }
        val personalRecords = ProgressAnalytics.personalRecords(all)

        val isEmpty = inPeriod.isEmpty() && perActivity.values.all { it.workoutCount == 0 }
        return State(
            periodBars = periodBars,
            trendBars = trendBars,
            perActivity = perActivity,
            personalRecords = personalRecords,
            isEmpty = isEmpty,
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
