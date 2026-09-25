package com.elijah.fitness.data.model

/**
 * The four goal kinds phase 7 ships with. Each maps to one concrete
 * [Goal] subclass:
 *
 *   DISTANCE → [DistanceGoal]
 *   COUNT    → [WorkoutCountGoal]
 *   DURATION → [DurationGoal]
 *   VOLUME   → [StrengthVolumeGoal]
 *
 * The wire representation is lowercase (`"distance"`, `"count"`, …) to
 * match the server; conversion goes through [wire] and [fromWire].
 */
enum class GoalType {
    DISTANCE, COUNT, DURATION, VOLUME;

    fun wire(): String = name.lowercase()

    companion object {
        fun fromWire(value: String): GoalType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: error("Unknown goalType: $value")
    }
}
