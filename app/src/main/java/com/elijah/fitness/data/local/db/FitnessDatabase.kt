package com.elijah.fitness.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-phone SQLite database (Room). It holds the signed-in user's
 * workouts, their sets and GPS routes, and a copy of their goals, so
 * every screen can show data with no connection and a workout logged
 * offline survives until it's uploaded.
 *
 * Sign-out empties it (see `AppContainer.signOutLocally`), so the next
 * account on the phone starts clean.
 */
@Database(
    entities = [
        WorkoutEntity::class,
        StrengthSetEntity::class,
        RoutePointEntity::class,
        CachedGoalEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FitnessDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    abstract fun goalCacheDao(): GoalCacheDao

    companion object {
        private const val NAME = "fitness.db"

        fun build(context: Context): FitnessDatabase =
            Room.databaseBuilder(context, FitnessDatabase::class.java, NAME).build()
    }
}
