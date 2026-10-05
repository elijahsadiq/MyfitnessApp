package com.elijah.fitness.data.model

import com.elijah.fitness.tracking.TrackPoint

/**
 * A full workout plus its GPS route, returned by
 * [com.elijah.fitness.data.repository.WorkoutRepository.getWorkoutDetail].
 * The list endpoint deliberately omits route points for payload size, so
 * the detail screen fetches them lazily on entry.
 *
 * @property routePoints empty when the workout has no stored route (manual
 *   logs and all strength workouts). The UI hides the map section in that
 *   case rather than rendering an empty container.
 */
data class WorkoutDetail(
    val workout: Workout,
    val routePoints: List<TrackPoint>,
)
