package com.elijah.fitness.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Room access to the cached copy of the server's goal list. */
@Dao
abstract class GoalCacheDao {

    @Query("SELECT * FROM cached_goals")
    abstract suspend fun all(): List<CachedGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<CachedGoalEntity>)

    @Query("DELETE FROM cached_goals")
    abstract suspend fun clear()

    /** Swap the whole cache for [rows] in one transaction. */
    @Transaction
    open suspend fun replaceAll(rows: List<CachedGoalEntity>) {
        clear()
        if (rows.isNotEmpty()) insertAll(rows)
    }
}
