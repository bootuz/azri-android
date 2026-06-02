package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog

/** FSRS presets mirroring the iOS app (requestRetention / maximumInterval). */
enum class FsrsPreset(val requestRetention: Double, val maximumInterval: Int) {
    Optimal(0.90, 365),
    Aggressive(0.95, 90),
    Relaxed(0.85, 365);
}

/** A scheduled review: the updated card plus the log entry to append to history. */
data class ScheduleResult(
    val card: Card,
    val log: ReviewLog,
)

/**
 * Applies FSRS-6 scheduling to domain [Card]s. The only place the rest of the app
 * touches the scheduler, so a future algorithm change stays behind this seam.
 */
class SchedulingService(
    private val fsrs: Fsrs6 = Fsrs6(),
) {
    constructor(preset: FsrsPreset) : this(
        Fsrs6(requestRetention = preset.requestRetention, maximumInterval = preset.maximumInterval),
    )

    fun retrievability(card: Card, nowMillis: Long): Double {
        if (card.fsrsLastReview == null || card.fsrsStability <= 0.0) return 0.0
        val elapsedDays = (nowMillis - card.fsrsLastReview).coerceAtLeast(0L) / 86_400_000.0
        return fsrs.retrievability(elapsedDays, card.fsrsStability)
    }

    /**
     * Previews the next due date (epoch millis) for every rating WITHOUT committing the review.
     * Mirrors iOS `previewScheduling`; relies on [schedule] being pure (no mutation/persistence).
     */
    fun preview(card: Card, nowMillis: Long): Map<Rating, Long> =
        Rating.entries.associateWith { schedule(card, it, nowMillis).card.fsrsDue }

    fun schedule(card: Card, rating: Rating, nowMillis: Long): ScheduleResult {
        val current = FsrsCard(
            stability = card.fsrsStability,
            difficulty = card.fsrsDifficulty,
            state = CardState.fromValue(card.fsrsState) ?: CardState.New,
            reps = card.fsrsReps,
            lapses = card.fsrsLapses,
            lastReviewMillis = card.fsrsLastReview,
        )
        val r = fsrs.review(current, rating, nowMillis)
        val updated = card.copy(
            fsrsStability = r.stability,
            fsrsDifficulty = r.difficulty,
            fsrsState = r.state.value,
            fsrsReps = r.reps,
            fsrsLapses = r.lapses,
            fsrsDue = r.dueMillis,
            fsrsScheduledDays = r.scheduledDays,
            fsrsElapsedDays = r.elapsedDays,
            fsrsLastReview = r.lastReviewMillis,
            lastModified = nowMillis,
        )
        val log = ReviewLog(
            rating = rating,
            state = r.state,
            due = r.dueMillis,
            stability = r.stability,
            difficulty = r.difficulty,
            elapsedDays = r.elapsedDays,
            lastElapsedDays = card.fsrsElapsedDays,
            scheduledDays = r.scheduledDays,
            review = r.lastReviewMillis,
        )
        return ScheduleResult(updated, log)
    }
}
