package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fsrs6Test {

    private val fsrs = Fsrs6()
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun newCard() = FsrsCard(
        stability = 0.0, difficulty = 0.0, state = CardState.New, reps = 0, lapses = 0, lastReviewMillis = null,
    )

    @Test
    fun parameters_are21_andPinned() {
        assertEquals(21, Fsrs6.DEFAULT_PARAMETERS.size)
        assertEquals(0.212, Fsrs6.DEFAULT_PARAMETERS.first(), 1e-9)
        assertEquals(0.1542, Fsrs6.DEFAULT_PARAMETERS.last(), 1e-9)
    }

    @Test
    fun newCard_again_entersLearning_andSchedulesSoon() {
        val r = fsrs.review(newCard(), Rating.Again, now)
        assertEquals(CardState.Learning, r.state)
        assertEquals(1, r.reps)
        assertTrue("due after now", r.dueMillis > now)
        assertTrue("sub-day learning step", r.scheduledDays <= 1.0)
        assertTrue(r.stability >= Fsrs6.MIN_STABILITY)
    }

    @Test
    fun newCard_easy_graduatesToReview() {
        val r = fsrs.review(newCard(), Rating.Easy, now)
        assertEquals(CardState.Review, r.state)
        assertTrue("review interval at least a day", r.scheduledDays >= 1.0)
    }

    @Test
    fun newCard_higherRating_yieldsHigherStability() {
        val again = fsrs.review(newCard(), Rating.Again, now).stability
        val hard = fsrs.review(newCard(), Rating.Hard, now).stability
        val good = fsrs.review(newCard(), Rating.Good, now).stability
        val easy = fsrs.review(newCard(), Rating.Easy, now).stability
        assertTrue("again <= hard", again <= hard)
        assertTrue("hard <= good", hard <= good)
        assertTrue("good <= easy", good <= easy)
    }

    @Test
    fun reviewCard_easyInterval_exceedsGood_exceedsHard() {
        val reviewCard = FsrsCard(
            stability = 10.0, difficulty = 5.0, state = CardState.Review,
            reps = 3, lapses = 0, lastReviewMillis = now - 10 * day,
        )
        val hard = fsrs.review(reviewCard, Rating.Hard, now).scheduledDays
        val good = fsrs.review(reviewCard, Rating.Good, now).scheduledDays
        val easy = fsrs.review(reviewCard, Rating.Easy, now).scheduledDays
        assertTrue("hard <= good ($hard,$good)", hard <= good)
        assertTrue("good <= easy ($good,$easy)", good <= easy)
    }

    @Test
    fun reviewCard_again_entersRelearning_incrementsLapses_andReducesStability() {
        val reviewCard = FsrsCard(
            stability = 20.0, difficulty = 5.0, state = CardState.Review,
            reps = 5, lapses = 1, lastReviewMillis = now - 15 * day,
        )
        val r = fsrs.review(reviewCard, Rating.Again, now)
        assertEquals(CardState.Relearning, r.state)
        assertEquals(2, r.lapses)
        assertTrue("forget reduces stability", r.stability < reviewCard.stability)
    }

    @Test
    fun difficulty_staysWithinBounds() {
        var card = newCard()
        var state = fsrs.review(card, Rating.Again, now)
        repeat(20) {
            card = FsrsCard(state.stability, state.difficulty, state.state, state.reps, state.lapses, state.lastReviewMillis)
            state = fsrs.review(card, Rating.Again, state.dueMillis + day)
            assertTrue("difficulty in [1,10] was ${state.difficulty}", state.difficulty in 1.0..10.0)
        }
    }

    @Test
    fun retrievability_decaysFromOneOverTime() {
        val rAtZero = fsrs.retrievability(0.0, 10.0)
        val rLater = fsrs.retrievability(10.0, 10.0)
        assertEquals(1.0, rAtZero, 1e-6)
        assertTrue("recall decays", rLater < rAtZero)
        assertTrue("recall positive", rLater > 0.0)
    }
}
