package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingLogRepositoryTest {
    private fun log(card: String, deck: String, correct: Boolean) =
        TypingLog(cardId = card, deckId = deck, correct = correct, typedText = "x", timestamp = 1L)

    @Test fun append_assignsId_marksDirty_andObservable() = runTest {
        val dao = FakeTypingLogDao()
        val repo = TypingLogRepository(dao, newId = { "fixed-id" })
        repo.append(log("c1", "d1", correct = true))

        val all = repo.observeLogs().first()
        assertEquals(1, all.size)
        assertEquals("fixed-id", all.first().id)
        assertTrue(dao.getDirty().isNotEmpty())
    }

    @Test fun observeLogsForDeck_filtersByDeck() = runTest {
        val repo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        repo.append(log("c1", "d1", true))
        repo.append(log("c2", "d2", true))
        assertEquals(listOf("c1"), repo.observeLogsForDeck("d1").first().map { it.cardId })
    }

    @Test fun append_withSameId_isIdempotent() = runTest {
        val repo = TypingLogRepository(FakeTypingLogDao(), newId = { "id1" })
        val log = TypingLog(id = "id1", cardId = "c1", deckId = "d1", correct = true, typedText = "x", timestamp = 1L)
        repo.append(log)
        repo.append(log)
        assertEquals(1, repo.observeLogs().first().size)
    }
}
