package nart.simpleanki.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.ReviewCardFilter

data class ReviewUiState(
    val loading: Boolean = true,
    val cards: List<Card> = emptyList(),
)

/**
 * Drives a read-only Review (cram) session: snapshots a deck's or folder's cards once, applies the
 * review filter (direction) + optional shuffle, and exposes the immutable pool. No rating, no FSRS
 * scheduling, no card writes — purely browsing.
 */
class ReviewViewModel(
    /** Deck to review; null when reviewing a folder. */
    private val deckId: String?,
    /** Folder to review (all cards across its decks); null when reviewing a single deck. */
    private val folderId: String?,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val pool = when {
            folderId != null -> {
                val deckIds = deckRepository.observeDecksInFolder(folderId).first().map { it.id }.toSet()
                val cards = cardRepository.observeAllCards().first().filter { it.deckId in deckIds }
                StudyQueueBuilder.buildReviewQueue(cards, ReviewCardFilter.All, shuffleSeed = now())
            }
            deckId != null -> {
                val deck = deckRepository.getById(deckId)
                val cards = cardRepository.observeCards(deckId).first()
                StudyQueueBuilder.buildReviewQueue(
                    cards = cards,
                    filter = deck?.reviewFilter ?: ReviewCardFilter.All,
                    shuffleSeed = if (deck?.shuffled == true) now() else null,
                )
            }
            else -> emptyList()
        }
        _uiState.value = ReviewUiState(loading = false, cards = pool)
        logManager.track(Event.ReviewStart(deckId, folderId, pool.size))
    }

    private sealed interface Event : LoggableEvent {
        data class ReviewStart(val deckId: String?, val folderId: String?, val count: Int) : Event {
            override val eventName = "cram_session_start"
            override val params get() = buildMap<String, Any?> {
                deckId?.let { put("deck_id", it) }
                folderId?.let { put("folder_id", it) }
                put("count", count)
            }
        }
    }
}
