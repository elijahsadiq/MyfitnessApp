package com.elijah.fitness.util

import com.elijah.fitness.testutil.StringsXmlText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** Uses the real strings.xml (duration_minutes / _hours / _hours_minutes). */
class DurationFormatterTest {

    private fun format(minutes: Long) = DurationFormatter.format(StringsXmlText, minutes)

    @Test
    fun `150 minutes reads 2 h 30 min`() = assertEquals("2 h 30 min", format(150))

    @Test
    fun `under an hour reads minutes only`() = assertEquals("45 min", format(45))

    @Test
    fun `whole hours drop the minutes`() = assertEquals("2 h", format(120))

    @Test
    fun `edges`() {
        assertEquals("0 min", format(0))
        assertEquals("59 min", format(59))
        assertEquals("1 h", format(60))
        assertEquals("1 h 1 min", format(61))
        assertEquals("166 h 40 min", format(10_000))
        // Negative input is treated as 0, never "-5 min".
        assertEquals("0 min", format(-5))
    }

    @Test
    fun `a workout duration reads like the goals, with seconds under a minute`() {
        fun format(d: Duration) = DurationFormatter.format(StringsXmlText, d)
        assertEquals("32 min", format(Duration.ofSeconds(32 * 60 + 10)))
        assertEquals("1 h 5 min", format(Duration.ofMinutes(65)))
        assertEquals("2 h", format(Duration.ofHours(2)))
        assertEquals("45 s", format(Duration.ofSeconds(45)))
        assertEquals("1 min", format(Duration.ofSeconds(60)))
        assertEquals("0 s", format(Duration.ofSeconds(-3)))
    }

    @Test
    fun `split gives whole hours and the remainder`() {
        assertEquals(DurationFormatter.Parts(hours = 3, minutes = 8), DurationFormatter.split(188))
        assertEquals(DurationFormatter.Parts(hours = 0, minutes = 0), DurationFormatter.split(-1))
    }
}
