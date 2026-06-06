package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.typing.DeckMastery
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingMasteryProviderTest {
    private val now = 1_700_000_000_000L
    private fun card(id: String, deck: String, back: String = "b") = Card(
        id = id, front = "f", back = back, deckId = deck,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test fun deckMastery_excludesBlankBackCards_andCountsLatestCorrect() = runTest {
        val cardDao = FakeCardDao()
        val cardRepo = CardRepository(cardDao, now = { now })
        cardRepo.upsert(card("c1", "d1"))
        cardRepo.upsert(card("c2", "d1"))
        cardRepo.upsert(card("c3", "d1", back = "   "))   // blank back -> not typeable, excluded from total
        val logDao = FakeTypingLogDao()
        val logRepo = TypingLogRepository(logDao, newId = { java.util.UUID.randomUUID().toString() })
        logRepo.append(TypingLog(cardId = "c1", deckId = "d1", correct = true, typedText = "x", timestamp = 1))

        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val provider = TypingMasteryProvider(logRepo, cardRepo, deckRepo)
        assertEquals(DeckMastery(mastered = 1, total = 2), provider.observeDeckMastery("d1").first())
    }

    @Test fun deckMastery_excludesMemorizedAndReviewFilteredCards() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        deckRepo.upsert(
            Deck(id = "d1", name = "D", dateCreated = now, lastModified = now, reviewFilter = ReviewCardFilter.OriginalsOnly),
        )
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(card("orig", "d1"))                               // typeable original
        cardRepo.upsert(card("rev", "d1").copy(isReverse = true))         // excluded: OriginalsOnly
        cardRepo.upsert(card("mem", "d1").copy(memorized = true))         // excluded: memorized
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })

        val provider = TypingMasteryProvider(logRepo, cardRepo, deckRepo)
        assertEquals(DeckMastery(mastered = 0, total = 1), provider.observeDeckMastery("d1").first())
    }
}
