package nart.simpleanki.feature.typepractice

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
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.SubmitResult
import nart.simpleanki.core.domain.typing.TypePracticeSession
import nart.simpleanki.core.domain.typing.TypingMastery

data class TypePracticeUiState(
    val loading: Boolean = true,
    val current: Card? = null,
    val input: String = "",
    /** Showing the correct answer after a wrong submit. */
    val revealing: Boolean = false,
    val revealedAnswer: String = "",
    val lastTyped: String = "",
    /** Whether "I was right" is offered (first attempts only). */
    val canOverride: Boolean = false,
    val remaining: Int = 0,
    val finished: Boolean = false,
    val report: SessionReport? = null,
    /** Increments whenever the prompt card changes; the screen keys autofocus on it. */
    val cardTick: Int = 0,
)

/**
 * Drives one Type-Practice session. Decoupled from FSRS: snapshots the deck's typeable cards
 * (respecting the deck's review filter), runs the pure [TypePracticeSession], and appends exactly
 * one [TypingLog] per card when its first attempt finalizes. No scheduler or review-log writes.
 */
class TypePracticeViewModel(
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val typingLogRepository: TypingLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private lateinit var session: TypePracticeSession
    private val _uiState = MutableStateFlow(TypePracticeUiState())
    val uiState: StateFlow<TypePracticeUiState> = _uiState.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val deck = deckId?.let { deckRepository.getById(it) }
        val cards = if (deckId != null) cardRepository.observeCards(deckId).first() else emptyList()
        val pool = StudyQueueBuilder.buildReviewQueue(
            cards = cards,
            filter = deck?.reviewFilter ?: ReviewCardFilter.All,
            // Type Practice always shuffles (a fresh drill order), independent of the deck's shuffle setting.
            shuffleSeed = now(),
        ).filter { it.back.isNotBlank() }
        val previouslyMastered = TypingMastery.masteredCardIds(
            typingLogRepository.observeLogsForDeck(deckId.orEmpty()).first(),
        )
        session = TypePracticeSession(pool, previouslyMastered) { card, correct, typed ->
            viewModelScope.launch {
                typingLogRepository.append(
                    TypingLog(cardId = card.id, deckId = card.deckId, correct = correct, typedText = typed, timestamp = now()),
                )
            }
        }
        logManager.track(Event.Start(deckId, pool.size))
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun onInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun onSubmit() {
        val typed = _uiState.value.input
        when (val r = session.submit(typed)) {
            SubmitResult.Correct -> {
                logManager.track(Event.Answered(true))
                renderAdvance()
                if (session.isFinished) logComplete()
            }
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = r.expected, lastTyped = typed, canOverride = session.canOverride,
                )
            }
        }
    }

    /** "Don't know": reveal the answer without an attempt; only Continue is offered. */
    fun onDontKnow() {
        val card = session.current ?: return
        when (session.submit("")) {
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = card.back, lastTyped = "", canOverride = false,
                )
            }
            SubmitResult.Correct -> renderAdvance()   // unreachable (blank backs are filtered out)
        }
    }

    fun onContinue() {
        session.continueAfterWrong()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun onOverride() {
        session.override()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun restart() { viewModelScope.launch { load() } }

    /** Refreshes state from the session after the prompt changes (clears input, bumps autofocus tick). */
    private fun renderAdvance() {
        val prev = _uiState.value
        _uiState.value = prev.copy(
            loading = false,
            current = session.current,
            input = "",
            revealing = false,
            revealedAnswer = "",
            lastTyped = "",
            canOverride = false,
            remaining = session.remaining,
            finished = session.isFinished,
            report = if (session.isFinished) session.report() else null,
            cardTick = prev.cardTick + 1,
        )
    }

    private fun logComplete() {
        val r = session.report()
        logManager.track(Event.Complete(r.completed, r.firstTryAccuracy))
    }

    private sealed interface Event : LoggableEvent {
        data class Start(val deckId: String?, val count: Int) : Event {
            override val eventName = "type_practice_start"
            override val params get() = buildMap<String, Any?> {
                deckId?.let { put("deck_id", it) }
                put("count", count)
            }
        }
        data class Answered(val correct: Boolean) : Event {
            override val eventName = "type_practice_answer"
            override val params get() = mapOf("correct" to correct)
        }
        data class Complete(val count: Int, val accuracy: Int) : Event {
            override val eventName = "type_practice_complete"
            override val params get() = mapOf("count" to count, "accuracy" to accuracy)
        }
    }
}
