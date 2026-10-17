package com.elijah.fitness.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.dto.DeleteAccountRequestDto
import com.elijah.fitness.data.remote.dto.UpdateProfileRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import com.elijah.fitness.notifications.ReminderSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalTime

/**
 * Profile screen state holder.
 *
 * Reads the real `GET /me` on init (amendment B — the previous Profile
 * screen rendered fake data from `profile_default_name` / `_email` strings
 * which would have shown "Alex Runner" to every marker that registered
 * a new account).
 */
class ProfileViewModel(
    private val container: AppContainer,
) : ViewModel() {

    data class Totals(val workouts: Int, val distanceMeters: Double)

    private val _user: MutableStateFlow<UserDto?> = MutableStateFlow(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAvatarUploading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAvatarUploading: StateFlow<Boolean> = _isAvatarUploading.asStateFlow()

    /** Lifetime totals computed from the already-fetched workout list. */
    val totals: StateFlow<Totals> = container.workoutRepository.observeWorkouts()
        .map { workouts ->
            Totals(
                workouts = workouts.size,
                distanceMeters = workouts.filterIsInstance<DistanceWorkout>()
                    .sumOf { it.distanceMeters },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Totals(0, 0.0),
        )

    private val _events: Channel<Event> = Channel(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** Phase 10: daily reminder + goal alert switches. */
    val notificationSettings: StateFlow<ReminderSettings> = container.reminderSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ReminderSettings(),
        )

    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch {
            container.reminderSettingsStore.setDailyReminder(enabled)
            if (enabled) {
                val time = container.reminderSettingsStore.settings.first().reminderTime
                container.dailyReminderScheduler.schedule(time)
            } else {
                container.dailyReminderScheduler.cancel()
            }
        }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch {
            container.reminderSettingsStore.setReminderTime(time)
            if (container.reminderSettingsStore.settings.first().dailyReminder) {
                container.dailyReminderScheduler.schedule(time)
            }
        }
    }

    fun setGoalAlerts(enabled: Boolean) {
        viewModelScope.launch { container.reminderSettingsStore.setGoalAlerts(enabled) }
    }

    /** Workouts saved on this phone that the server doesn't have yet. */
    suspend fun unsyncedWorkoutCount(): Int =
        container.workoutRepository.observeUnsyncedIds().first().size

    sealed interface Event {
        data object Saved : Event
        data class SaveFailed(val fieldErrors: Map<String, String>) : Event
        data object AvatarUpdated : Event
        data object AvatarRemoved : Event
        data object AvatarFailed : Event
        data object AccountDeleted : Event
        data class DeleteFailed(val message: String) : Event
        data object Network : Event
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = apiCall { container.userApi.me() }) {
                is ApiResult.Success -> {
                    _user.value = result.data
                    container.userSettings.hydrateFrom(
                        weightKg = result.data.weightKg,
                        heightCm = result.data.heightCm,
                    )
                }
                is ApiResult.Error -> _events.send(Event.SaveFailed(result.fieldErrors))
                ApiResult.NetworkError -> _events.send(Event.Network)
            }
            _isLoading.value = false
        }
    }

    fun updateProfile(fullName: String, weightKg: Double?, heightCm: Double?) {
        val current = _user.value ?: return
        viewModelScope.launch {
            val body = UpdateProfileRequestDto(
                fullName = fullName.trim(),
                weightKg = weightKg ?: current.weightKg,
                heightCm = heightCm ?: current.heightCm,
            )
            when (val result = apiCall { container.userApi.updateMe(body) }) {
                is ApiResult.Success -> {
                    _user.value = result.data
                    container.userSettings.hydrateFrom(
                        weightKg = result.data.weightKg,
                        heightCm = result.data.heightCm,
                    )
                    _events.send(Event.Saved)
                }
                is ApiResult.Error -> _events.send(Event.SaveFailed(result.fieldErrors))
                ApiResult.NetworkError -> _events.send(Event.Network)
            }
        }
    }

    fun uploadAvatar(fileBytes: ByteArray, mimeType: String) {
        if (_isAvatarUploading.value) return
        viewModelScope.launch {
            _isAvatarUploading.value = true
            val mediaType = (mimeType.takeIf { it.isNotBlank() } ?: "image/jpeg").toMediaType()
            val part = MultipartBody.Part.createFormData(
                "file", "avatar", fileBytes.toRequestBody(mediaType),
            )
            when (val result = apiCall { container.userApi.uploadAvatar(part) }) {
                is ApiResult.Success -> {
                    _user.value = result.data
                    _events.send(Event.AvatarUpdated)
                }
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.AvatarFailed)
            }
            _isAvatarUploading.value = false
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            when (val result = apiCall { container.userApi.deleteAvatar() }) {
                is ApiResult.Success -> {
                    _user.value = result.data
                    _events.send(Event.AvatarRemoved)
                }
                is ApiResult.Error,
                ApiResult.NetworkError -> _events.send(Event.AvatarFailed)
            }
        }
    }

    /**
     * Permanent delete, confirmed by the user typing
     * [DeleteAccountRequestDto.CONFIRM_WORD]. On success clears the local
     * token so the next screen is Auth, and sends an `AccountDeleted`
     * event so the fragment bounces to
     * [com.elijah.fitness.ui.launcher.LauncherActivity].
     */
    fun deleteAccount() {
        viewModelScope.launch {
            val body = DeleteAccountRequestDto(confirm = DeleteAccountRequestDto.CONFIRM_WORD)
            when (val result = apiCall { container.userApi.deleteMe(body) }) {
                is ApiResult.Success -> {
                    container.tokenStore.clear()
                    _events.send(Event.AccountDeleted)
                }
                is ApiResult.Error -> _events.send(
                    Event.DeleteFailed(
                        result.fieldErrors["confirm"] ?: result.message,
                    )
                )
                ApiResult.NetworkError -> _events.send(Event.Network)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProfileViewModel(container) as T
            }
    }
}
