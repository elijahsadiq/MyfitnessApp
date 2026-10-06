package com.elijah.fitness.insights

/**
 * One insight to render on Home. Priorities are intentionally sparse
 * (10, 20, 30, …) so a future rule can slot in between without renumbering.
 * Higher priority wins when multiple insights fire for the same open.
 *
 * Priority order (phase 8 step 6):
 *   10 — New personal record (biggest moment, always surface)
 *   20 — At-this-rate projection for an in-progress monthly goal
 *   30 — Pace or speed improvement over the previous period
 *   40 — "You ran X% further than last week" style streak comparison
 *   50 — Dormancy nudge ("you haven't cycled in 9 days")
 *   99 — Fallback copy (the default shown when nothing else fires)
 */
data class Insight(
    val text: String,
    val priority: Int,
    val kind: Kind,
) {
    enum class Kind {
        PersonalRecord,
        GoalProjection,
        PaceImprovement,
        VolumeChange,
        Dormancy,
        Fallback,
    }
}
