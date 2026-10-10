package com.elijah.fitness.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Queues the daily reminder as a one-off WorkManager job due at the next
 * reminder time. The job books the following day itself when it runs
 * (see [DailyReminderWorker]).
 *
 * A one-off job per day stays close to the chosen time; a 24-hour
 * periodic job is allowed to drift by hours. WorkManager keeps the job
 * across reboots, so nothing else is needed to bring it back.
 */
class DailyReminderScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(at: LocalTime, now: ZonedDateTime = ZonedDateTime.now()) {
        val delay = ReminderPlanner.delayUntilNext(now, at)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "daily-reminder"
    }
}
