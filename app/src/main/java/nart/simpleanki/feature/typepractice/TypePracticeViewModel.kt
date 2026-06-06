package nart.simpleanki.feature.typepractice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.SubmitResult
import nart.simpleanki.core.domain.typing.TypeDirection
import nart.simpleanki.core.domain.typing.TypePracticeSession
import nart.simpleanki.core.domain.typing.TypingMastery

/** How long the mint success flash holds before auto-advancing. */
private const val CELEBRATE_MS = 400L

data class TypePracticeUiState(
    val loading: Boolean = true,
    /** Show the direction chooser (start of a session, before any card). */
    val awaitingDirection: Boolean = false,
    /** Chosen direction once the session has started. */
    val direction: TypeDirection? = null,
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
    /** Live combo for the chip (consecutive first-try correct; 0 resets on a miss). */
    val combo: Int = 0,
    /** Session pool size, for the progress bar (progress = (total - remaining)/total). */
    val total: Int = 0,
    /** True during the brief mint success flash before auto-advancing. */
    val celebrating: Boolean = false,
)

/**
 * Drives one Type-Practice session. Decoupled from FSRS: snapshots the deck's typeable cards
 * (respecting the deck's review filter), prompts for a [TypeDirection], runs the pure
 * [TypePracticeSession], and appends exactly one [TypingLog] per card when its first attempt
 * finalizes. No scheduler or review-log writes.
 */
class TypePracticeViewModel(
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val typingLogRepository: TypingLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
    /** Supplies the pool shuffle seed; production uses now(). Return null to disable shuffling (tests). */
    private val shuffleSeed: () -> Long? = { now() },
) : ViewModel() {

    private lateinit var session: TypePracticeSession
    private var deck: Deck? = null
    private var baseCards: List<Card> = emptyList()
    private var poolTotal = 0
    private val _uiState = MutableStateFlow(TypePracticeUiState())
    val uiState: StateFlow<TypePracticeUiState> = _uiState.asStateFlow()

    init { viewModelScope.launch { load() } }

    /** Fetch the deck + its cards, then show the direction chooser (no session yet). */
    private suspend fun load() {
        deck = deckId?.let { deckRepository.getById(it) }
        baseCards = if (deckId != null) cardRepository.observeCards(deckId).first() else emptyList()
        _uiState.value = TypePracticeUiState(loading = false, awaitingDirection = true)
    }

    /** Called from the direction chooser; builds + starts the session in the chosen direction. */
    fun chooseDirection(direction: TypeDirection) {
        viewModelScope.launch { startSession(direction) }
    }

    private suspend fun startSession(direction: TypeDirection) {
        val pool = StudyQueueBuilder.buildReviewQueue(
            cards = baseCards,
            filter = deck?.reviewFilter ?: ReviewCardFilter.All,
            // Type Practice always shuffles (a fresh drill order), independent of the deck's shuffle setting.
            shuffleSeed = shuffleSeed(),
        ).filter { typedSide(it, direction).isNotBlank() }
        poolTotal = pool.size
        val previouslyMastered = TypingMastery.masteredCardIds(
            typingLogRepository.observeLogsForDeck(deckId.orEmpty()).first(),
        )
        session = TypePracticeSession(
            pool = pool,
            previouslyMastered = previouslyMastered,
            onFinalize = { card, correct, typed ->
                viewModelScope.launch {
                    typingLogRepository.append(
                        TypingLog(cardId = card.id, deckId = card.deckId, correct = correct, typedText = typed, timestamp = now()),
                    )
                }
            },
            direction = direction,
        )
        logManager.track(Event.Start(deckId, pool.size))
        _uiState.value = _uiState.value.copy(awaitingDirection = false, direction = direction)
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    /** The side the user TYPES for [direction] (the answer side); blank-skip uses this. */
    private fun typedSide(card: Card, direction: TypeDirection): String =
        if (direction == TypeDirection.TypeFront) card.front else card.back

    fun onInput(text: String) {
        if (_uiState.value.celebrating) return
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun onSubmit() {
        if (!::session.isInitialized || _uiState.value.celebrating) return
        val typed = _uiState.value.input
        val answered = session.current
        when (val r = session.submit(typed)) {
            SubmitResult.Correct -> {
                logManager.track(Event.Answered(true))
                _uiState.value = _uiState.value.copy(
                    celebrating = true,
                    current = answered,
                    input = typed,
                    combo = session.currentCombo,
                    revealing = false,
                )
                viewModelScope.launch {
                    delay(CELEBRATE_MS)
                    renderAdvance()
                    if (session.isFinished) logComplete()
                }
            }
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = r.expected, lastTyped = typed,
                    canOverride = session.canOverride, combo = session.currentCombo,
                )
            }
        }
    }

    /** "Don't know": reveal the answer without an attempt; only Continue is offered. */
    fun onDontKnow() {
        if (!::session.isInitialized || _uiState.value.celebrating) return
        if (session.current == null) return
        when (val r = session.submit("")) {
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = r.expected, lastTyped = "",
                    canOverride = false, combo = session.currentCombo,
                )
            }
            SubmitResult.Correct -> renderAdvance()   // unreachable (the typed side is never blank)
        }
    }

    fun onContinue() {
        if (!::session.isInitialized) return
        session.continueAfterWrong()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun onOverride() {
        if (!::session.isInitialized) return
        session.override()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    /** Restart re-prompts for direction (a new session). */
    fun restart() { viewModelScope.launch { load() } }

    /** Refreshes state from the session after the prompt changes (clears input, bumps autofocus tick). */
    private fun renderAdvance() {
        val prev = _uiState.value
        _uiState.value = prev.copy(
            loading = false,
            awaitingDirection = false,
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
            combo = session.currentCombo,
            total = poolTotal,
            celebrating = false,
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
