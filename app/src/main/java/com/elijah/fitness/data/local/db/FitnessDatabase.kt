package com.elijah.fitness.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The on-phone SQLite database (Room). It holds the signed-in user's
 * workouts, their sets and GPS routes, and a copy of their goals, so
 * every screen can show data with no connection and a workout logged
 * offline survives until it's uploaded.
 *
 * Sign-out empties it (see `AppContainer.signOutLocally`), so the next
 * account on the phone starts clean.
 *
 * ### Versions
 * - 1: phase 10.
 * - 2: phase 8d adds `laps`, `poolLengthM` and `caloriesKcal` to
 *   `workouts` ([MIGRATION_1_2]). Workouts saved offline before the
 *   update are kept; there is no destructive fallback.
 */
@Database(
    entities = [
        WorkoutEntity::class,
        StrengthSetEntity::class,
        RoutePointEntity::class,
        CachedGoalEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FitnessDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    abstract fun goalCacheDao(): GoalCacheDao

    companion object {
        private const val NAME = "fitness.db"

        /**
         * Version 1 -> 2: three nullable INTEGER columns on `workouts`.
         * No DEFAULT, so existing rows read them as NULL, which is what
         * the entity's `Int? = null` fields expect.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `laps` INTEGER")
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `poolLengthM` INTEGER")
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `caloriesKcal` INTEGER")
            }
        }

        fun build(context: Context): FitnessDatabase =
            Room.databaseBuilder(context, FitnessDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
