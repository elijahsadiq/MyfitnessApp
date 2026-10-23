package com.elijah.fitness.ui.goals

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DurationGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import com.elijah.fitness.testutil.StringsXmlText

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
        // Phase 8d: time reads "3 h 45 min", never "225 min".
        assertEquals("Run for 3 h 45 min in 20 days", goal.displayTitle(StringsXmlText))
    }

    @Test
    fun `display title keeps a half kilometre`() {
        val goal = com.elijah.fitness.data.model.DistanceGoal(
            id = "2",
            title = "Swim 10 km in 30 days",
            activityType = ActivityType.SWIMMING,
            targetValue = 10.5,
            startDate = LocalDate.of(2026, 10, 7),
            durationDays = 30,
            createdAt = Instant.parse("2026-10-07T08:00:00Z"),
        )
        assertEquals("Swim 10.5 km in 30 days", goal.displayTitle(StringsXmlText))
    }

    @Test
    fun `quick start rows read exactly like the goal they create`() {
        val titles = GoalSuggestion.DEFAULTS.map { it.title(StringsXmlText, java.util.Locale.UK) }
        assertEquals(
            listOf("Run 20 km in 14 days", "Ride 100 km in 30 days", "Work out for 2 h 30 min in 7 days"),
            titles,
        )
        GoalSuggestion.DEFAULTS.forEach { s ->
            assertEquals(
                autoTitle(StringsXmlText, s.activity, s.metric, s.target, s.days, java.util.Locale.UK),
                s.title(StringsXmlText, java.util.Locale.UK),
            )
        }
    }

    @Test
    fun `quick start subtitles are the sheet's daily pace, in the device locale`() {
        val nbsp = ' '
        val uk = GoalSuggestion.DEFAULTS.map { it.subtitle(StringsXmlText, java.util.Locale.UK) }
        assertEquals(
            listOf(
                "About 1.4${nbsp}km a${nbsp}day",
                "About 3.3${nbsp}km a${nbsp}day",
                "About 21${nbsp}min a${nbsp}day",
            ),
            uk,
        )
        // A decimal-comma locale writes the pace as the sheet does there.
        assertEquals(
            "About 1,4${nbsp}km a${nbsp}day",
            GoalSuggestion.DEFAULTS.first().subtitle(StringsXmlText, java.util.Locale.GERMANY),
        )
    }
}
