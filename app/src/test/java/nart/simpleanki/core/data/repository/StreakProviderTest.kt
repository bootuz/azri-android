package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.domain.streak.Streak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class StreakProviderTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private val today = 1_700_000_000_000L

    private fun logEntity(id: String, reviewMillis: Long) = ReviewLogEntity(
        id = id, cardId = "c1", rating = 3, state = 2, due = 0, stability = 1.0, difficulty = 5.0,
        elapsedDays = 0.0, lastElapsedDays = 0.0, scheduledDays = 0.0, review = reviewMillis, dirty = false,
    )

    private fun provider(dao: FakeReviewLogDao) =
        StreakProvider(ReviewLogRepository(dao), now = { today }, timeZone = utc)

    @Test
    fun observeStreak_countsConsecutiveDaysEndingToday() = runTest {
        val dao = FakeReviewLogDao()
        dao.insertAll(listOf(
            logEntity("a", today),
            logEntity("b", today),          // same day, still counts once
            logEntity("c", today - day),
            logEntity("d", today - 2 * day),
        ))
        assertEquals(Streak(3, 3), provider(dao).observeStreak().first())
    }

    @Test
    fun observeStreak_noLogs_isZero() = runTest {
        assertEquals(Streak(0, 0), provider(FakeReviewLogDao()).observeStreak().first())
    }

    @Test
    fun streakIncludingToday_countsTodayEvenIfNotYetLogged() = runTest {
        val dao = FakeReviewLogDao()
        dao.insertAll(listOf(logEntity("y", today - day)))  // only yesterday logged
        assertEquals(Streak(1, 1), provider(dao).observeStreak().first())
        assertEquals(Streak(2, 2), provider(dao).streakIncludingToday())
    }
}
