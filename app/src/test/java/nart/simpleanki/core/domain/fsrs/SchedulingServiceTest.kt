package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingServiceTest {

    private val service = SchedulingService()
    private val now = 1_700_000_000_000L

    private fun newCard() = Card(
        id = "c1", front = "Q", back = "A", deckId = "d1",
        dateCreated = now - 1000, lastModified = now - 1000,
        fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun schedule_updatesCardFsrsFields_andStampsLastModified() {
        val result = service.schedule(newCard(), Rating.Good, now)
        val card = result.card
        assertEquals(1, card.fsrsReps)
        assertEquals(now, card.lastModified)
        assertEquals(now, card.fsrsLastReview)
        assertTrue("due moved forward", card.fsrsDue > now)
        assertTrue("stability set", card.fsrsStability > 0.0)
        assertEquals(CardState.Learning.value, card.fsrsState)
    }

    @Test
    fun schedule_producesLog_matchingReview() {
        val result = service.schedule(newCard(), Rating.Easy, now)
        assertEquals(Rating.Easy, result.log.rating)
        assertEquals(now, result.log.review)
        assertEquals(result.card.fsrsStability, result.log.stability!!, 1e-9)
        assertEquals(CardState.Review, result.log.state)
    }

    @Test
    fun preview_returnsAllRatings_withIncreasingDueDates_andDoesNotMutate() {
        val card = newCard()
        val preview = service.preview(card, now)

        assertEquals(setOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy), preview.keys)
        // Better grades push the next review further out.
        assertTrue(preview[Rating.Again]!! <= preview[Rating.Good]!!)
        assertTrue(preview[Rating.Good]!! <= preview[Rating.Easy]!!)
        // Preview must not commit anything to the card it inspected.
        assertEquals(CardState.New.value, card.fsrsState)
        assertEquals(0, card.fsrsReps)
    }

    @Test
    fun presets_changeRetention() {
        val aggressive = SchedulingService(FsrsPreset.Aggressive.fixedParameters!!)
        val relaxed = SchedulingService(FsrsPreset.Relaxed.fixedParameters!!)
        // A review card scheduled under higher retention should get a shorter interval.
        val reviewCard = newCard().copy(
            fsrsState = CardState.Review.value, fsrsStability = 10.0, fsrsDifficulty = 5.0,
            fsrsLastReview = now - 10L * 86_400_000L,
        )
        val aggInterval = aggressive.schedule(reviewCard, Rating.Good, now).card.fsrsScheduledDays
        val relInterval = relaxed.schedule(reviewCard, Rating.Good, now).card.fsrsScheduledDays
        assertTrue("aggressive retention => shorter interval ($aggInterval vs $relInterval)", aggInterval < relInterval)
    }

    @Test
    fun customParameters_shortTermOff_graduatesNewCardToReviewInDays() {
        val longTerm = SchedulingService(
            FsrsParameters(requestRetention = 0.90, maximumInterval = 365, enableFuzz = false, enableShortTerm = false),
        )
        val card = longTerm.schedule(newCard(), Rating.Good, now).card
        assertEquals(CardState.Review.value, card.fsrsState)
        assertTrue("graduates in days, not minute steps", card.fsrsDue - now >= 86_400_000L)
    }
}
