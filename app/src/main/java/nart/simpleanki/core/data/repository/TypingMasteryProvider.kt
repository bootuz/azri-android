package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nart.simpleanki.core.domain.typing.DeckMastery
import nart.simpleanki.core.domain.typing.TypingMastery

/**
 * Live per-deck typing mastery for the deck-detail ring. Derives from logs + the deck's current
 * (non-deleted, typeable) cards — blank-back cards are excluded, matching what Type Practice studies.
 */
class TypingMasteryProvider(
    private val typingLogRepository: TypingLogRepository,
    private val cardRepository: CardRepository,
) {
    fun observeDeckMastery(deckId: String): Flow<DeckMastery> =
        combine(
            typingLogRepository.observeLogsForDeck(deckId),
            cardRepository.observeCards(deckId),
        ) { logs, cards ->
            val typeable = cards.filter { it.back.isNotBlank() }.map { it.id }.toSet()
            TypingMastery.deckMastery(logs, typeable)
        }
}
