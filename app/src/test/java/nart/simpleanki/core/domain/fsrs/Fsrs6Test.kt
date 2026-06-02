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
    fun newCard_learningSteps_areFixedMinuteIntervals() {
        val minute = 60_000L
        // Short-term learning steps mirror iOS swift-fsrs: 1m / 5m / 10m; Easy graduates to days.
        assertEquals(1 * minute, fsrs.review(newCard(), Rating.Again, now).dueMillis - now)
        assertEquals(5 * minute, fsrs.review(newCard(), Rating.Hard, now).dueMillis - now)
        assertEquals(10 * minute, fsrs.review(newCard(), Rating.Good, now).dueMillis - now)
        assertTrue("easy graduates to >= 1 day", fsrs.review(newCard(), Rating.Easy, now).dueMillis - now >= day)
    }

    @Test
    fun learningCard_again_hard_stepFiveAndTenMinutes_stayLearning() {
        val minute = 60_000L
        val learning = FsrsCard(
            stability = 1.0, difficulty = 5.0, state = CardState.Learning,
            reps = 1, lapses = 0, lastReviewMillis = now,
        )
        val again = fsrs.review(learning, Rating.Again, now)
        val hard = fsrs.review(learning, Rating.Hard, now)
        assertEquals(5 * minute, again.dueMillis - now)
        assertEquals(10 * minute, hard.dueMillis - now)
        assertEquals(CardState.Learning, again.state)
        assertEquals(CardState.Learning, hard.state)
        // Good graduates to a day-scale review interval.
        val good = fsrs.review(learning, Rating.Good, now)
        assertEquals(CardState.Review, good.state)
        assertTrue(good.dueMillis - now >= day)
    }

    @Test
    fun reviewCard_lapse_stepIsFiveMinutes() {
        val minute = 60_000L
        val review = FsrsCard(
            stability = 20.0, difficulty = 5.0, state = CardState.Review,
            reps = 5, lapses = 1, lastReviewMillis = now - 15 * day,
        )
        val r = fsrs.review(review, Rating.Again, now)
        assertEquals(5 * minute, r.dueMillis - now)
        assertEquals(CardState.Relearning, r.state)
    }

    // --- Fuzz (interval jitter) ---

    private val fuzzed = Fsrs6(enableFuzz = true)

    private fun matureReviewCard() = FsrsCard(
        stability = 15.0, difficulty = 5.0, state = CardState.Review,
        reps = 4, lapses = 0, lastReviewMillis = now - 12 * day,
    )

    @Test
    fun fuzz_isDeterministic_sameCardSameInterval() {
        val a = fuzzed.review(matureReviewCard(), Rating.Good, now).dueMillis
        val b = fuzzed.review(matureReviewCard(), Rating.Good, now).dueMillis
        assertEquals("fuzz must be reproducible for a given card", a, b)
    }

    @Test
    fun fuzz_disabledByDefault_matchesUnfuzzed() {
        val default = fsrs.review(matureReviewCard(), Rating.Good, now).scheduledDays
        val explicitOff = Fsrs6(enableFuzz = false).review(matureReviewCard(), Rating.Good, now).scheduledDays
        assertEquals(default, explicitOff, 0.0)
    }

    @Test
    fun fuzz_staysCloseToUnfuzzedInterval() {
        val raw = fsrs.review(matureReviewCard(), Rating.Good, now).scheduledDays
        val fz = fuzzed.review(matureReviewCard(), Rating.Good, now).scheduledDays
        assertTrue("fuzzed >= 1 day", fz >= 1.0)
        assertTrue("fuzz stays in a small band raw=$raw fz=$fz", kotlin.math.abs(fz - raw) <= raw * 0.3 + 3)
    }

    @Test
    fun fuzz_respectsMaximumInterval() {
        val capped = Fsrs6(enableFuzz = true, maximumInterval = 30)
        val strong = FsrsCard(
            stability = 1000.0, difficulty = 5.0, state = CardState.Review,
            reps = 10, lapses = 0, lastReviewMillis = now - 5 * day,
        )
        val iv = capped.review(strong, Rating.Easy, now).scheduledDays
        assertTrue("capped at maximumInterval was $iv", iv <= 30.0)
    }

    // --- Short-term optimization off (long-term scheduler) ---

    private val longTerm = Fsrs6(enableShortTerm = false)

    @Test
    fun shortTermOff_newGood_schedulesInDaysNotMinutes() {
        val r = longTerm.review(newCard(), Rating.Good, now)
        assertEquals(CardState.Review, r.state)
        assertTrue("graduates straight to a day-scale interval", r.dueMillis - now >= day)
    }

    @Test
    fun shortTermOff_newAgain_graduatesToReviewState() {
        val r = longTerm.review(newCard(), Rating.Again, now)
        assertEquals("no Learning step when short-term is off", CardState.Review, r.state)
        assertTrue(r.dueMillis - now >= day)
    }

    @Test
    fun shortTermOn_byDefault_newGoodIsMinuteStep() {
        // Guard: default behaviour (short-term on) keeps the fixed 10-minute step.
        assertEquals(10 * 60_000L, fsrs.review(newCard(), Rating.Good, now).dueMillis - now)
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
