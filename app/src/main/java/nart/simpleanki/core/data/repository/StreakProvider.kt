package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import nart.simpleanki.core.domain.streak.Streak
import nart.simpleanki.core.domain.streak.StreakCalculator
import nart.simpleanki.core.domain.streak.localEpochDay
import java.util.TimeZone

/** Derives the study [Streak] from the review logs, unioned with frozen days from [StreakStateRepository]. */
class StreakProvider(
    private val reviewLogRepository: ReviewLogRepository,
    private val streakStateRepository: StreakStateRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    /** Live streak for the home header — reacts to new review logs. */
    fun observeStreak(): Flow<Streak> =
        combine(reviewLogRepository.observeLogs(), streakStateRepository.observe()) { logs, state ->
            val days = logs.mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            days += state.frozenDays
            StreakCalculator.compute(days, localEpochDay(now(), timeZone))
        }

    /**
     * Streak treating today as studied — for the post-session summary, so it's correct even though
     * the per-rating log append is fire-and-forget and may not have landed yet.
     */
    suspend fun streakIncludingToday(): Streak {
        val today = localEpochDay(now(), timeZone)
        val days = reviewLogRepository.observeLogs().first()
            .mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
        days += streakStateRepository.get().frozenDays
        days += today
        return StreakCalculator.compute(days, today)
    }
}
