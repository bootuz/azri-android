# Real-Time Study Queue Updates — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/realtime-due-queue` (off `main`; does not touch the flip-card files in the
open PR #11, so no conflict).

## Goal

Make the study-queue surfaces refresh **the instant a card becomes due**, without requiring a data
change, navigation, or app restart. Specifically: the global **Today/Queue home**
(`StudyQueueViewModel`) and the **deck-detail screen** (`DeckDetailViewModel`) should update their
due/new counts, per-deck/per-folder chips, queue preview, and the Study ↔ "You're all caught up!"
action slot live as wall-clock time crosses each card's `fsrsDue`.

## Background: root cause

Both view models compute dueness as `fsrsState != New && fsrsDue <= now()` inside a `combine { }`
block:

- `StudyQueueViewModel.uiState` combines `observeAllCards()`, `observeDecks()`,
  `observeFolders()`, `settings`, `entitlement`; it reads `val nowMillis = now()` once inside the
  lambda and derives `readyCount`, `dueCount`/`newCount`, per-deck and per-folder chips, and the
  queue preview from it.
- `DeckDetailViewModel.uiState` combines `observeCards(deckId)`, `queryFlow`, `deckNameFlow`; it
  reads `val nowMillis = now()` once and derives `dueCount`/`newCount`.

A `combine` lambda only re-runs when one of its **upstream Flows emits**. A card "becoming due" is
purely wall-clock time crossing `fsrsDue` — **no data changes at that instant** — so nothing
re-emits, `now()` is never re-sampled, and the counts stay stale until some unrelated event (a card
edit, a sync, a settings change, or re-subscription after the `WhileSubscribed(5_000)` timeout)
forces a recompute. There is currently **no time-ticker / clock Flow** anywhere in the codebase.

**Decision (from brainstorming): exact-moment scheduling.** Rather than blind polling, manufacture
the "card became due" event: from the current card set, find the soonest future `fsrsDue`, sleep
precisely until then, re-emit, and reschedule. This is the event-driven ("listener") equivalent —
there is no free OS/data event for dueness, so we create a timer at the exact deadline.
(`AlarmManager`/`WorkManager` were rejected: they're background/notification tools — `WorkManager`'s
periodic minimum is 15 min — and wrong for a live on-screen counter.)

## Components

### `core/domain/fsrs/DueTicker.kt` (new — the entire mechanism)

A single Flow operator that pairs each card list with a `now` timestamp that re-emits exactly when
the next card becomes due:

```kotlin
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
```

Behavior:
- **Emit → schedule → re-emit:** emits `(cards, now)`, computes the soonest future due
  (`non-New && fsrsDue > now`), `delay`s precisely until then, then re-emits with a fresh `now`
  (so that card now satisfies `fsrsDue <= now` downstream), and loops to the next deadline.
- **Cards change:** `flatMapLatest` cancels the in-flight `delay` and restarts the inner flow with
  the new list — recomputing the deadline. Handles add/edit/delete/rate for free.
- **No future-due card** (empty, all-New, or all-already-due): `minOfOrNull` returns `null` →
  `break` → emit once, then idle. No wasted wake-ups.
- **`@OptIn(ExperimentalCoroutinesApi::class)` is contained in this file;** callers receive a plain
  `Flow<Pair<List<Card>, Long>>` and need no opt-in.
- `coerceAtLeast(0)` guards a race where a card is already due by the time the deadline is computed.

The "future-due" filter intentionally watches only **non-New** cards crossing `fsrsDue`, matching
`StudyQueueBuilder.buildStudyQueue` semantics: New cards are already available, so only review
cards crossing their due time change what is studyable over time.

### `feature/queue/StudyQueueViewModel.kt` (modify, ~3 lines)

Apply the operator to the cards flow and consume the reactive timestamp:

```kotlin
combine(
    cardRepository.observeAllCards().withDueTicks(now),   // was: cardRepository.observeAllCards()
    deckRepository.observeDecks(),
    folderRepository.observeFolders(),
    settingsRepository.settings,
    entitlementRepository.entitlement,
) { (cards, nowMillis), decks, folders, settings, entitlement ->
    // DELETE the old `val nowMillis = now()` — everything below already uses `nowMillis`.
    ...
}
```

Add `import nart.simpleanki.core.domain.fsrs.withDueTicks`. The lambda already funnels every
derived value (`readyCount`, `dueCount`/`newCount`, `perDeck`, `perFolder`, `queueCards`,
`studiedToday`'s `startOfDay(nowMillis)`) through a single `nowMillis`, so one destructure makes the
whole screen live. Stays at 5 `combine` inputs (no array-combine needed).

### `feature/deckdetail/DeckDetailViewModel.kt` (modify, ~3 lines)

```kotlin
combine(
    cardRepository.observeCards(deckId).withDueTicks(now),   // was: cardRepository.observeCards(deckId)
    queryFlow,
    deckNameFlow,
) { (cards, nowMillis), query, name ->
    // DELETE the old `val nowMillis = now()`.
    ...
}
```

Add `import nart.simpleanki.core.domain.fsrs.withDueTicks`. Makes the Due/New stat card and the
Study ↔ "You're all caught up!" action slot flip live the instant a card comes due. Stays at 3
`combine` inputs.

## Data flow

`observeCards()` emits the card set → `withDueTicks` emits `(cards, now)` immediately and schedules
a `delay` until the soonest `fsrsDue` → at that exact instant it re-emits `(cards, laterNow)` →
`combine` recomputes counts → `StateFlow` pushes new state to Compose. `WhileSubscribed(5_000)`
already gates the subscription: when the screen leaves, the ticker's `delay` is cancelled within
~5 s (zero background battery); when it returns, the operator restarts and emits the current time
immediately.

## Error handling

Pure presentation timing — no new I/O or failure modes. `coerceAtLeast(0)` guards an already-due
race; `minOfOrNull ?: break` handles the empty / all-New / all-already-due cases without a
busy-wait. Arbitrarily long `delay`s (days) are valid and are realistically cancelled by
`WhileSubscribed` long before firing.

## Testing

- **`DueTickerTest`** (new, JVM `runTest` + turbine, mirroring `StudyQueueViewModelTest`'s
  `UnconfinedTestDispatcher` + `var`-clock idiom — a `var nowMs` returned by `now`, advanced
  alongside `advanceTimeBy`):
  - emits immediately with the initial `now`;
  - after advancing virtual time (and the clock) past a future `fsrsDue`, re-emits with the updated
    `now`;
  - idles (no second emission) when only New and/or already-due cards exist;
  - re-emits immediately when the source card list changes (flatMapLatest restart);
  - with two future-due cards, fires at the soonest first, then at the next.
- **`StudyQueueViewModelTest`**: add a case — a review card with `fsrsDue` slightly in the future
  starts uncounted (`dueCount`/`readyCount` exclude it); after advancing virtual time past its due,
  it appears. Existing assertions stay green (the operator is transparent at `t = now`).
- **Deck-detail VM test** (`LibraryAndDeckDetailViewModelTest`): analogous case — a near-future
  review card moves into `dueCount` after time advances.

**Build/test prefix:** all Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`. The emulator is currently
unavailable, so instrumented (`androidTest`) sources are compile-verified only; these new tests are
JVM unit tests and run normally.

## Out of scope

- **Active study session** (`StudyViewModel`): stays a fixed snapshot built once with `.first()`.
  Appending newly-due cards mid-session is jarring; the user finishes and re-enters to pick them up.
- **`studiedToday` midnight rollover**: a different time boundary (device-local midnight), not a
  due-based one; not driven by this ticker.
- **OS background / notification refresh** (`AlarmManager`/`WorkManager`): background concern,
  separate from live in-app UI.
- **HomeScreen**: not a due-count surface.
- No change to FSRS scheduling, the queue-building algorithm, or the study flow itself.
