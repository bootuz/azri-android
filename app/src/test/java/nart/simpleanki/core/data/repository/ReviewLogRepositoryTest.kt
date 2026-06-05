package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewLogRepositoryTest {

    private fun sampleLog(review: Long) = ReviewLog(
        rating = Rating.Good, state = CardState.Review, due = 0L,
        stability = 1.0, difficulty = 5.0, review = review,
    )

    @Test
    fun append_assignsInjectedId_cardId_andMarksDirty() = runTest {
        val dao = FakeReviewLogDao()
        val repo = ReviewLogRepository(dao, newId = { "log-1" })

        repo.append(cardId = "card-7", log = sampleLog(review = 1_000L))

        val logs = repo.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("log-1", logs[0].id)
        assertEquals("card-7", logs[0].cardId)
        assertEquals(Rating.Good, logs[0].rating)
        assertEquals(1_000L, logs[0].review)
        // Marked dirty so the next sync pushes it.
        assertEquals(listOf("log-1"), dao.getDirty().map { it.id })
    }
}
