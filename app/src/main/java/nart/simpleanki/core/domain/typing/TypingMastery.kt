package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.TypingLog

/** Mastered/total typed cards for one deck. */
data class DeckMastery(val mastered: Int, val total: Int)

/**
 * Derives typing mastery purely from logs (single source of truth, like StreakProvider over review
 * logs). A card is "mastered" iff its LATEST first-attempt log is correct, so mastery regresses
 * honestly when a later session misses it.
 */
object TypingMastery {
    fun latestPerCard(logs: List<TypingLog>): Map<String, TypingLog> =
        logs.groupBy { it.cardId }.mapValues { (_, group) -> group.maxBy { it.timestamp } }

    fun masteredCardIds(logs: List<TypingLog>): Set<String> =
        latestPerCard(logs).filterValues { it.correct }.keys

    fun deckMastery(logs: List<TypingLog>, deckCardIds: Set<String>): DeckMastery =
        DeckMastery(
            mastered = masteredCardIds(logs).count { it in deckCardIds },
            total = deckCardIds.size,
        )
}
