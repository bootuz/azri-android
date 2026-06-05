package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.streak.StreakState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakStateRepositoryTest {
    private val now = 1_700_000_000_000L

    @Test
    fun observe_defaultsToEmptyState_whenAbsent() = runTest {
        val repo = StreakStateRepository(FakeStreakStateDao(), now = { now })
        assertEquals(StreakState(), repo.observe().first())
        assertEquals(StreakState(), repo.get())
    }

    @Test
    fun update_stampsLastModifiedAndDirty_andRoundTripsFrozenDays() = runTest {
        val dao = FakeStreakStateDao()
        val repo = StreakStateRepository(dao, now = { now })
        repo.update(StreakState(freezeTokens = 2, frozenDays = setOf(6L, 3L), freezesAwardedForRun = 1, lastReconciledDay = 7))
        val saved = dao.get()!!
        assertEquals(now, saved.lastModified)
        assertTrue(saved.dirty)
        assertEquals("3,6", saved.frozenDays)
        assertEquals(setOf(3L, 6L), repo.get().frozenDays)
    }
}
