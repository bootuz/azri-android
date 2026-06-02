package nart.simpleanki.core.domain.fsrs

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalFormatterTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun subMinute_showsLessThanOneMinute() {
        assertEquals("< 1m", IntervalFormatter.format(0))
        assertEquals("< 1m", IntervalFormatter.format(59_000))
        assertEquals("< 1m", IntervalFormatter.format(-5_000)) // negative clamps to 0
    }

    @Test
    fun minutesHoursDays_singleUnit() {
        assertEquals("10m", IntervalFormatter.format(10 * minute))
        assertEquals("59m", IntervalFormatter.format(59 * minute + 59_000))
        assertEquals("1h", IntervalFormatter.format(hour))
        assertEquals("23h", IntervalFormatter.format(23 * hour))
        assertEquals("1d", IntervalFormatter.format(day))
        assertEquals("4d", IntervalFormatter.format(4 * day))
        assertEquals("29d", IntervalFormatter.format(29 * day))
    }

    @Test
    fun months_aboveThirtyDays() {
        assertEquals("1mo", IntervalFormatter.format(30 * day))
        assertEquals("12mo", IntervalFormatter.format(365 * day)) // matches iOS month-capped unit
    }
}
