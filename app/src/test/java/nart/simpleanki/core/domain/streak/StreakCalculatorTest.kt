package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test fun empty_isZeroZero() {
        assertEquals(Streak(0, 0), StreakCalculator.compute(emptySet(), today = 100))
    }

    @Test fun onlyToday_isOneOne() {
        assertEquals(Streak(1, 1), StreakCalculator.compute(setOf(100L), today = 100))
    }

    @Test fun consecutiveEndingToday_countsRun() {
        assertEquals(Streak(3, 3), StreakCalculator.compute(setOf(98L, 99L, 100L), today = 100))
    }

    @Test fun studiedYesterdayNotToday_streakStillAlive() {
        assertEquals(Streak(2, 2), StreakCalculator.compute(setOf(98L, 99L), today = 100))
    }

    @Test fun missedAFullDay_currentResetsButLongestPersists() {
        assertEquals(Streak(1, 5), StreakCalculator.compute(setOf(90L, 91L, 92L, 93L, 94L, 100L), today = 100))
    }

    @Test fun neitherTodayNorYesterday_currentZero_longestFromPastRun() {
        assertEquals(Streak(0, 2), StreakCalculator.compute(setOf(96L, 97L), today = 100))
    }
}
