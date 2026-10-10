package com.elijah.fitness.data.local.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the Room-generated [WorkoutDao], so the
 * offline repository can be tested on the JVM. It implements only the
 * abstract SQL methods; the `@Transaction` helpers ([WorkoutDao.save],
 * [WorkoutDao.mergeServer], …) are the real ones from the base class.
 */
class FakeWorkoutDao : WorkoutDao() {

    private val rows = MutableStateFlow<Map<String, WorkoutEntity>>(emptyMap())
    private val sets = mutableMapOf<String, List<StrengthSetEntity>>()
    private val routes = mutableMapOf<String, List<RoutePointEntity>>()

    /** Direct view of a row, for assertions. */
    fun row(uuid: String): WorkoutEntity? = rows.value[uuid]

    fun routeSize(uuid: String): Int = routes[uuid].orEmpty().size

    private fun withSets(entity: WorkoutEntity) =
        WorkoutWithSets(entity, sets[entity.clientUuid].orEmpty())

    private fun update(uuid: String, change: (WorkoutEntity) -> WorkoutEntity) {
        val current = rows.value[uuid] ?: return
        rows.value = rows.value + (uuid to change(current))
    }

    override fun observeVisible(): Flow<List<WorkoutWithSets>> = rows.map { all ->
        all.values
            .filter { it.syncState != SyncState.PENDING_DELETE }
            .sortedByDescending { it.startTimeMs }
            .map(::withSets)
    }

    override fun observeUnsyncedIds(): Flow<List<String>> = rows.map { all ->
        all.values
            .filter { it.syncState == SyncState.PENDING_CREATE || it.syncState == SyncState.PENDING_UPDATE }
            .map { it.clientUuid }
    }

    override suspend fun get(uuid: String): WorkoutWithSets? = rows.value[uuid]?.let(::withSets)

    override suspend fun pending(): List<WorkoutWithSets> = rows.value.values
        .filter { it.syncState != SyncState.SYNCED }
        .sortedBy { it.startTimeMs }
        .map(::withSets)

    override suspend fun all(): List<WorkoutEntity> = rows.value.values.toList()

    override suspend fun route(uuid: String): List<RoutePointEntity> =
        routes[uuid].orEmpty().sortedBy { it.seq }

    override suspend fun upsert(workout: WorkoutEntity) {
        rows.value = rows.value + (workout.clientUuid to workout)
    }

    override suspend fun insertSets(sets: List<StrengthSetEntity>) {
        sets.groupBy { it.workoutUuid }.forEach { (uuid, list) ->
            this.sets[uuid] = this.sets[uuid].orEmpty() + list
        }
    }

    override suspend fun insertRoute(points: List<RoutePointEntity>) {
        points.groupBy { it.workoutUuid }.forEach { (uuid, list) ->
            routes[uuid] = routes[uuid].orEmpty() + list
        }
    }

    override suspend fun deleteSets(uuid: String) { sets.remove(uuid) }

    override suspend fun deleteRoute(uuid: String) { routes.remove(uuid) }

    override suspend fun deleteRow(uuid: String) { rows.value = rows.value - uuid }

    override suspend fun clearSets() = sets.clear()

    override suspend fun clearRoutes() = routes.clear()

    override suspend fun clearRows() { rows.value = emptyMap() }

    override suspend fun setServerId(uuid: String, serverId: Long) =
        update(uuid) { it.copy(serverId = serverId) }

    override suspend fun markUploadAttempted(uuid: String) =
        update(uuid) { it.copy(uploadAttempted = true) }

    override suspend fun markSyncedIfUnchanged(uuid: String, version: Int): Int {
        val current = rows.value[uuid] ?: return 0
        if (current.localVersion != version || current.syncState == SyncState.PENDING_DELETE) return 0
        update(uuid) { it.copy(syncState = SyncState.SYNCED) }
        return 1
    }

    override suspend fun editNotes(uuid: String, notes: String?) = update(uuid) {
        it.copy(
            notes = notes,
            localVersion = it.localVersion + 1,
            syncState = if (it.syncState == SyncState.PENDING_CREATE) {
                SyncState.PENDING_CREATE
            } else {
                SyncState.PENDING_UPDATE
            },
        )
    }

    override suspend fun markDeleted(uuid: String) = update(uuid) {
        it.copy(syncState = SyncState.PENDING_DELETE, localVersion = it.localVersion + 1)
    }

    override suspend fun markRouteCached(uuid: String) = update(uuid) { it.copy(routeCached = true) }
}
