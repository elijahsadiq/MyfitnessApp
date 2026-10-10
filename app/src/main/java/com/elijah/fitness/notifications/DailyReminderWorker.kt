package com.elijah.fitness.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elijah.fitness.FitnessApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fires at the reminder time. If nothing has been logged today it posts
 * the reminder, then books tomorrow's.
 *
 * It reads workouts from the on-phone database, so it works with no
 * connection and never wakes the server.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FitnessApp).container
        val settings = container.reminderSettingsStore.settings.first()
        // Turned off, or signed out: stop the daily chain here.
        if (!settings.dailyReminder) return Result.success()
        if (container.tokenStore.token.first() == null) return Result.success()

        val zone = ZoneId.systemDefault()
        val workouts = container.workoutRepository.observeWorkouts().first()
        val weekCount = ReminderPlanner.workoutsThisWeekIfNoneToday(
            workouts = workouts,
            today = LocalDate.now(zone),
            zone = zone,
        )
        if (weekCount != null) AppNotifications.showDailyReminder(applicationContext, weekCount)

        container.dailyReminderScheduler.schedule(settings.reminderTime)
        return Result.success()
    }
}
