package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayBucketingTest {

    private val ny = TimeZone.getTimeZone("America/New_York")

    private fun millis(tz: TimeZone, year: Int, month0: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(tz).apply {
            clear(); set(year, month0, day, hour, 0, 0)
        }.timeInMillis

    @Test fun sameLocalDate_differentTimes_sameIndex() {
        val morning = millis(ny, 2026, Calendar.JUNE, 5, 8)
        val evening = millis(ny, 2026, Calendar.JUNE, 5, 23)
        assertEquals(localEpochDay(morning, ny), localEpochDay(evening, ny))
    }

    @Test fun consecutiveDates_differByOne() {
        val d1 = millis(ny, 2026, Calendar.JUNE, 5, 12)
        val d2 = millis(ny, 2026, Calendar.JUNE, 6, 9)
        assertEquals(localEpochDay(d1, ny) + 1, localEpochDay(d2, ny))
    }

    @Test fun acrossDstSpringForward_stillDiffersByOne() {
        // US DST spring-forward 2026 is Sun Mar 8 (a 23-hour day). The civil-day index must still +1.
        val before = millis(ny, 2026, Calendar.MARCH, 8, 12)
        val after = millis(ny, 2026, Calendar.MARCH, 9, 12)
        assertEquals(localEpochDay(before, ny) + 1, localEpochDay(after, ny))
    }
}
