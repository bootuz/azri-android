package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ReviewCardFilter

/**
 * Builds study/review queues from a deck's cards, mirroring the iOS study flow.
 *
 * - [buildStudyQueue] = FSRS study: due (non-new) cards first, then up to [newLimit]
 *   brand-new cards, honoring [reviewLimit]. This is what spaced repetition schedules.
 * - [buildReviewQueue] = manual review: every non-deleted card filtered by direction
 *   ([ReviewCardFilter]); not driven by FSRS due dates.
 *
 * Deterministic by default; pass a [shuffleSeed] to randomize order reproducibly.
 */
object StudyQueueBuilder {

    fun buildStudyQueue(
        cards: List<Card>,
        nowMillis: Long,
        newLimit: Int,
        reviewLimit: Int,
        shuffleSeed: Long? = null,
    ): List<Card> {
        val active = cards.filter { !it.isDeleted }
        val due = active
            .filter { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis }
            .sortedBy { it.fsrsDue }
            .take(reviewLimit.coerceAtLeast(0))
        val new = active
            .filter { it.fsrsState == CardState.New.value }
            .sortedBy { it.dateCreated }
            .take(newLimit.coerceAtLeast(0))
        val queue = due + new
        return if (shuffleSeed != null) queue.shuffled(kotlin.random.Random(shuffleSeed)) else queue
    }

    fun buildReviewQueue(
        cards: List<Card>,
        filter: ReviewCardFilter,
        shuffleSeed: Long? = null,
    ): List<Card> {
        val filtered = cards.filter { !it.isDeleted }.filter { card ->
            when (filter) {
                ReviewCardFilter.All -> true
                ReviewCardFilter.OriginalsOnly -> !card.isReverse
                ReviewCardFilter.ReversesOnly -> card.isReverse
            }
        }
        return if (shuffleSeed != null) filtered.shuffled(kotlin.random.Random(shuffleSeed)) else filtered
    }
}
