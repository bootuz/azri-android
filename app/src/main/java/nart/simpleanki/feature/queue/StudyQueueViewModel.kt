package nart.simpleanki.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption

/** A deck that has cards waiting in the queue today (for the "Up next" list). */
data class DeckQueueItem(
    val deckId: String,
    val deckName: String,
    val color: ColorOption,
    val dueCount: Int,
    val newCount: Int,
) {
    val total: Int get() = dueCount + newCount
}

data class StudyQueueUiState(
    val loading: Boolean = true,
    /** Cards actually scheduled for today across all decks, after daily limits. */
    val readyCount: Int = 0,
    val newCount: Int = 0,
    val dueCount: Int = 0,
    val estimatedMinutes: Int = 0,
    val decks: List<DeckQueueItem> = emptyList(),
) {
    val hasWork: Boolean get() = readyCount > 0
}

/**
 * The global "Today" queue across every deck: applies the user's FSRS daily limits to all
 * cards combined (mirrors the iOS study-queue home), and breaks down per-deck for "Up next".
 */
class StudyQueueViewModel(
    cardRepository: CardRepository,
    deckRepository: DeckRepository,
    settingsRepository: SettingsRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    val uiState: StateFlow<StudyQueueUiState> =
        combine(
            cardRepository.observeAllCards(),
            deckRepository.observeDecks(),
            settingsRepository.settings,
        ) { cards, decks, settings ->
            val nowMillis = now()
            val queue = StudyQueueBuilder.buildStudyQueue(
                cards = cards,
                nowMillis = nowMillis,
                newLimit = settings.newCardsPerDay,
                reviewLimit = settings.maxReviewsPerDay,
            )
            val newCount = queue.count { it.fsrsState == CardState.New.value }
            val dueCount = queue.size - newCount

            val perDeck = decks.mapNotNull { deck ->
                val deckCards = cards.filter { it.deckId == deck.id && !it.isDeleted }
                val due = deckCards.count { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis }
                val new = deckCards.count { it.fsrsState == CardState.New.value }
                if (due + new == 0) null
                else DeckQueueItem(deck.id, deck.name, deck.color, dueCount = due, newCount = new)
            }.sortedWith(compareByDescending<DeckQueueItem> { it.dueCount }.thenByDescending { it.total })

            StudyQueueUiState(
                loading = false,
                readyCount = queue.size,
                newCount = newCount,
                dueCount = dueCount,
                estimatedMinutes = estimateMinutes(queue.size),
                decks = perDeck,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyQueueUiState(),
        )

    /** Rough study-time estimate: ~9s per card, at least a minute if there's anything to do. */
    private fun estimateMinutes(cardCount: Int): Int =
        if (cardCount == 0) 0 else ((cardCount * 9 + 59) / 60).coerceAtLeast(1)
}
