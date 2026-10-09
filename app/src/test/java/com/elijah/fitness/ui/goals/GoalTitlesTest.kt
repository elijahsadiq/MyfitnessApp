package com.elijah.fitness.ui.goals

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DurationGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalTitlesTest {

    @Test
    fun `display title follows the current number of days, not the stored title`() {
        // Created as a 13-day goal, then extended by 7 days on the server:
        // the stored title still says 13.
        val goal = DurationGoal(
            id = "1",
            title = "Run for 225 min in 13 days",
            activityType = ActivityType.RUNNING,
            targetValue = 225.0,
            startDate = LocalDate.of(2026, 10, 7),
            durationDays = 20,
            createdAt = Instant.parse("2026-10-07T08:00:00Z"),
        )
        assertEquals("Run for 225 min in 20 days", goal.displayTitle())
    }
}
