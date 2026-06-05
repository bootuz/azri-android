package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreakReconcilerTest {

    private fun seeded(today: Long, tokens: Int = 0, awarded: Int = 0, frozen: Set<Long> = emptySet()) =
        StreakState(freezeTokens = tokens, frozenDays = frozen, freezesAwardedForRun = awarded, lastReconciledDay = today)

    @Test
    fun autoFreeze_coversSingleMissedDay_whenTokenAvailable() {
        val reviews = setOf(1L, 2, 3, 4, 5)
        val state = seeded(today = 5, tokens = 1)
        val out = StreakReconciler.reconcile(reviews, state, today = 7)
        assertEquals(setOf(6L), out.frozenDays)
        assertEquals(0, out.freezeTokens)
        assertEquals(6, StreakCalculator.compute(reviews + out.frozenDays, 7).current)
    }

    @Test
    fun autoFreeze_breaks_whenNoTokens() {
        val reviews = setOf(1L, 2, 3, 4, 5)
        val state = seeded(today = 5, tokens = 0)
        val out = StreakReconciler.reconcile(reviews, state, today = 7)
        assertEquals(emptySet<Long>(), out.frozenDays)
        assertEquals(0, StreakCalculator.compute(reviews + out.frozenDays, 7).current)
    }

    @Test
    fun earn_grantsOneFreezePerSevenDays_cappedAtTwo() {
        val reviews = (1L..7L).toSet()
        val out = StreakReconciler.reconcile(reviews, seeded(today = 6), today = 7)
        assertEquals(1, out.freezeTokens)
        assertEquals(1, out.freezesAwardedForRun)
        val reviews3 = (1L..21L).toSet()
        val out3 = StreakReconciler.reconcile(reviews3, seeded(today = 20), today = 21)
        assertEquals(2, out3.freezeTokens)
        assertEquals(3, out3.freezesAwardedForRun)
    }

    @Test
    fun earn_isIdempotent_acrossRepeatedReconciles() {
        val reviews = (1L..7L).toSet()
        val once = StreakReconciler.reconcile(reviews, seeded(today = 6), today = 7)
        val twice = StreakReconciler.reconcile(reviews, once, today = 7)
        assertEquals(once.freezeTokens, twice.freezeTokens)
        assertEquals(once.freezesAwardedForRun, twice.freezesAwardedForRun)
    }

    @Test
    fun firstRun_doesNotFloodFreezes_forPreexistingStreak() {
        val reviews = (1L..10L).toSet()
        val out = StreakReconciler.reconcile(reviews, StreakState(), today = 10)
        assertEquals(0, out.freezeTokens)
        assertEquals(1, out.freezesAwardedForRun)
    }

    @Test
    fun brokenRun_resetsAwardCounter() {
        val reviews = setOf(20L)
        val out = StreakReconciler.reconcile(reviews, seeded(today = 19, awarded = 3), today = 20)
        assertEquals(0, out.freezesAwardedForRun)
    }

    @Test
    fun repair_offeredOnlyForSingleRecentGap_afterStudyingToday() {
        val reviews = setOf(1L, 2, 3, 4, 5, 7)
        val state = seeded(today = 7)
        val offer = StreakReconciler.repairEligibility(reviews, state, today = 7)
        assertNotNull(offer)
        assertEquals(7, offer!!.restoredStreak)
        val repaired = StreakReconciler.repair(state, today = 7)
        assertEquals(setOf(6L), repaired.frozenDays)
        assertEquals(7L, repaired.lastRepairDay)
        assertEquals(7, StreakCalculator.compute(reviews + repaired.frozenDays, 7).current)
    }

    @Test
    fun repair_cooldownBoundary_29DaysBlocked_30DaysAllowed() {
        val reviews = setOf(1L, 2, 3, 4, 5, 7)
        // 29 days since last repair → still within cooldown → no offer.
        assertNull(StreakReconciler.repairEligibility(reviews, seeded(today = 7).copy(lastRepairDay = 7 - 29), today = 7))
        // 30 days since last repair → cooldown elapsed → offered.
        assertNotNull(StreakReconciler.repairEligibility(reviews, seeded(today = 7).copy(lastRepairDay = 7 - 30), today = 7))
    }

    @Test
    fun repair_notOffered_withoutStudyingToday_orWithinCooldown_orMultiDayGap() {
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 5), seeded(today = 7), today = 7))
        val recentRepair = seeded(today = 7).copy(lastRepairDay = 6)
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 5, 7), recentRepair, today = 7))
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 7), seeded(today = 7), today = 7))
    }
}
