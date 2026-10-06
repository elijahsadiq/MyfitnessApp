package com.elijah.fitness.analytics

import com.elijah.fitness.data.model.ActivityType

/**
 * The chip-group selection on the Progress tab. `ALL` is deliberately not
 * an `ActivityType` — the "any activity" case needs its own first-class
 * value in the UI state.
 */
enum class ActivityFilter {
    ALL, RUNNING, CYCLING, WEIGHTLIFTING;

    /** Null when the filter is ALL — matches [ActivityType]-less lookups. */
    fun asActivityType(): ActivityType? = when (this) {
        ALL -> null
        RUNNING -> ActivityType.RUNNING
        CYCLING -> ActivityType.CYCLING
        WEIGHTLIFTING -> ActivityType.WEIGHTLIFTING
    }
}
