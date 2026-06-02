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
import nart.simpleanki.core.data.settings.dailyGoalTotal
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import java.util.Calendar
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
    /** Cards ready to study today across all decks (uncapped — the daily goal does not limit this). */
    val readyCount: Int = 0,
    val newCount: Int = 0,
    val dueCount: Int = 0,
    val estimatedMinutes: Int = 0,
    val decks: List<DeckQueueItem> = emptyList(),
    // Daily goal (soft target). [goalMet] is independent of [hasWork]: you can hit your goal
    // with cards still queued, or clear the queue without reaching it.
    val dailyGoalEnabled: Boolean = true,
    val goalTotal: Int = 0,
    val studiedToday: Int = 0,
) {
    val hasWork: Boolean get() = readyCount > 0
    val goalMet: Boolean get() = dailyGoalEnabled && goalTotal > 0 && studiedToday >= goalTotal
    val goalRemaining: Int get() = (goalTotal - studiedToday).coerceAtLeast(0)
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
                // Uncapped: the daily goal is a soft target, not a queue limit.
                newLimit = Int.MAX_VALUE,
                reviewLimit = Int.MAX_VALUE,
            )
            val newCount = queue.count { it.fsrsState == CardState.New.value }
            val dueCount = queue.size - newCount

            val startOfToday = startOfDay(nowMillis)
            val studiedToday = cards.count {
                !it.isDeleted && (it.fsrsLastReview ?: 0L) >= startOfToday
            }

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
                dailyGoalEnabled = settings.dailyGoalEnabled,
                goalTotal = settings.dailyGoalTotal,
                studiedToday = studiedToday,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyQueueUiState(),
        )

    /** Rough study-time estimate: ~9s per card, at least a minute if there's anything to do. */
    private fun estimateMinutes(cardCount: Int): Int =
        if (cardCount == 0) 0 else ((cardCount * 9 + 59) / 60).coerceAtLeast(1)

    /** Device-local midnight for [nowMillis] — the cutoff for "studied today". */
    private fun startOfDay(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
