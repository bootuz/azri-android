package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.typing.DeckMastery
import nart.simpleanki.core.domain.typing.TypingMastery

/**
 * Live per-deck typing mastery for the deck-detail ring. The denominator mirrors exactly what Type
 * Practice studies: [StudyQueueBuilder.buildReviewQueue] applies the deck's reviewFilter and drops
 * deleted/memorized cards, then blank-back cards are excluded. Mastered = latest-first-try-correct
 * cards that are still in that typeable pool.
 */
class TypingMasteryProvider(
    private val typingLogRepository: TypingLogRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
) {
    fun observeDeckMastery(deckId: String): Flow<DeckMastery> =
        combine(
            typingLogRepository.observeLogsForDeck(deckId),
            cardRepository.observeCards(deckId),
        ) { logs, cards ->
            val deck = deckRepository.getById(deckId)
            // shuffleSeed = null: a count is order-independent.
            val typeable = StudyQueueBuilder.buildReviewQueue(
                cards = cards,
                filter = deck?.reviewFilter ?: ReviewCardFilter.All,
                shuffleSeed = null,
            ).filter { it.back.isNotBlank() }.map { it.id }.toSet()
            TypingMastery.deckMastery(logs, typeable)
        }
}
