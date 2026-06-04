package nart.simpleanki.core.domain.fsrs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState

/**
 * Pairs each emitted card list with a "now" timestamp that re-emits the instant the next card
 * becomes due.
 *
 * Dueness is purely time-based — no data change fires when a card crosses its fsrsDue — so this
 * operator manufactures that event: for a given card set it emits now, finds the soonest FUTURE
 * due (non-New, fsrsDue > now), sleeps exactly until then, re-emits with a fresh now, and
 * reschedules. When the upstream card list changes, flatMapLatest cancels the pending wait and
 * restarts with the new list (recomputing the deadline). When no card is due in the future, it
 * emits once and idles — no busy-wait — until the card list changes.
 *
 * Only non-New cards are watched: New cards are already available, so only review cards crossing
 * their fsrsDue change what is studyable over time (matches StudyQueueBuilder.buildStudyQueue).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<List<Card>>.withDueTicks(now: () -> Long): Flow<Pair<List<Card>, Long>> =
    flatMapLatest { cards ->
        flow {
            while (true) {
                val nowMillis = now()
                emit(cards to nowMillis)
                val nextDue = cards
                    .filter { !it.isDeleted && it.fsrsState != CardState.New.value && it.fsrsDue > nowMillis }
                    .minOfOrNull { it.fsrsDue } ?: break
                delay((nextDue - nowMillis).coerceAtLeast(0))
            }
        }
    }
