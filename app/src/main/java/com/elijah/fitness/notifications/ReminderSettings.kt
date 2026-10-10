package com.elijah.fitness.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs",
)

/**
 * The user's notification choices.
 *
 * @property dailyReminder off until the user turns it on in Profile, so
 *   nobody gets a nudge they didn't ask for.
 * @property reminderTime local time of day the reminder fires.
 * @property goalAlerts "Goal achieved" notifications; on by default
 *   (they only appear once notification permission is granted).
 */
data class ReminderSettings(
    val dailyReminder: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_TIME,
    val goalAlerts: Boolean = true,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)
    }
}

/** Persistent home for [ReminderSettings]. An interface so tests can fake it. */
interface ReminderSettingsStore {
    val settings: Flow<ReminderSettings>
    suspend fun setDailyReminder(enabled: Boolean)
    suspend fun setReminderTime(time: LocalTime)
    suspend fun setGoalAlerts(enabled: Boolean)

    /** Back to the defaults (sign-out). */
    suspend fun reset()
}

class DataStoreReminderSettingsStore(context: Context) : ReminderSettingsStore {

    private val store = context.applicationContext.notificationDataStore

    override val settings: Flow<ReminderSettings> = store.data.map { prefs ->
        ReminderSettings(
            dailyReminder = prefs[DAILY_REMINDER] ?: false,
            reminderTime = prefs[REMINDER_MINUTE_OF_DAY]
                ?.let { LocalTime.of(it / 60, it % 60) }
                ?: ReminderSettings.DEFAULT_TIME,
            goalAlerts = prefs[GOAL_ALERTS] ?: true,
        )
    }

    override suspend fun setDailyReminder(enabled: Boolean) {
        store.edit { it[DAILY_REMINDER] = enabled }
    }

    override suspend fun setReminderTime(time: LocalTime) {
        store.edit { it[REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute }
    }

    override suspend fun setGoalAlerts(enabled: Boolean) {
        store.edit { it[GOAL_ALERTS] = enabled }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val DAILY_REMINDER = booleanPreferencesKey("daily_reminder")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val GOAL_ALERTS = booleanPreferencesKey("goal_alerts")
    }
}
