package nart.simpleanki.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderSchedulerTest {

    private fun calAt(delayFromNow: Long, now: Long) = Calendar.getInstance().apply { timeInMillis = now + delayFromNow }

    @Test
    fun delay_landsOnRequestedHourAndMinute() {
        val now = System.currentTimeMillis()
        val delay = nextTriggerDelayMillis(now, hour = 9, minute = 30)
        val target = calAt(delay, now)
        assertEquals(9, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, target.get(Calendar.MINUTE))
        assertEquals(0, target.get(Calendar.SECOND))
    }

    @Test
    fun delay_isAlwaysWithinNext24h_andPositive() {
        val now = System.currentTimeMillis()
        for (h in intArrayOf(0, 6, 12, 18, 23)) {
            val delay = nextTriggerDelayMillis(now, hour = h, minute = 0)
            assertTrue("positive ($h)", delay > 0)
            assertTrue("<= 24h ($h)", delay <= 24L * 60 * 60 * 1000)
        }
    }

    @Test
    fun pastTimeToday_rollsToTomorrow() {
        // Fix "now" to 18:00 local; a 09:00 reminder has passed → must schedule for tomorrow.
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delay = nextTriggerDelayMillis(now, hour = 9, minute = 0)
        // 18:00 → next 09:00 is exactly 15 hours away.
        assertEquals(15L * 60 * 60 * 1000, delay)
    }
}
