package nart.simpleanki.feature.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.notifications.ReminderScheduler
import nart.simpleanki.core.notifications.ReminderType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private class FakeScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Triple<ReminderType, Int, Int>>()
        val cancelled = mutableListOf<ReminderType>()
        override fun schedule(type: ReminderType, hour: Int, minute: Int) { scheduled += Triple(type, hour, minute) }
        override fun cancel(type: ReminderType) { cancelled += type }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun enablingStudy_persists_andSchedules() = runTest {
        val settings = FakeSettingsRepository()
        val scheduler = FakeScheduler()
        val vm = NotificationsViewModel(settings, scheduler)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setStudy(true, 8, 30); runCurrent()

        val s = vm.uiState.value
        assertTrue(s.studyEnabled)
        assertEquals(8, s.studyHour)
        assertEquals(30, s.studyMinute)
        assertEquals(listOf(Triple(ReminderType.Study, 8, 30)), scheduler.scheduled)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun disablingStudy_cancels() = runTest {
        val scheduler = FakeScheduler()
        val vm = NotificationsViewModel(FakeSettingsRepository(), scheduler)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setStudy(false, 9, 0); runCurrent()
        assertEquals(listOf(ReminderType.Study), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun goalReminder_schedulesAndCancels() = runTest {
        val scheduler = FakeScheduler()
        val vm = NotificationsViewModel(FakeSettingsRepository(), scheduler)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setGoal(true, 20, 15); runCurrent()
        assertEquals(listOf(Triple(ReminderType.Goal, 20, 15)), scheduler.scheduled)
        assertEquals(20, vm.uiState.value.goalHour)

        vm.setGoal(false, 20, 15); runCurrent()
        assertEquals(listOf(ReminderType.Goal), scheduler.cancelled)
    }

    @Test
    fun formatTime_rendersTwelveHourClock() {
        assertEquals("9:00 AM", formatTime(9, 0))
        assertEquals("8:05 PM", formatTime(20, 5))
        assertEquals("12:00 PM", formatTime(12, 0))
        assertEquals("12:30 AM", formatTime(0, 30))
    }
}
