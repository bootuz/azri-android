package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogManagerTest {

    @Test fun track_fansOutToEveryService() {
        val a = FakeLogService(); val b = FakeLogService()
        val mgr = LogManager(listOf(a, b))
        val event = AnyLoggableEvent("test_event", mapOf("k" to 1))
        mgr.track(event)
        assertEquals(listOf(event), a.events)
        assertEquals(listOf(event), b.events)
    }

    @Test fun trackScreen_identify_clear_fanOut() {
        val a = FakeLogService(); val b = FakeLogService()
        val mgr = LogManager(listOf(a, b))
        mgr.trackScreen("home")
        mgr.identifyUser("u1", "Grace", "g@x.com")
        mgr.clearUser()
        assertEquals(listOf("home"), a.screens)
        assertEquals(Triple("u1", "Grace", "g@x.com"), b.identified)
        assertTrue(a.cleared && b.cleared)
    }

    @Test fun oneThrowingService_doesNotStopOthers() {
        val bad = FakeLogService(throwOnTrack = true)
        val good = FakeLogService()
        val mgr = LogManager(listOf(bad, good))
        mgr.track(AnyLoggableEvent("e"))
        assertEquals(1, good.events.size) // good still received it despite bad throwing
    }
}
