package com.elijah.fitness.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room access to the on-phone workout tables.
 *
 * An abstract class rather than an interface so the multi-step writes
 * ([save], [remove], [mergeServer]) can be `@Transaction` methods with
 * a body. Room generates the subclass that runs the SQL; unit tests
 * subclass it with an in-memory fake.
 *
 * Writes use [upsert], never `INSERT OR REPLACE`: REPLACE deletes the
 * old row first, and the foreign-key cascade would take its sets and
 * route with it.
 */
@Dao
abstract class WorkoutDao {

    /** Every workout the screens should show, newest first. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE syncState != 'PENDING_DELETE' ORDER BY startTimeMs DESC")
    abstract fun observeVisible(): Flow<List<WorkoutWithSets>>

    /** Visible workouts whose latest version hasn't reached the server. */
    @Query("SELECT clientUuid FROM workouts WHERE syncState IN ('PENDING_CREATE', 'PENDING_UPDATE')")
    abstract fun observeUnsyncedIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE clientUuid = :uuid")
    abstract suspend fun get(uuid: String): WorkoutWithSets?

    /** Rows with a change still to upload, oldest workout first. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE syncState != 'SYNCED' ORDER BY startTimeMs")
    abstract suspend fun pending(): List<WorkoutWithSets>

    @Query("SELECT * FROM workouts")
    abstract suspend fun all(): List<WorkoutEntity>

    @Query("SELECT * FROM route_points WHERE workoutUuid = :uuid ORDER BY seq")
    abstract suspend fun route(uuid: String): List<RoutePointEntity>

    @Upsert
    abstract suspend fun upsert(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSets(sets: List<StrengthSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoute(points: List<RoutePointEntity>)

    @Query("DELETE FROM strength_sets WHERE workoutUuid = :uuid")
    abstract suspend fun deleteSets(uuid: String)

    @Query("DELETE FROM route_points WHERE workoutUuid = :uuid")
    abstract suspend fun deleteRoute(uuid: String)

    @Query("DELETE FROM workouts WHERE clientUuid = :uuid")
    abstract suspend fun deleteRow(uuid: String)

    @Query("DELETE FROM strength_sets")
    abstract suspend fun clearSets()

    @Query("DELETE FROM route_points")
    abstract suspend fun clearRoutes()

    @Query("DELETE FROM workouts")
    abstract suspend fun clearRows()

    @Query("UPDATE workouts SET serverId = :serverId WHERE clientUuid = :uuid")
    abstract suspend fun setServerId(uuid: String, serverId: Long)

    @Query("UPDATE workouts SET uploadAttempted = 1 WHERE clientUuid = :uuid")
    abstract suspend fun markUploadAttempted(uuid: String)

    /**
     * Mark a row synced, but only if nobody edited it on the phone since
     * [version] was read. Returns the number of rows changed (0 or 1).
     */
    @Query(
        "UPDATE workouts SET syncState = 'SYNCED' " +
            "WHERE clientUuid = :uuid AND localVersion = :version AND syncState != 'PENDING_DELETE'"
    )
    abstract suspend fun markSyncedIfUnchanged(uuid: String, version: Int): Int

    /**
     * Notes edited on the phone. A row that was never uploaded stays
     * PENDING_CREATE (the POST carries the new notes); anything else
     * becomes PENDING_UPDATE.
     */
    @Query(
        "UPDATE workouts SET notes = :notes, localVersion = localVersion + 1, " +
            "syncState = CASE WHEN syncState = 'PENDING_CREATE' THEN 'PENDING_CREATE' " +
            "ELSE 'PENDING_UPDATE' END " +
            "WHERE clientUuid = :uuid"
    )
    abstract suspend fun editNotes(uuid: String, notes: String?)

    @Query(
        "UPDATE workouts SET syncState = 'PENDING_DELETE', localVersion = localVersion + 1 " +
            "WHERE clientUuid = :uuid"
    )
    abstract suspend fun markDeleted(uuid: String)

    @Query("UPDATE workouts SET routeCached = 1 WHERE clientUuid = :uuid")
    abstract suspend fun markRouteCached(uuid: String)

    /**
     * Write a workout with its sets. [route] replaces the stored route
     * when given; null leaves the existing route rows alone.
     */
    @Transaction
    open suspend fun save(
        workout: WorkoutEntity,
        sets: List<StrengthSetEntity>,
        route: List<RoutePointEntity>?,
    ) {
        upsert(workout)
        deleteSets(workout.clientUuid)
        if (sets.isNotEmpty()) insertSets(sets)
        if (route != null) {
            deleteRoute(workout.clientUuid)
            if (route.isNotEmpty()) insertRoute(route)
        }
    }

    /** Store a route fetched from the server and remember it's complete. */
    @Transaction
    open suspend fun cacheRoute(uuid: String, points: List<RoutePointEntity>) {
        deleteRoute(uuid)
        if (points.isNotEmpty()) insertRoute(points)
        markRouteCached(uuid)
    }

    /** Delete a workout with its sets and route. */
    @Transaction
    open suspend fun remove(uuid: String) {
        deleteSets(uuid)
        deleteRoute(uuid)
        deleteRow(uuid)
    }

    @Transaction
    open suspend fun clearAll() {
        clearSets()
        clearRoutes()
        clearRows()
    }

    /**
     * Bring the phone's copy in line with the server's list.
     *
     * - Synced rows the server no longer has were deleted on another
     *   device, so they go.
     * - New server rows are added; synced rows are refreshed from the
     *   server, keeping any route already cached.
     * - Rows with a change still to upload are left alone so the edit
     *   isn't overwritten. They only pick up a missing [WorkoutEntity.serverId].
     */
    @Transaction
    open suspend fun mergeServer(server: List<Pair<WorkoutEntity, List<StrengthSetEntity>>>) {
        val local = all().associateBy { it.clientUuid }
        val serverUuids = server.map { it.first.clientUuid }.toSet()
        local.values
            .filter { it.syncState == SyncState.SYNCED && it.clientUuid !in serverUuids }
            .forEach { remove(it.clientUuid) }
        server.forEach { (incoming, sets) ->
            val existing = local[incoming.clientUuid]
            when {
                existing == null -> save(incoming, sets, route = null)
                existing.syncState == SyncState.SYNCED -> save(
                    incoming.copy(
                        routeCached = existing.routeCached || incoming.routeCached,
                        localVersion = existing.localVersion,
                        uploadAttempted = true,
                    ),
                    sets,
                    route = null,
                )
                existing.serverId == null && incoming.serverId != null ->
                    setServerId(existing.clientUuid, incoming.serverId)
            }
        }
    }
}
