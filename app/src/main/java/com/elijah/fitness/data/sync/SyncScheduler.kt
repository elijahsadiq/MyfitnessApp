package com.elijah.fitness.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Asks for the offline changes to be uploaded. An interface so the
 * repository tests can count calls without WorkManager.
 */
interface SyncScheduler {
    /** Upload pending changes as soon as the phone is online. */
    fun schedule()

    /** Drop any queued upload (sign-out). */
    fun cancel()
}

/**
 * WorkManager-backed [SyncScheduler].
 *
 * The request only runs while the phone has a network connection, and
 * WorkManager keeps it across app restarts and reboots. That's the
 * "sync once connectivity is restored" behaviour: a workout saved in a
 * tunnel uploads when the signal comes back, even if the app was
 * closed in between.
 *
 * `APPEND_OR_REPLACE` queues a new run behind one already running, so a
 * workout saved while a sync is mid-way still gets its own upload pass.
 */
class WorkManagerSyncScheduler(context: Context) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "workout-sync"
        const val BACKOFF_SECONDS = 30L
    }
}
