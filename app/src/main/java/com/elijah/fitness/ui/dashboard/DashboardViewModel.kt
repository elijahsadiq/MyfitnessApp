package com.elijah.fitness.ui.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.insights.Insight
import com.elijah.fitness.insights.InsightEngine
import com.elijah.fitness.ui.history.HistoryItem
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * State holder for the Home ("Dashboard") screen.
 *
 * Phase 11: the calendar picks a day ([selectDate]). Everything below it
 * follows that pick — [calendar] (strip / month, dots), [summary] (the
 * hero card: the picked day's week) and [day] (that day's workouts).
 * With nothing picked, all three follow today, even across midnight.
 */
class DashboardViewModel(
    private val repository: WorkoutRepository,
    private val userSettings: UserSettings,
    private val userApi: UserApi,
    private val goalProgressCalculator: GoalProgressCalculator,
    private val insightEngine: InsightEngine,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** Day picked on the calendar; `null` means "today". */
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _calendarExpanded = MutableStateFlow(false)

    /** Whether the calendar shows the whole month (kept across rotation). */
    val calendarExpanded: StateFlow<Boolean> = _calendarExpanded.asStateFlow()

    val calendar: StateFlow<CalendarUi> = combine(
        repository.observeWorkouts(),
        selectedDate,
    ) { workouts, selected ->
        val today = today()
        CalendarUi(
            today = today,
            selected = selected ?: today,
            workoutDays = CalendarMath.workoutDays(workouts, zone),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = today().let { CalendarUi(it, it, emptySet()) },
    )

    /** Hero card numbers for the picked day's week. The streak always counts back from today. */
    val summary: StateFlow<WeeklySummary> = combine(
        repository.observeWorkouts(),
        userSettings.bodyWeightKg,
        userSettings.heightCm,
        selectedDate,
    ) { workouts, weight, height, selected ->
        WeeklySummaryCalculator.compute(
            workouts = workouts,
            now = clock.instant(),
            zone = zone,
            bodyWeightKg = weight,
            heightCm = height,
            weekOf = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WeeklySummary.EMPTY,
    )

    /** The picked day's workouts, as History rows. */
    val day: StateFlow<DayUi> = combine(
        repository.observeWorkouts(),
        repository.observeUnsyncedIds(),
        selectedDate,
    ) { workouts, unsynced, selected ->
        val today = today()
        val date = selected ?: today
        val onDay = CalendarMath.workoutsOn(workouts, date, zone)
        DayUi(
            date = date,
            today = today,
            rows = onDay.map { HistoryItem.Row(it, it.id in unsynced) },
            distanceMeters = onDay.filterIsInstance<DistanceWorkout>().sumOf { it.distanceMeters },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = today().let { DayUi(it, it, emptyList(), 0.0) },
    )

    // Phase-8 note: the Home Goals section was removed. The Goals tab is
    // one bottom-nav tap away and the duplicate was double-rendering the
    // same data. Keeping Home focused on the week's momentum tightens
    // the first-impression surface.

    /**
     * Top-ranked insight for the Home card (phase 8 step 6). `null` when
     * no rule fires — the fragment shows the fallback copy in that case.
     * Rebuilt whenever either stream changes.
     */
    val topInsight: StateFlow<Insight?> = combine(
        repository.observeWorkouts(),
        goalProgressCalculator.observe(),
    ) { workouts, goals ->
        insightEngine.rank(workouts, goals).firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvents = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<ErrorEvent> = _errorEvents.receiveAsFlow()

    init {
        refresh()
        refreshProfile()
    }

    /** Pick a day on the calendar. Future days fall back to today. */
    fun selectDate(date: LocalDate) {
        val today = today()
        val clamped = CalendarMath.clamp(date, today)
        selectedDate.value = clamped.takeIf { it != today }
    }

    fun setCalendarExpanded(expanded: Boolean) {
        _calendarExpanded.value = expanded
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

    /**
     * Hydrate the local body-metrics cache from `/me` on every dashboard
     * open. New sessions already got these on login; this covers
     * the case of an existing token that pre-dates the BMI feature and
     * so has an empty height in [UserSettings]. Silent on failure — the
     * BMI tile just stays in its "missing" state.
     */
    private fun refreshProfile() {
        viewModelScope.launch {
            val result = apiCall { userApi.me() }
            if (result is ApiResult.Success) {
                userSettings.hydrateFrom(
                    weightKg = result.data.weightKg,
                    heightCm = result.data.heightCm,
                )
            }
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    /** The workout list under the hero card. */
    data class DayUi(
        val date: LocalDate,
        val today: LocalDate,
        val rows: List<HistoryItem.Row>,
        val distanceMeters: Double,
    ) {
        val isToday: Boolean get() = date == today
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
            zone: ZoneId = ZoneId.systemDefault(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    repository = container.workoutRepository,
                    userSettings = container.userSettings,
                    userApi = container.userApi,
                    goalProgressCalculator = container.goalProgressCalculator,
                    insightEngine = container.insightEngine,
                    clock = clock,
                    zone = zone,
                )
            }
        }
    }
}
