package nart.simpleanki.core.domain.fsrs

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DueTickerTest {

    private val base = 1_700_000_000_000L

    private fun reviewCard(id: String, due: Long) = Card(
        id = id, front = "Q", back = "A", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = CardState.Review.value,
    )

    private fun newCard(id: String) = Card(
        id = id, front = "Q", back = "A", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    // `clock` is tied to the test scheduler's virtual time so that as runTest auto-advances past
    // a `delay`, now() reflects the advanced time.
    @Test
    fun emitsImmediately_thenCompletes_whenNoFutureDue() = runTest {
        val clock = { base + testScheduler.currentTime }
        flowOf(listOf(newCard("n1"))).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)   // emits current now immediately
            awaitComplete()                          // only New cards → nothing to wait for, idles
        }
    }

    @Test
    fun reEmits_whenAFutureReviewCardBecomesDue() = runTest {
        val clock = { base + testScheduler.currentTime }
        flowOf(listOf(reviewCard("r1", due = base + 60_000L))).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)            // before due
            assertEquals(base + 60_000L, awaitItem().second)  // re-emits exactly at the due moment
            awaitComplete()                                   // now already due → no further ticks
        }
    }

    @Test
    fun firesAtSoonestDue_thenTheNext() = runTest {
        val clock = { base + testScheduler.currentTime }
        val cards = listOf(reviewCard("r1", base + 30_000L), reviewCard("r2", base + 60_000L))
        flowOf(cards).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)
            assertEquals(base + 30_000L, awaitItem().second)  // soonest first
            assertEquals(base + 60_000L, awaitItem().second)  // then the next
            awaitComplete()
        }
    }

    @Test
    fun reEmits_whenAFutureLearningCardBecomesDue() = runTest {
        val clock = { base + testScheduler.currentTime }
        val learning = Card(
            id = "l1", front = "Q", back = "A", deckId = "d1",
            dateCreated = 0, lastModified = 0, fsrsDue = base + 45_000L, fsrsState = CardState.Learning.value,
        )
        flowOf(listOf(learning)).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)
            assertEquals(base + 45_000L, awaitItem().second)  // Learning cards are watched, not just Review
            awaitComplete()
        }
    }

    @Test
    fun idles_whenTheOnlyFutureDueCardIsDeleted() = runTest {
        val clock = { base + testScheduler.currentTime }
        val deleted = reviewCard("r1", due = base + 60_000L).copy(isDeleted = true)
        flowOf(listOf(deleted)).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)  // emits current now once
            awaitComplete()                          // deleted card ignored → no tick scheduled
        }
    }

    @Test
    fun reEmitsImmediately_whenCardListChanges() = runTest {
        val clock = { base + testScheduler.currentTime }
        val source = MutableStateFlow(listOf(newCard("n1")))
        source.withDueTicks(clock).test {
            assertEquals(1, awaitItem().first.size)   // initial list
            source.value = listOf(newCard("n1"), newCard("n2"))
            assertEquals(2, awaitItem().first.size)   // flatMapLatest restarts with the new list
            cancelAndIgnoreRemainingEvents()          // StateFlow stays open; stop collecting
        }
    }
}
