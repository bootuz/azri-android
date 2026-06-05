package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import nart.simpleanki.core.domain.streak.RepairOffer
import nart.simpleanki.core.domain.streak.StreakReconciler
import nart.simpleanki.core.domain.streak.localEpochDay
import java.util.TimeZone

/**
 * Ties the pure [StreakReconciler] to persisted [StreakStateRepository] + review logs. Call
 * [reconcile] on app foreground and after a session — never inside a Flow (it persists state).
 */
class StreakStateManager(
    private val streakStateRepository: StreakStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    private suspend fun reviewDays(): Set<Long> =
        reviewLogRepository.observeLogs().first().mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }

    suspend fun reconcile() {
        val today = localEpochDay(now(), timeZone)
        val state = streakStateRepository.get()
        val updated = StreakReconciler.reconcile(reviewDays(), state, today)
        if (updated != state) streakStateRepository.update(updated)
    }

    /**
     * Whether a free repair is available for yesterday's gap. Assumes [reconcile] has already run for
     * today, so any auto-freeze that would already cover yesterday is reflected in the persisted state
     * before eligibility is judged.
     *
     * [includeToday] forces today into the day set — for the post-session summary, so the offer is
     * correct even though the per-rating review-log append is fire-and-forget and may not have landed
     * in the logs yet.
     */
    suspend fun repairOffer(includeToday: Boolean = false): RepairOffer? {
        val today = localEpochDay(now(), timeZone)
        val days = reviewDays()
        val effective = if (includeToday) days + today else days
        return StreakReconciler.repairEligibility(effective, streakStateRepository.get(), today)
    }

    /**
     * Applies a repair, freezing yesterday's gap. Callers must serialize this with [reconcile]: the
     * read-modify-write is not atomic. The intended call sites (app foreground + a button handler) are
     * already sequential.
     */
    suspend fun repair() {
        val today = localEpochDay(now(), timeZone)
        streakStateRepository.update(StreakReconciler.repair(streakStateRepository.get(), today))
    }
}
