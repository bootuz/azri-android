package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingMasteryTest {
    private fun log(card: String, correct: Boolean, ts: Long, deck: String = "d") =
        TypingLog(id = "$card-$ts", cardId = card, deckId = deck, correct = correct, typedText = "", timestamp = ts)

    @Test fun latestFirstTryWins_masteryRegresses() {
        // c1: correct then later wrong -> NOT mastered. c2: wrong then later correct -> mastered.
        val logs = listOf(
            log("c1", correct = true, ts = 1),
            log("c1", correct = false, ts = 2),
            log("c2", correct = false, ts = 1),
            log("c2", correct = true, ts = 2),
        )
        assertEquals(setOf("c2"), TypingMastery.masteredCardIds(logs))
    }

    @Test fun deckMastery_countsAgainstCurrentDeckCards() {
        val logs = listOf(log("c1", true, 1), log("c2", true, 1), log("gone", true, 1))
        // "gone" has a log but is no longer in the deck -> excluded; c3 is in the deck but never typed.
        val m = TypingMastery.deckMastery(logs, deckCardIds = setOf("c1", "c2", "c3"))
        assertEquals(DeckMastery(mastered = 2, total = 3), m)
    }

    @Test fun emptyLogs_zeroMastered() {
        assertEquals(DeckMastery(0, 2), TypingMastery.deckMastery(emptyList(), setOf("c1", "c2")))
    }
}
