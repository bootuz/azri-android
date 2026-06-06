package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.Card

/** Result of submitting a typed answer. */
sealed interface SubmitResult {
    data object Correct : SubmitResult
    data class Wrong(val expected: String) : SubmitResult
}

/** End-of-session summary (first-try based; accuracy is a 0..100 percent). */
data class SessionReport(
    val completed: Int,
    val firstTryCorrect: Int,
    val firstTryAccuracy: Int,
    val bestCombo: Int,
    val newlyMastered: Int,
)

/**
 * In-memory state machine for one Type-Practice session — Android-free and unit-testable.
 *
 * Whole deck, retry-until-correct: a wrong FIRST attempt is revealed then requeued to later in the
 * session; the card's first-attempt correctness is what's scored. Each first-attempt outcome is
 * finalized exactly once (correct-on-first, or after [continueAfterWrong] / [override]) and emitted
 * to [onFinalize] so the caller persists exactly one log per card. Requeued retries clear the loop
 * but never re-score and never emit.
 */
class TypePracticeSession(
    pool: List<Card>,
    private val previouslyMastered: Set<String> = emptySet(),
    private val onFinalize: (card: Card, correct: Boolean, typed: String) -> Unit = { _, _, _ -> },
    private val direction: TypeDirection = TypeDirection.TypeBack,
) {
    private val queue = ArrayDeque(pool)
    private val firstTry = LinkedHashMap<String, Boolean>()   // finalized first-try outcome per card
    private var combo = 0
    private var bestCombo = 0

    // Reveal state after a wrong submit, until continue/override.
    private var awaiting = false
    private var awaitingFirstAttempt = false
    private var awaitingTyped = ""

    private fun answerOf(card: Card): String =
        if (direction == TypeDirection.TypeFront) card.front else card.back

    val current: Card? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size
    val isFinished: Boolean get() = queue.isEmpty()
    /** True while a wrong answer is revealed, awaiting Continue/override. */
    val isRevealing: Boolean get() = awaiting
    /** "I was right" is only offered on a first attempt. */
    val canOverride: Boolean get() = awaiting && awaitingFirstAttempt

    fun submit(answer: String): SubmitResult {
        val card = current ?: return SubmitResult.Correct
        if (awaiting) return SubmitResult.Wrong(answerOf(card))   // UI gates this; be safe
        val firstAttempt = card.id !in firstTry
        return if (AnswerMatcher.matches(answer, answerOf(card))) {
            if (firstAttempt) {
                firstTry[card.id] = true
                combo += 1
                if (combo > bestCombo) bestCombo = combo
                onFinalize(card, true, answer)
            }
            queue.removeFirst()
            SubmitResult.Correct
        } else {
            combo = 0
            awaiting = true
            awaitingFirstAttempt = firstAttempt
            awaitingTyped = answer
            SubmitResult.Wrong(answerOf(card))
        }
    }

    /** Dismiss the reveal as wrong: finalize the first-try outcome (once) and requeue the card. */
    fun continueAfterWrong() {
        val card = current ?: return
        if (!awaiting) return
        if (awaitingFirstAttempt && card.id !in firstTry) {
            firstTry[card.id] = false
            onFinalize(card, false, awaitingTyped)
        }
        awaiting = false
        queue.removeFirst()
        queue.addLast(card)                                       // returns later this session
    }

    /** "I was right" — first attempts only: finalize correct and clear the card. */
    fun override() {
        val card = current ?: return
        if (!awaiting || !awaitingFirstAttempt) return
        if (card.id !in firstTry) {
            firstTry[card.id] = true
            onFinalize(card, true, awaitingTyped)
        }
        // Override does not advance the combo — the player needed the reveal.
        awaiting = false
        queue.removeFirst()
    }

    fun report(): SessionReport {
        val completed = firstTry.size
        val correct = firstTry.values.count { it }
        return SessionReport(
            completed = completed,
            firstTryCorrect = correct,
            firstTryAccuracy = if (completed == 0) 0 else correct * 100 / completed,
            bestCombo = bestCombo,
            newlyMastered = firstTry.count { (k, v) -> v && k !in previouslyMastered },
        )
    }
}
