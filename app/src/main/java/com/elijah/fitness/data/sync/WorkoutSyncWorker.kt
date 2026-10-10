package com.elijah.fitness.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.first

/**
 * Background job that uploads workouts saved, edited or deleted while
 * the phone was offline. WorkManager starts it once a network is
 * available (see [WorkManagerSyncScheduler]).
 *
 * It uses the same repository instance as the screens, so its writes
 * reach the UI through the usual Room flows and the repository's lock
 * keeps it from racing a refresh.
 */
class WorkoutSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FitnessApp).container
        // Signed out since the job was queued: nothing to upload to.
        if (container.tokenStore.token.first() == null) return Result.success()
        return when (container.workoutRepository.syncPending()) {
            is ApiResult.Success -> Result.success()
            // Connection dropped again or the server is waking up:
            // WorkManager retries with exponential backoff.
            is ApiResult.Error, ApiResult.NetworkError -> Result.retry()
        }
    }
}
