package nart.simpleanki.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.settings.fsrsParameters
import nart.simpleanki.core.domain.fsrs.IntervalFormatter
import nart.simpleanki.core.domain.fsrs.SchedulingService
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.Rating

data class StudyUiState(
    val loading: Boolean = true,
    val current: Card? = null,
    val isRevealed: Boolean = false,
    val completed: Int = 0,
    val remaining: Int = 0,
    val ratingCounts: Map<Rating, Int> = emptyMap(),
    /** Next-due interval label per rating for the current card (e.g. Good -> "4d"), shown on the answer buttons. */
    val ratingIntervals: Map<Rating, String> = emptyMap(),
    val finished: Boolean = false,
)

/**
 * Drives one study session: reads the user's FSRS preset + daily limits from settings,
 * builds the queue, reveals the answer on flip, and on each rating applies the scheduler,
 * persists the updated card, and advances.
 */
class StudyViewModel(
    /** Deck to study, or null to study the global queue across every deck. */
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val queue = ArrayDeque<Card>()
    private lateinit var scheduling: SchedulingService
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsRepository.settings.first()
        scheduling = SchedulingService(settings.fsrsParameters())
        val all = if (deckId != null) {
            cardRepository.observeCards(deckId).first()
        } else {
            cardRepository.observeAllCards().first()
        }
        queue.clear()
        queue.addAll(
            StudyQueueBuilder.buildStudyQueue(
                cards = all,
                nowMillis = now(),
                // Daily-goal targets are a soft goal, NOT a queue cap — study everything available.
                newLimit = Int.MAX_VALUE,
                reviewLimit = Int.MAX_VALUE,
            ),
        )
        val first = queue.firstOrNull()
        _uiState.value = StudyUiState(
            loading = false,
            current = first,
            remaining = queue.size,
            ratingIntervals = intervalsFor(first),
            finished = queue.isEmpty(),
        )
    }

    /** Formats the next-due interval per rating for [card] (empty when there's no card). */
    private fun intervalsFor(card: Card?): Map<Rating, String> {
        if (card == null) return emptyMap()
        val nowMillis = now()
        return scheduling.preview(card, nowMillis)
            .mapValues { (_, dueMillis) -> IntervalFormatter.format(dueMillis - nowMillis) }
    }

    fun onReveal() {
        if (_uiState.value.current != null) {
            _uiState.value = _uiState.value.copy(isRevealed = true)
        }
    }

    fun onRate(rating: Rating) {
        val card = _uiState.value.current ?: return
        val result = scheduling.schedule(card, rating, now())
        viewModelScope.launch { cardRepository.save(result.card) }

        queue.removeFirstOrNull()
        val prev = _uiState.value
        val counts = prev.ratingCounts.toMutableMap().apply { this[rating] = (this[rating] ?: 0) + 1 }
        val next = queue.firstOrNull()
        _uiState.value = prev.copy(
            current = next,
            isRevealed = false,
            completed = prev.completed + 1,
            remaining = queue.size,
            ratingCounts = counts,
            ratingIntervals = intervalsFor(next),
            finished = next == null,
        )
    }
}
