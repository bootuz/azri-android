package nart.simpleanki.feature.study

import nart.simpleanki.core.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsTest {

    @Test fun accuracy_typicalMix_isCorrectRounded() {
        // good+easy = 15 of 25 reviews → 60%
        val counts = mapOf(Rating.Again to 4, Rating.Hard to 6, Rating.Good to 10, Rating.Easy to 5)
        assertEquals(60, sessionAccuracy(counts))
    }

    @Test fun accuracy_allEasy_is100() {
        assertEquals(100, sessionAccuracy(mapOf(Rating.Easy to 10)))
    }

    @Test fun accuracy_noReviews_isZero() {
        assertEquals(0, sessionAccuracy(emptyMap()))
    }

    @Test fun accuracy_rounds_toNearest() {
        // 5 good of 7 = 71.43% → 71
        assertEquals(71, sessionAccuracy(mapOf(Rating.Good to 5, Rating.Again to 2)))
        // 6 good of 7 = 85.71% → 86
        assertEquals(86, sessionAccuracy(mapOf(Rating.Good to 6, Rating.Again to 1)))
    }

    @Test fun message_matchesThresholds() {
        assertEquals("Outstanding session!", motivationalMessage(100))
        assertEquals("Outstanding session!", motivationalMessage(90))
        assertEquals("Great work, keep it up!", motivationalMessage(70))
        assertEquals("Great work, keep it up!", motivationalMessage(89))
        assertEquals("Solid effort, you're improving!", motivationalMessage(50))
        assertEquals("Solid effort, you're improving!", motivationalMessage(69))
        assertEquals("Every review makes you stronger!", motivationalMessage(49))
        assertEquals("Every review makes you stronger!", motivationalMessage(0))
    }

    @Test fun duration_formatsAbbreviated() {
        assertEquals("0s", formattedDuration(0))
        assertEquals("42s", formattedDuration(42_000))
        assertEquals("5m", formattedDuration(300_000))
        assertEquals("5m 12s", formattedDuration(312_000))
        assertEquals("60m", formattedDuration(3_600_000))
    }

    @Test fun duration_negative_clampsToZero() {
        assertEquals("0s", formattedDuration(-1_000))
    }
}
