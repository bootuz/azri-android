package nart.simpleanki.core.domain.streak

/**
 * Persisted, synced streak overlay: freezes earned and the days they cover.
 * [frozenDays], [lastReconciledDay] and [lastRepairDay] are civil-day indices (as produced by
 * `localEpochDay`), not millis.
 */
data class StreakState(
    val freezeTokens: Int = 0,
    val frozenDays: Set<Long> = emptySet(), // civil-day index
    val freezesAwardedForRun: Int = 0,
    val lastReconciledDay: Long = 0, // civil-day index
    val lastRepairDay: Long = 0, // civil-day index
)

/** A streak that can be restored by a (free, limited) repair. */
data class RepairOffer(val restoredStreak: Int)

/**
 * Pure streak-overlay logic. Operates on civil-day indices (bucket with `localEpochDay` first).
 * A frozen day is treated as an active day by [StreakCalculator], so the run survives the gap.
 */
object StreakReconciler {
    const val FREEZE_CAP = 2
    const val FREEZE_EARN_EVERY = 7
    const val REPAIR_COOLDOWN_DAYS = 30L

    /** Advances [state] for [today]: auto-freeze elapsed missed days, then earn freezes. Idempotent. */
    fun reconcile(reviewDays: Set<Long>, state: StreakState, today: Long): StreakState {
        var frozen = state.frozenDays
        var tokens = state.freezeTokens
        var awarded = state.freezesAwardedForRun

        if (today > state.lastReconciledDay) {
            val active = reviewDays + frozen
            val lastActive = active.filter { it <= today }.maxOrNull()
            if (lastActive != null) {
                var d = lastActive + 1
                while (d <= today - 1) {
                    if (d !in reviewDays && d !in frozen) {
                        if (tokens > 0) { frozen = frozen + d; tokens -= 1 } else break
                    }
                    d++
                }
            }
        }

        val current = StreakCalculator.compute(reviewDays + frozen, today).current

        if (state.lastReconciledDay == 0L) {
            awarded = maxOf(awarded, current / FREEZE_EARN_EVERY)
        }
        if (current < awarded * FREEZE_EARN_EVERY) {
            awarded = current / FREEZE_EARN_EVERY
        }
        while (current >= (awarded + 1) * FREEZE_EARN_EVERY) {
            awarded += 1
            if (tokens < FREEZE_CAP) tokens += 1
        }

        return state.copy(
            freezeTokens = tokens,
            frozenDays = frozen,
            freezesAwardedForRun = awarded,
            lastReconciledDay = maxOf(state.lastReconciledDay, today),
        )
    }

    /**
     * A repair is offered for a single missed day at [today]-1, only after studying today, once per
     * cooldown. Assumes [reconcile] has already run for [today] (so an auto-freeze that would cover
     * [today]-1 is already in [state].frozenDays).
     */
    fun repairEligibility(reviewDays: Set<Long>, state: StreakState, today: Long): RepairOffer? {
        if (today !in reviewDays) return null
        if (state.lastRepairDay != 0L && today - state.lastRepairDay < REPAIR_COOLDOWN_DAYS) return null
        val active = reviewDays + state.frozenDays
        if ((today - 1) in active) return null
        val priorRun = runEndingAt(active, today - 2)
        if (priorRun < 1) return null
        return RepairOffer(restoredStreak = priorRun + 2)
    }

    /** Freezes the [today]-1 gap day and records the repair. Caller persists. */
    fun repair(state: StreakState, today: Long): StreakState =
        state.copy(frozenDays = state.frozenDays + (today - 1), lastRepairDay = today)

    private fun runEndingAt(days: Set<Long>, end: Long): Int {
        var n = 0
        var d = end
        while (d in days) { n++; d-- }
        return n
    }
}
