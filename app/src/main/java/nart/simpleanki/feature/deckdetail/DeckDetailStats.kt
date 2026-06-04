package nart.simpleanki.feature.deckdetail

import nart.simpleanki.core.domain.fsrs.IntervalFormatter
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState

/**
 * Relative time until the soonest FUTURE review among non-New cards, e.g. "in 3d".
 * Null when there is no scheduled future review (empty list, all New, or all already due).
 * Unknown FSRS state values are treated as New (excluded), matching `dueLabel` in the screen.
 */
internal fun nextReviewLabel(cards: List<Card>, now: Long): String? {
    val soonest = cards
        .filter { (CardState.fromValue(it.fsrsState) ?: CardState.New) != CardState.New && it.fsrsDue > now }
        .minOfOrNull { it.fsrsDue } ?: return null
    return "in ${IntervalFormatter.format(soonest - now)}"
}
