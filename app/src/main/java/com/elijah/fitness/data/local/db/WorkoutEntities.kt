package com.elijah.fitness.data.local.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Where a workout stored on the phone stands against the server.
 *
 * - [SYNCED]: same as the server copy.
 * - [PENDING_CREATE]: saved on the phone, not uploaded yet.
 * - [PENDING_UPDATE]: on the server, but its notes were edited here.
 * - [PENDING_DELETE]: deleted here, still on the server. Hidden from
 *   every screen until the DELETE goes through.
 *
 * Room stores an enum as its name (TEXT), so the DAO queries compare
 * against the literal names.
 */
enum class SyncState { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE }

/**
 * One workout row in the on-phone database. Mirrors the server's
 * `workouts` table plus the bookkeeping the sync needs.
 *
 * @property serverId the server's numeric id; null until the first
 *   upload succeeds (or a refresh matches the row by [clientUuid]).
 * @property routeCached true once `route_points` holds this workout's
 *   whole route (which may be no points at all). The list endpoint
 *   leaves routes out, so a synced run starts false and the detail
 *   screen fills it in on first open.
 * @property localVersion bumped by every edit made on the phone. An
 *   upload only marks the row [SyncState.SYNCED] if the version is
 *   still the one it sent, so an edit made mid-upload is never lost.
 * @property uploadAttempted true once a POST for this row has been
 *   sent. A row deleted before that point never reached the server
 *   and can be dropped without a DELETE.
 */
@Entity(tableName = "workouts", indices = [Index("startTimeMs")])
data class WorkoutEntity(
    @PrimaryKey val clientUuid: String,
    val serverId: Long?,
    val activityType: String,
    val startTimeMs: Long,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val notes: String?,
    val syncState: SyncState,
    val routeCached: Boolean,
    val localVersion: Int = 0,
    val uploadAttempted: Boolean = false,
)

/** One set of a weightlifting workout. Deleted with its workout. */
@Entity(
    tableName = "strength_sets",
    primaryKeys = ["workoutUuid", "setOrder"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["clientUuid"],
            childColumns = ["workoutUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutUuid")],
)
data class StrengthSetEntity(
    val workoutUuid: String,
    val setOrder: Int,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double,
)

/** One accepted GPS fix of a tracked workout. Deleted with its workout. */
@Entity(
    tableName = "route_points",
    primaryKeys = ["workoutUuid", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["clientUuid"],
            childColumns = ["workoutUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutUuid")],
)
data class RoutePointEntity(
    val workoutUuid: String,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val elapsedMs: Long,
    val accuracyM: Double?,
    val recordedAtMs: Long,
)

/** A workout row with its sets, read in one query by Room. */
data class WorkoutWithSets(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "clientUuid", entityColumn = "workoutUuid")
    val sets: List<StrengthSetEntity>,
)

/**
 * Last goal list the server sent, one JSON-encoded `GoalDto` per row.
 * Goals are only read offline (progress is worked out on the phone),
 * so a plain copy of the server's answer is all the cache needs.
 */
@Entity(tableName = "cached_goals")
data class CachedGoalEntity(
    @PrimaryKey val id: Long,
    val json: String,
)
