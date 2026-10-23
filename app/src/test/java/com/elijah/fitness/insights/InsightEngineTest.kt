package com.elijah.fitness.insights

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.elijah.fitness.testutil.StringsXmlText
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.RowingWorkout

class InsightEngineTest {

    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-09-16T12:00:00Z") // Wed
    private val clock: Clock = Clock.fixed(now, zone)
    private val engine = InsightEngine(text = StringsXmlText, clock = clock, zone = zone)

    @Test
    fun `no workouts yields no insights`() {
        assertTrue(engine.rank(emptyList()).isEmpty())
    }

    @Test
    fun `longest run in the last week beats PR history and surfaces as PersonalRecord`() {
        val workouts: List<Workout> = listOf(
            // Prior history: 8 km longest run four weeks ago.
            run("2026-08-18T10:00:00Z", meters = 8_000.0, minutes = 45),
            // This week: 12 km Monday.
            run("2026-09-14T10:00:00Z", meters = 12_000.0, minutes = 60),
        )
        val top = engine.rank(workouts).first()
        assertEquals(Insight.Kind.PersonalRecord, top.kind)
        assertTrue("text was '${top.text}'", top.text.contains("12.0"))
    }

    @Test
    fun `goal projection fires on an active, 7-plus-day goal on track to finish within the window`() {
        // 30-day goal that started 2026-09-01; now = 2026-09-16 (day 16).
        val goal = DistanceGoal(
            id = "g",
            title = "20 km in 30 days",
            activityType = ActivityType.RUNNING,
            targetValue = 20.0,
            startDate = LocalDate.parse("2026-09-01"),
            durationDays = 30,
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        )
        val gwp = GoalWithProgress(
            goal = goal,
            status = Goal.Status.Active,
            currentValue = 13.0,
            progressFraction = 0.65,
            daysLeft = 15, // 30 - 15 elapsed = 15 remaining counting today
            averagePerDay = 13.0 / 16.0,
            neededPerDay = 7.0 / 15.0,
            sessionsLogged = 3,
        )
        val insights = engine.rank(emptyList(), listOf(gwp))
        val projection = insights.firstOrNull { it.kind == Insight.Kind.GoalProjection }
        assertTrue("no projection fired", projection != null)
        assertTrue(
            "text was '${projection!!.text}'",
            projection.text.contains("goal"),
        )
    }

    @Test
    fun `goal projection does not fire for a sub-7-day goal`() {
        val goal = DistanceGoal(
            id = "g", title = "5 km in 5 days",
            activityType = ActivityType.RUNNING, targetValue = 5.0,
            startDate = LocalDate.parse("2026-09-14"), durationDays = 5,
            createdAt = Instant.parse("2026-09-14T00:00:00Z"),
        )
        val gwp = GoalWithProgress(
            goal = goal, status = Goal.Status.Active,
            currentValue = 2.0, progressFraction = 0.4,
            daysLeft = 3, averagePerDay = 1.0, neededPerDay = 1.0, sessionsLogged = 2,
        )
        val projection = engine.rank(emptyList(), listOf(gwp))
            .firstOrNull { it.kind == Insight.Kind.GoalProjection }
        assertTrue("expected no projection for sub-7-day goal", projection == null)
    }

    @Test
    fun `pace improvement fires when this month beats last month by more than five seconds per km`() {
        val workouts: List<Workout> = listOf(
            // Last month: two slow runs ≈ 6:40/km (10 km in 66 min).
            run("2026-08-10T10:00:00Z", meters = 10_000.0, minutes = 67),
            run("2026-08-20T10:00:00Z", meters = 10_000.0, minutes = 66),
            // This month: two faster runs ≈ 6:00/km.
            run("2026-09-05T10:00:00Z", meters = 10_000.0, minutes = 60),
            run("2026-09-12T10:00:00Z", meters = 10_000.0, minutes = 61),
        )
        val pace = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.PaceImprovement }
        assertTrue("no pace insight fired", pace != null)
        assertTrue(
            "text was '${pace!!.text}'",
            pace.text.contains("seconds per kilometre"),
        )
    }

    @Test
    fun `volume change fires at ten percent or more vs last week`() {
        val workouts: List<Workout> = listOf(
            // Last week: 10 km total.
            run("2026-09-08T10:00:00Z", meters = 10_000.0, minutes = 60),
            // This week: 15 km total → 50% increase.
            run("2026-09-14T10:00:00Z", meters = 8_000.0, minutes = 50),
            run("2026-09-15T10:00:00Z", meters = 7_000.0, minutes = 45),
        )
        val vol = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.VolumeChange }
        assertTrue("no volume insight fired", vol != null)
        assertTrue("text was '${vol!!.text}'", vol.text.contains("further"))
    }

    @Test
    fun `dormancy fires when latest cycling is 7 to 60 days ago`() {
        val workouts: List<Workout> = listOf(
            // One run two days ago → no dormancy for running.
            run("2026-09-14T10:00:00Z", meters = 5_000.0, minutes = 25),
            // Last ride 10 days ago → dormancy for cycling.
            ride("2026-09-06T10:00:00Z", meters = 15_000.0, minutes = 45),
        )
        val dorm = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.Dormancy }
        assertTrue("no dormancy insight fired", dorm != null)
        assertTrue("text was '${dorm!!.text}'", dorm.text.contains("cycled"))
    }

    @Test
    fun `ranking returns PR ahead of volume change`() {
        val workouts: List<Workout> = listOf(
            run("2026-08-20T10:00:00Z", meters = 8_000.0, minutes = 50),
            run("2026-09-14T10:00:00Z", meters = 12_000.0, minutes = 60), // PR
            // Volume change: last week 8 km, this week 12 km → 50% more.
        )
        val top = engine.rank(workouts).first()
        assertEquals(Insight.Kind.PersonalRecord, top.kind)
    }

    @Test
    fun `new one rep max fires for a strength PR`() {
        val workouts: List<Workout> = listOf(
            // Prior PR: 100 kg x 3 → 100 * (1 + 3/30) = 110
            StrengthWorkout(
                id = "s0",
                startTime = Instant.parse("2026-08-20T10:00:00Z"),
                duration = Duration.ofMinutes(30),
                sets = listOf(StrengthSet("Bench", 3, 100.0)),
            ),
            // New PR: 90 kg x 8 → 90 * (1 + 8/30) ≈ 114
            StrengthWorkout(
                id = "s1",
                startTime = Instant.parse("2026-09-14T10:00:00Z"),
                duration = Duration.ofMinutes(30),
                sets = listOf(StrengthSet("Bench", 8, 90.0)),
            ),
        )
        val pr = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.PersonalRecord }
        assertTrue("no strength PR insight fired", pr != null)
        assertTrue("text was '${pr!!.text}'", pr.text.contains("Bench"))
    }

    private fun run(iso: String, meters: Double, minutes: Long): RunningWorkout =
        RunningWorkout(
            id = iso,
            startTime = Instant.parse(iso),
            duration = Duration.ofMinutes(minutes),
            distanceMeters = meters,
        )

    private fun ride(iso: String, meters: Double, minutes: Long): CyclingWorkout =
        CyclingWorkout(
            id = iso,
            startTime = Instant.parse(iso),
            duration = Duration.ofMinutes(minutes),
            distanceMeters = meters,
        )

    // --------- Phase 8d: swims and rows, copy from strings.xml ---------

    @Test
    fun `dormancy covers swimming`() {
        val workouts: List<Workout> = listOf(
            run("2026-09-14T10:00:00Z", meters = 5_000.0, minutes = 25),
            SwimmingWorkout(
                "swim", Instant.parse("2026-09-04T10:00:00Z"), Duration.ofMinutes(30), 1_500.0,
            ),
        )
        val dorm = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.Dormancy }
        assertEquals("You haven't swum in 12 days.", dorm?.text)
    }

    @Test
    fun `volume change covers rowing`() {
        val workouts: List<Workout> = listOf(
            RowingWorkout("r1", Instant.parse("2026-09-08T07:00:00Z"), Duration.ofMinutes(30), 5_000.0),
            RowingWorkout("r2", Instant.parse("2026-09-15T07:00:00Z"), Duration.ofMinutes(40), 7_000.0),
        )
        val vol = engine.rank(workouts).firstOrNull { it.kind == Insight.Kind.VolumeChange }
        assertEquals("You rowed 40% further than last week.", vol?.text)
    }
}
