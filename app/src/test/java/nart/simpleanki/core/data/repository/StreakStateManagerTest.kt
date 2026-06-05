package nart.simpleanki.core.data.repository

import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.domain.streak.StreakState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.TimeZone

class StreakStateManagerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private fun dayMillis(epochDay: Long) = epochDay * day + day / 2 // noon UTC → localEpochDay == epochDay

    private fun log(epochDay: Long) = ReviewLogEntity(
        id = "r$epochDay", cardId = "c1", rating = 3, state = 2, due = 0, stability = 1.0, difficulty = 5.0,
        elapsedDays = 0.0, lastElapsedDays = 0.0, scheduledDays = 0.0, review = dayMillis(epochDay), dirty = false,
    )

    @Test
    fun reconcile_persistsAutoFreeze() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll((1L..5L).map { log(it) })
        val stateRepo = StreakStateRepository(FakeStreakStateDao(), now = { 0L })
        stateRepo.update(StreakState(freezeTokens = 1, lastReconciledDay = 5))
        val mgr = StreakStateManager(stateRepo, ReviewLogRepository(logDao), now = { dayMillis(7) }, timeZone = utc)
        mgr.reconcile()
        assertEquals(setOf(6L), stateRepo.get().frozenDays)
    }

    @Test
    fun repairOffer_andRepair_restoresFrozenGap() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(1L, 2, 3, 4, 5, 7).map { log(it) })
        val stateRepo = StreakStateRepository(FakeStreakStateDao(), now = { 0L })
        stateRepo.update(StreakState(lastReconciledDay = 7))
        val mgr = StreakStateManager(stateRepo, ReviewLogRepository(logDao), now = { dayMillis(7) }, timeZone = utc)
        assertNotNull(mgr.repairOffer())
        mgr.repair()
        assertEquals(setOf(6L), stateRepo.get().frozenDays)
    }
}
