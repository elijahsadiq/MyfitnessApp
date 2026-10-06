package com.elijah.fitness.ui.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.insights.Insight
import com.elijah.fitness.insights.InsightEngine
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * State holder for the Home ("Dashboard") screen.
 *
 * Step 9 additions:
 *  - [homeUi] — packages everything the Home layout needs: the summary,
 *    the current week's Monday + today, and the set of days that already
 *    have workouts (drives the week strip's lime dots).
 *  - Goals are hardcoded placeholders per the phase-2 brief; real goals
 *    arrive in phase 5.
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

    val summary: StateFlow<WeeklySummary> = combine(
        repository.observeWorkouts(),
        userSettings.bodyWeightKg,
        userSettings.heightCm,
    ) { workouts, weight, height ->
        WeeklySummaryCalculator.compute(
            workouts = workouts,
            now = clock.instant(),
            zone = zone,
            bodyWeightKg = weight,
            heightCm = height,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WeeklySummary.EMPTY,
    )

    val homeUi: StateFlow<HomeUi> = repository.observeWorkouts()
        .map { workouts -> buildHomeUi(workouts) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUi.EMPTY,
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
     * open. New sessions already got these on login/register; this covers
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

    private fun buildHomeUi(workouts: List<Workout>): HomeUi {
        val today = LocalDate.ofInstant(clock.instant(), zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfWeek = monday.atStartOfDay(zone).toInstant()
        val daysWithWorkouts = workouts
            .filter { !it.startTime.isBefore(startOfWeek) }
            .map { LocalDate.ofInstant(it.startTime, zone).dayOfWeek }
            .toSet()
        return HomeUi(monday = monday, today = today, daysWithWorkouts = daysWithWorkouts)
    }

    data class HomeUi(
        val monday: LocalDate,
        val today: LocalDate,
        val daysWithWorkouts: Set<DayOfWeek>,
    ) {
        companion object {
            val EMPTY = HomeUi(
                monday = LocalDate.MIN,
                today = LocalDate.MIN,
                daysWithWorkouts = emptySet(),
            )
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
