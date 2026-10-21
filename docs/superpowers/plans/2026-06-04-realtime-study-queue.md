# Real-Time Study Queue Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Today/Queue home and deck-detail screens refresh their due/new counts the exact instant a card crosses its `fsrsDue`, with no data change or restart.

**Architecture:** Introduce a single Flow operator `withDueTicks` (in `core/domain/fsrs/DueTicker.kt`) that pairs each card list with a `now` timestamp re-emitted precisely at the next card's due moment (via `flatMapLatest` + `delay`). Both `StudyQueueViewModel` and `DeckDetailViewModel` apply it to their cards flow and consume the reactive `nowMillis` instead of a one-shot `now()`. No state, scheduling, or UI changes.

**Tech Stack:** Kotlin, kotlinx.coroutines Flow (`flatMapLatest`, `delay`), JUnit4 + `kotlinx-coroutines-test` (`runTest`, virtual time) + Turbine (`app.cash.turbine.test`).

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Spec:** `docs/superpowers/specs/2026-06-04-realtime-study-queue-design.md`.

**Note:** the new tests are plain JVM unit tests (`app/src/test`) and run normally; no emulator needed.

---

### Task 1: `withDueTicks` Flow operator (the whole mechanism)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/fsrs/DueTicker.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/fsrs/DueTickerTest.kt`

TDD: write the test first (it won't compile until the operator exists), then implement.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/domain/fsrs/DueTickerTest.kt`:

```kotlin
package nart.simpleanki.core.domain.fsrs

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DueTickerTest {

    private val base = 1_700_000_000_000L

    private fun reviewCard(id: String, due: Long) = Card(
        id = id, front = "Q", back = "A", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = CardState.Review.value,
    )

    private fun newCard(id: String) = Card(
        id = id, front = "Q", back = "A", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    // `clock` is tied to the test scheduler's virtual time so that as runTest auto-advances past
    // a `delay`, now() reflects the advanced time.
    @Test
    fun emitsImmediately_thenCompletes_whenNoFutureDue() = runTest {
        val clock = { base + testScheduler.currentTime }
        flowOf(listOf(newCard("n1"))).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)   // emits current now immediately
            awaitComplete()                          // only New cards → nothing to wait for, idles
        }
    }

    @Test
    fun reEmits_whenAFutureReviewCardBecomesDue() = runTest {
        val clock = { base + testScheduler.currentTime }
        flowOf(listOf(reviewCard("r1", due = base + 60_000L))).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)            // before due
            assertEquals(base + 60_000L, awaitItem().second)  // re-emits exactly at the due moment
            awaitComplete()                                   // now already due → no further ticks
        }
    }

    @Test
    fun firesAtSoonestDue_thenTheNext() = runTest {
        val clock = { base + testScheduler.currentTime }
        val cards = listOf(reviewCard("r1", base + 30_000L), reviewCard("r2", base + 60_000L))
        flowOf(cards).withDueTicks(clock).test {
            assertEquals(base, awaitItem().second)
            assertEquals(base + 30_000L, awaitItem().second)  // soonest first
            assertEquals(base + 60_000L, awaitItem().second)  // then the next
            awaitComplete()
        }
    }

    @Test
    fun reEmitsImmediately_whenCardListChanges() = runTest {
        val clock = { base + testScheduler.currentTime }
        val source = MutableStateFlow(listOf(newCard("n1")))
        source.withDueTicks(clock).test {
            assertEquals(1, awaitItem().first.size)   // initial list
            source.value = listOf(newCard("n1"), newCard("n2"))
            assertEquals(2, awaitItem().first.size)   // flatMapLatest restarts with the new list
            cancelAndIgnoreRemainingEvents()          // StateFlow stays open; stop collecting
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.fsrs.DueTickerTest"`
Expected: FAIL — compilation error `unresolved reference: withDueTicks`.

- [ ] **Step 3: Implement the operator**

Create `app/src/main/java/nart/simpleanki/core/domain/fsrs/DueTicker.kt`:

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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.fsrs.DueTickerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/domain/fsrs/DueTicker.kt app/src/test/java/nart/simpleanki/core/domain/fsrs/DueTickerTest.kt
git commit -m "Add withDueTicks operator: re-emit time at each card's due moment"
```

---

### Task 2: Wire `StudyQueueViewModel` to the ticker

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/queue/StudyQueueViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt`

TDD: add a live-update test first, then wire the operator.

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt`, add this test method inside the class (place it after the existing `aggregatesAcrossDecks_andBreaksDownPerDeck` test). It reuses the file's existing `private val now = 1_700_000_000_000L` field and `review(...)` helper:

```kotlin
    @Test
    fun dueCount_updatesLive_whenAFutureCardBecomesDue() = runTest {
        // The @Before sets Main to an UnconfinedTestDispatcher with its OWN scheduler; override it
        // here so the VM's coroutines (incl. the ticker's delay) share THIS test's scheduler and
        // are driven by advanceTimeBy below.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // Clock tied to virtual time: advancing the scheduler advances now().
        val clock = { now + testScheduler.currentTime }
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        // A review card due 60s from now — not ready yet.
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 60_000L))

        val vm = StudyQueueViewModel(
            cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }),
            FakeSettingsRepository(), FakeEntitlementRepository(), now = clock,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("not due yet", 0, vm.uiState.value.dueCount)
        assertFalse("no work yet", vm.uiState.value.hasWork)

        advanceTimeBy(61_000L)   // cross the due moment
        runCurrent()
        assertEquals("became due", 1, vm.uiState.value.dueCount)
        assertEquals(1, vm.uiState.value.readyCount)
        assertTrue(vm.uiState.value.hasWork)
    }
```

Add this import to the test file's import block (the others — `assertFalse`, `assertTrue`, `runCurrent`, `runTest`, `launch` — are already imported):

```kotlin
import kotlinx.coroutines.test.advanceTimeBy
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: FAIL on `dueCount_updatesLive_whenAFutureCardBecomesDue` — after `advanceTimeBy`, `dueCount` is still `0` (the `combine` never re-sampled `now()`). The assertion `became due` fails with `expected:<1> but was:<0>`.

- [ ] **Step 3: Wire the operator into the view model**

In `app/src/main/java/nart/simpleanki/feature/queue/StudyQueueViewModel.kt`:

3a. Add the import (alongside the existing `import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder`):

```kotlin
import nart.simpleanki.core.domain.fsrs.withDueTicks
```

3b. Change the first `combine` argument from `cardRepository.observeAllCards()` to `cardRepository.observeAllCards().withDueTicks(now)`, destructure the first lambda parameter, and delete the old one-shot `now()` read. The block currently begins:

```kotlin
        combine(
            cardRepository.observeAllCards(),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { cards, decks, folders, settings, entitlement ->
            val nowMillis = now()
            val queue = StudyQueueBuilder.buildStudyQueue(
```

Replace those lines with:

```kotlin
        combine(
            cardRepository.observeAllCards().withDueTicks(now),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { (cards, nowMillis), decks, folders, settings, entitlement ->
            val queue = StudyQueueBuilder.buildStudyQueue(
```

(Everything below `val queue = ...` already references `nowMillis` and is unchanged. The `cards` name is still bound — now via destructuring.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: PASS (all tests in the class, including the new one and the pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/queue/StudyQueueViewModel.kt app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt
git commit -m "Refresh Today queue live as cards become due"
```

---

### Task 3: Wire `DeckDetailViewModel` to the ticker

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/LibraryAndDeckDetailViewModelTest.kt`

TDD: add a live-update test first, then wire the operator.

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/nart/simpleanki/feature/LibraryAndDeckDetailViewModelTest.kt`, add this test method inside the class (place it after the existing `deckDetail_searchFiltersCards` test):

```kotlin
    @Test
    fun deckDetail_dueCountUpdatesLive_whenACardBecomesDue() = runTest {
        // Override the @Before Main dispatcher so the VM shares THIS test's scheduler (so the
        // ticker's delay is advanced by advanceTimeBy below).
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val base = 1_700_000_000_000L
        val clock = { base + testScheduler.currentTime }
        val cardRepo = CardRepository(FakeCardDao(), now = { base })
        // A review card due 60s from now — not due yet.
        cardRepo.upsert(
            Card(
                id = "c1", front = "Q", back = "A", deckId = "d1",
                dateCreated = base, lastModified = base,
                fsrsDue = base + 60_000L, fsrsState = CardState.Review.value,
            ),
        )

        val vm = DeckDetailViewModel("d1", cardRepo, now = clock)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("not due yet", 0, vm.uiState.value.dueCount)

        advanceTimeBy(61_000L)   // cross the due moment
        runCurrent()
        assertEquals("became due", 1, vm.uiState.value.dueCount)
    }
```

Add these imports to the test file's import block (`Card`, `CardState`, `CardRepository`, `FakeCardDao`, `runCurrent`, `runTest`, `launch`, `assertEquals` are already imported):

```kotlin
import kotlinx.coroutines.test.advanceTimeBy
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.LibraryAndDeckDetailViewModelTest"`
Expected: FAIL on `deckDetail_dueCountUpdatesLive_whenACardBecomesDue` — after `advanceTimeBy`, `dueCount` is still `0`. Assertion `became due` fails with `expected:<1> but was:<0>`.

- [ ] **Step 3: Wire the operator into the view model**

In `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailViewModel.kt`:

3a. Add the import (after `import nart.simpleanki.core.domain.model.CardState`):

```kotlin
import nart.simpleanki.core.domain.fsrs.withDueTicks
```

3b. Change the first `combine` argument, destructure the first lambda parameter, and delete the old one-shot `now()` read. The block currently reads:

```kotlin
        combine(
            cardRepository.observeCards(deckId),
            queryFlow,
            deckNameFlow,
        ) { cards, query, name ->
            val nowMillis = now()
            DeckDetailUiState(
```

Replace those lines with:

```kotlin
        combine(
            cardRepository.observeCards(deckId).withDueTicks(now),
            queryFlow,
            deckNameFlow,
        ) { (cards, nowMillis), query, name ->
            DeckDetailUiState(
```

(The `DeckDetailUiState(...)` body already uses `cards` and `nowMillis` and is unchanged.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.LibraryAndDeckDetailViewModelTest"`
Expected: PASS (all tests in the class, including the new one).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailViewModel.kt app/src/test/java/nart/simpleanki/feature/LibraryAndDeckDetailViewModelTest.kt
git commit -m "Refresh deck detail due count live as cards become due"
```

---

## Final verification

- [ ] **Run the full app unit-test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available)**

- Open the Today/Queue home with a deck whose next review is a minute or two out and nothing else ready ("All caught up"-ish): without touching anything, when that card's due time arrives the due count / "ready" rises and the deck chip appears — no navigation needed.
- Open that deck's detail screen with the next card due shortly: the Due stat ticks up and the "You're all caught up!" slot flips to the Study button at the due moment.
- Background the app past the due moment and return: counts are already correct on resume (re-subscription emits current time immediately).
