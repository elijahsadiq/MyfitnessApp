package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.db.SyncState
import com.elijah.fitness.data.local.db.WorkoutDao
import com.elijah.fitness.data.local.db.WorkoutWithSets
import com.elijah.fitness.data.local.db.setEntities
import com.elijah.fitness.data.local.db.toDomain
import com.elijah.fitness.data.local.db.toEntity
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.apiCallEmpty
import com.elijah.fitness.data.remote.mapper.toDomain
import com.elijah.fitness.data.remote.mapper.toRequestDto
import com.elijah.fitness.data.sync.SyncScheduler
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Offline-first [WorkoutRepository]: the Room database on the phone is
 * what every screen reads, and the PHP API is kept in step behind it.
 *
 * ### Writes
 * Saving, editing notes and deleting all change the local database
 * first, so they work with no connection and the screens update at
 * once. The row is marked with a [SyncState] and an upload is
 * attempted straight away. If the phone is offline (or the free server
 * is still waking up), [syncScheduler] queues a WorkManager job that
 * runs [syncPending] when a network is back.
 *
 * ### Reads
 * [refresh] first uploads pending changes, then pulls the server's list
 * and merges it in ([WorkoutDao.mergeServer]). Offline, it returns
 * [ApiResult.NetworkError] but the cached list stays on screen.
 *
 * ### Duplicates
 * Every workout carries its phone-generated `clientUuid`, and the
 * server's `POST /workouts` returns the existing row for a uuid it
 * already has. A retry after a lost response can't create a second
 * copy.
 *
 * ### Races
 * Everything that talks to the server holds [syncLock], so a refresh's
 * merge can never run between an upload and its "synced" mark. Local
 * writes don't take the lock (they must be instant); instead each one
 * bumps `localVersion`, and an upload only marks a row synced if the
 * version it sent is still current.
 */
class OfflineFirstWorkoutRepository(
    private val dao: WorkoutDao,
    private val api: WorkoutApi,
    private val syncScheduler: SyncScheduler,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean = { true },
    private val uploadWaitMs: Long = DEFAULT_UPLOAD_WAIT_MS,
) : WorkoutRepository {

    private val syncLock = Mutex()

    override fun observeWorkouts(): Flow<List<Workout>> =
        dao.observeVisible().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnsyncedIds(): Flow<Set<String>> =
        dao.observeUnsyncedIds().map { it.toSet() }

    override suspend fun refresh(): ApiResult<Unit> {
        if (syncPending() is ApiResult.NetworkError) return ApiResult.NetworkError
        return syncLock.withLock {
            when (val result = apiCall { api.list() }) {
                is ApiResult.Success -> {
                    dao.mergeServer(result.data.map { it.toEntity() to it.setEntities() })
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> result
                ApiResult.NetworkError -> ApiResult.NetworkError
            }
        }
    }

    /**
     * Saves on the phone, then gives the upload [uploadWaitMs] to finish
     * so an online save shows as synced straight away. Past that the
     * upload carries on in the background. The save itself never fails
     * for lack of a connection.
     */
    override suspend fun addWorkout(
        workout: Workout,
        routePoints: List<TrackPoint>,
    ): ApiResult<Unit> {
        val existing = dao.get(workout.id)?.workout
        // A double-tapped Save for a workout that's already uploaded.
        if (existing != null && existing.syncState != SyncState.PENDING_CREATE) {
            return ApiResult.Success(Unit)
        }
        dao.save(
            workout.toEntity(SyncState.PENDING_CREATE, routeCached = true),
            workout.setEntities(),
            routePoints.map { it.toEntity(workout.id) },
        )
        syncSoon()
        return ApiResult.Success(Unit)
    }

    override suspend fun getWorkout(id: String): Workout? =
        dao.get(id)
            ?.takeIf { it.workout.syncState != SyncState.PENDING_DELETE }
            ?.toDomain()

    /**
     * Route from the phone when it's there (every workout tracked here,
     * and any synced one opened before). Otherwise it's fetched once and
     * cached. Offline with nothing cached, the workout still opens, just
     * without its map.
     */
    override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail> {
        val row = dao.get(clientUuid)
            ?.takeIf { it.workout.syncState != SyncState.PENDING_DELETE }
            ?: return notFound()
        val workout = row.toDomain()
        if (row.workout.routeCached) {
            return ApiResult.Success(WorkoutDetail(workout, localRoute(clientUuid)))
        }
        val serverId = row.workout.serverId
            ?: return ApiResult.Success(WorkoutDetail(workout, emptyList()))
        return when (val result = apiCall { api.get(serverId) }) {
            is ApiResult.Success -> {
                val points = result.data.routePoints.sortedBy { it.seq }.map { it.toDomain() }
                dao.cacheRoute(clientUuid, points.map { it.toEntity(clientUuid) })
                ApiResult.Success(WorkoutDetail(workout, points))
            }
            is ApiResult.Error,
            ApiResult.NetworkError -> ApiResult.Success(WorkoutDetail(workout, emptyList()))
        }
    }

    override suspend fun deleteWorkout(clientUuid: String): ApiResult<Unit> {
        dao.get(clientUuid) ?: return notFound()
        dao.markDeleted(clientUuid)
        syncSoon()
        return ApiResult.Success(Unit)
    }

    override suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout> {
        dao.get(clientUuid) ?: return notFound()
        dao.editNotes(clientUuid, notes?.takeUnless { it.isBlank() })
        syncSoon()
        val updated = dao.get(clientUuid)?.toDomain() ?: return notFound()
        return ApiResult.Success(updated)
    }

    /**
     * Upload every pending change, oldest workout first.
     *
     * Returns [ApiResult.NetworkError] if the connection (or the server)
     * gave out part-way, so the caller can retry later. A row the server
     * rejects outright stays pending and doesn't block the rest.
     */
    override suspend fun syncPending(): ApiResult<Unit> = syncLock.withLock {
        // No network at all: don't send requests that can only fail.
        if (!isOnline()) return@withLock ApiResult.NetworkError
        repeat(MAX_SYNC_PASSES) {
            val pending = dao.pending()
            if (pending.isEmpty()) return@withLock ApiResult.Success(Unit)
            var uploaded = false
            for (row in pending) {
                when (upload(row)) {
                    Outcome.Done -> uploaded = true
                    Outcome.Offline -> return@withLock ApiResult.NetworkError
                    Outcome.Rejected -> Unit
                }
            }
            // Only rejected rows left: trying again now won't help.
            if (!uploaded) return@withLock ApiResult.Success(Unit)
        }
        ApiResult.Success(Unit)
    }

    override suspend fun clearCache() {
        syncScheduler.cancel()
        dao.clearAll()
    }

    // -----------------------------------------------------------------
    // Upload steps. Each runs with [syncLock] held.
    // -----------------------------------------------------------------

    private suspend fun upload(row: WorkoutWithSets): Outcome {
        val entity = row.workout
        return when (entity.syncState) {
            SyncState.SYNCED -> Outcome.Done
            SyncState.PENDING_CREATE -> create(row)
            SyncState.PENDING_UPDATE ->
                if (entity.serverId == null) create(row) else patchNotes(row, entity.serverId)
            SyncState.PENDING_DELETE -> delete(row)
        }
    }

    private suspend fun create(row: WorkoutWithSets): Outcome {
        val entity = row.workout
        val uuid = entity.clientUuid
        dao.markUploadAttempted(uuid)
        val request = row.toDomain().toRequestDto(localRoute(uuid))
        return when (val result = apiCall { api.create(request) }) {
            is ApiResult.Success -> {
                val dto = result.data
                dao.setServerId(uuid, dto.id)
                // The server kept an earlier copy (its response was lost
                // the first time) and the notes were edited since.
                if (dto.notes.orEmpty() != entity.notes.orEmpty()) {
                    patchNotes(row, dto.id)
                } else {
                    dao.markSyncedIfUnchanged(uuid, entity.localVersion)
                    Outcome.Done
                }
            }
            is ApiResult.Error -> result.toOutcome()
            ApiResult.NetworkError -> Outcome.Offline
        }
    }

    private suspend fun patchNotes(row: WorkoutWithSets, serverId: Long): Outcome {
        val entity = row.workout
        val body: Map<String, Any?> = mapOf("notes" to entity.notes)
        return when (val result = apiCall { api.patch(serverId, body) }) {
            is ApiResult.Success -> {
                dao.markSyncedIfUnchanged(entity.clientUuid, entity.localVersion)
                Outcome.Done
            }
            is ApiResult.Error -> {
                // Deleted on another device: drop the local copy too.
                if (result.status == HTTP_NOT_FOUND) {
                    dao.remove(entity.clientUuid)
                    Outcome.Done
                } else {
                    result.toOutcome()
                }
            }
            ApiResult.NetworkError -> Outcome.Offline
        }
    }

    private suspend fun delete(row: WorkoutWithSets): Outcome {
        val entity = row.workout
        val uuid = entity.clientUuid
        val serverId: Long = entity.serverId ?: run {
            // Never sent: nothing to delete on the server.
            if (!entity.uploadAttempted) {
                dao.remove(uuid)
                return Outcome.Done
            }
            // Sent, but the reply was lost. POST is idempotent on the
            // uuid, so it hands back the server's id without a copy.
            val request = row.toDomain().toRequestDto(localRoute(uuid))
            when (val result = apiCall { api.create(request) }) {
                is ApiResult.Success -> result.data.id
                is ApiResult.Error -> return result.toOutcome()
                ApiResult.NetworkError -> return Outcome.Offline
            }
        }
        return when (val result = apiCallEmpty { api.delete(serverId) }) {
            is ApiResult.Success -> {
                dao.remove(uuid)
                Outcome.Done
            }
            is ApiResult.Error -> {
                if (result.status == HTTP_NOT_FOUND) {
                    dao.remove(uuid)
                    Outcome.Done
                } else {
                    result.toOutcome()
                }
            }
            ApiResult.NetworkError -> Outcome.Offline
        }
    }

    // -----------------------------------------------------------------

    /**
     * Try the upload now and wait up to [uploadWaitMs] for it. If it
     * hasn't finished (slow or no network), WorkManager takes over.
     */
    private suspend fun syncSoon() {
        val attempt = scope.async { syncPending() }
        val result = withTimeoutOrNull(uploadWaitMs) { attempt.await() }
        if (result !is ApiResult.Success) syncScheduler.schedule()
    }

    private suspend fun localRoute(uuid: String): List<TrackPoint> =
        dao.route(uuid).map { it.toDomain() }

    private fun notFound(): ApiResult.Error = ApiResult.Error(
        message = "Workout not found",
        status = HTTP_NOT_FOUND,
        fieldErrors = emptyMap(),
    )

    /**
     * Server errors that may clear up on their own (server down or
     * waking, timeouts, rate limits, an HTML error page instead of JSON,
     * an expired session that sign-out is about to handle) count as
     * offline and are retried. Any other 4xx means the server will never
     * accept this row as it is.
     */
    private fun ApiResult.Error.toOutcome(): Outcome = when {
        status == -1 || status == HTTP_UNAUTHORIZED || status == HTTP_TIMEOUT ||
            status == HTTP_TOO_MANY || status >= HTTP_SERVER_ERROR -> Outcome.Offline
        else -> Outcome.Rejected
    }

    private enum class Outcome { Done, Offline, Rejected }

    private companion object {
        const val DEFAULT_UPLOAD_WAIT_MS = 6_000L
        const val MAX_SYNC_PASSES = 3
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TIMEOUT = 408
        const val HTTP_TOO_MANY = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
