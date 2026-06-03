package nart.simpleanki.core.notifications

import nart.simpleanki.core.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderContentTest {

    private val settings = AppSettings(dailyGoalEnabled = true, newCardsTarget = 10, reviewCardsTarget = 20) // goal = 30

    @Test
    fun study_skipsWhenNothingReady() {
        assertNull(reminderContent(ReminderType.Study, settings, studiedToday = 0, readyCount = 0))
    }

    @Test
    fun study_postsCountWhenReady() {
        val c = reminderContent(ReminderType.Study, settings, studiedToday = 0, readyCount = 5)!!
        assertTrue(c.body.contains("5 cards"))
    }

    @Test
    fun study_singularCard() {
        val c = reminderContent(ReminderType.Study, settings, studiedToday = 0, readyCount = 1)!!
        assertTrue(c.body.contains("1 card "))
    }

    @Test
    fun goal_skipsWhenDisabled() {
        val off = settings.copy(dailyGoalEnabled = false)
        assertNull(reminderContent(ReminderType.Goal, off, studiedToday = 0, readyCount = 5))
    }

    @Test
    fun goal_skipsWhenMet() {
        assertNull(reminderContent(ReminderType.Goal, settings, studiedToday = 30, readyCount = 5))
        assertNull(reminderContent(ReminderType.Goal, settings, studiedToday = 31, readyCount = 5))
    }

    @Test
    fun goal_postsRemainingWhenUnmet() {
        val c = reminderContent(ReminderType.Goal, settings, studiedToday = 22, readyCount = 5)!!
        assertEquals("Daily goal", c.title)
        assertTrue("remaining = 8", c.body.contains("8 cards"))
    }
}
