package nart.simpleanki.feature.deckdetail

import nart.simpleanki.core.domain.fsrs.IntervalFormatter
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckDetailStatsTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun card(id: String, state: CardState, due: Long) = Card(
        id = id, front = "f$id", back = "b$id", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = state.value,
    )

    @Test fun emptyList_isNull() {
        assertNull(nextReviewLabel(emptyList(), now))
    }

    @Test fun allNew_isNull() {
        // New cards are never "reviews", even with a future due value.
        assertNull(nextReviewLabel(listOf(card("1", CardState.New, now + day)), now))
    }

    @Test fun onlyPastDue_isNull() {
        // Already-due review cards are not a FUTURE review.
        assertNull(nextReviewLabel(listOf(card("1", CardState.Review, now - 1_000L)), now))
    }

    @Test fun picksSoonestFutureReview_ignoringNewAndPastDue() {
        val cards = listOf(
            card("1", CardState.Review, now + 3 * day),     // +3d
            card("2", CardState.Learning, now + 1 * day),   // +1d (soonest future review)
            card("3", CardState.New, now + 1_000L),         // New -> ignored
            card("4", CardState.Review, now - 5_000L),      // past due -> ignored
        )
        assertEquals("in ${IntervalFormatter.format(day)}", nextReviewLabel(cards, now))
    }
}
