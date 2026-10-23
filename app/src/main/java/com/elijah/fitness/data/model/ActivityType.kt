package com.elijah.fitness.data.model

/**
 * Every activity the app can log. The declaration order is the order the
 * UI lists them in (log sheet, filters, goal pickers).
 *
 * The names double as the wire and database values (`"SWIMMING"`, …),
 * so never rename one. The user-facing label for [WEIGHTLIFTING] is
 * "Workout" (phase 8d); only the label changed, not the value.
 *
 * Swimming and rowing arrived in phase 8d.
 */
enum class ActivityType { RUNNING, CYCLING, SWIMMING, ROWING, WEIGHTLIFTING }
