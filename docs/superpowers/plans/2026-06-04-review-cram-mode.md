# Review / Cram Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only "Review" (cram) mode — a horizontal carousel of flip cards for a deck or folder, with no rating and no FSRS scheduling — mirroring iOS.

**Architecture:** A new self-contained `feature/review/` (ViewModel + Screen) reusing the existing `FlipCard` composable in a Compose `HorizontalPager`. The card pool is built by the existing `StudyQueueBuilder.buildReviewQueue` (extended to exclude `memorized`). Entry points: an always-present "Review" action on deck-detail, and a folder-level action on folder-detail. Two new nav routes. No scheduling, no persistence, no effect on the study queue.

**Tech Stack:** Kotlin, Jetpack Compose (`androidx.compose.foundation.pager`), Koin, JUnit4 + coroutines-test.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Spec:** `docs/superpowers/specs/2026-06-04-review-cram-mode-design.md`.

**Note:** new tests are JVM unit tests (run normally). The emulator is unavailable, so Compose screens are compile-verified only.

---

### Task 1: Exclude memorized cards from `buildReviewQueue`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/domain/fsrs/StudyQueueBuilder.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/fsrs/StudyQueueBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

In `StudyQueueBuilderTest.kt`, add this test after the existing `reviewQueue_filtersByDirection` test (the file already imports `Card`, `CardState`, `ReviewCardFilter`, `assertEquals`):

```kotlin
    @Test
    fun reviewQueue_excludesMemorizedAndDeleted() {
        val cards = listOf(
            card("keep"),
            card("mem").copy(memorized = true),
            card("gone", deleted = true),
        )
        assertEquals(
            listOf("keep"),
            StudyQueueBuilder.buildReviewQueue(cards, ReviewCardFilter.All).map { it.id },
        )
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.fsrs.StudyQueueBuilderTest"`
Expected: FAIL on `reviewQueue_excludesMemorizedAndDeleted` — `mem` is still present (`expected:<[keep]> but was:<[keep, mem]>`), because the current filter does not exclude memorized cards.

- [ ] **Step 3: Add the `memorized` exclusion**

In `StudyQueueBuilder.kt`, in `buildReviewQueue`, change the first filter line. It currently reads:

```kotlin
        val filtered = cards.filter { !it.isDeleted }.filter { card ->
```

Change it to:

```kotlin
        val filtered = cards.filter { !it.isDeleted && !it.memorized }.filter { card ->
```

(Nothing else in the function changes.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.fsrs.StudyQueueBuilderTest"`
Expected: PASS (all tests in the class).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/domain/fsrs/StudyQueueBuilder.kt app/src/test/java/nart/simpleanki/core/domain/fsrs/StudyQueueBuilderTest.kt
git commit -m "Exclude memorized cards from review queue"
```

---

### Task 2: `ReviewViewModel`

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/review/ReviewViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/review/ReviewViewModelTest.kt`

TDD: write the test first (won't compile until the VM exists), then implement.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/feature/review/ReviewViewModelTest.kt`:

```kotlin
package nart.simpleanki.feature.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.ReviewCardFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(
        id: String,
        deckId: String,
        reverse: Boolean = false,
        memorized: Boolean = false,
        deleted: Boolean = false,
    ) = Card(
        id = id, front = "f", back = "b", deckId = deckId,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.Review.value,
        isReverse = reverse, memorized = memorized, isDeleted = deleted,
    )

    @Test
    fun deckReview_appliesDeckFilter_andExcludesMemorizedAndDeleted() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(
            Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now, reviewFilter = ReviewCardFilter.OriginalsOnly),
        )
        cardRepo.upsert(card("orig", "A", reverse = false))
        cardRepo.upsert(card("rev", "A", reverse = true))                  // excluded: OriginalsOnly
        cardRepo.upsert(card("mem", "A", memorized = true))                // excluded: memorized
        cardRepo.upsert(card("gone", "A", deleted = true))                 // excluded: deleted

        val vm = ReviewViewModel("A", null, cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(listOf("orig"), s.cards.map { it.id })
    }

    @Test
    fun folderReview_aggregatesAcrossFoldersDecks_bothDirections() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", folderId = "F", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "B", name = "B", folderId = "F", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "C", name = "C", folderId = null, dateCreated = now, lastModified = now))
        cardRepo.upsert(card("a1", "A"))
        cardRepo.upsert(card("b1", "B", reverse = true))
        cardRepo.upsert(card("c1", "C"))                                   // excluded: not in folder F

        val vm = ReviewViewModel(null, "F", cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        // Folder review uses ReviewCardFilter.All (both directions), across F's decks only.
        // Order is shuffled, so compare as a set.
        assertEquals(setOf("a1", "b1"), s.cards.map { it.id }.toSet())
    }

    @Test
    fun emptyDeck_yieldsEmptyPool() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))

        val vm = ReviewViewModel("A", null, cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertFalse(vm.uiState.value.loading)
        assertEquals(emptyList<String>(), vm.uiState.value.cards.map { it.id })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.review.ReviewViewModelTest"`
Expected: FAIL — compilation error `unresolved reference: ReviewViewModel`.

- [ ] **Step 3: Implement `ReviewViewModel`**

Create `app/src/main/java/nart/simpleanki/feature/review/ReviewViewModel.kt`:

```kotlin
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
            override val eventName = "review_session_start"
            override val params get() = buildMap<String, Any?> {
                deckId?.let { put("deck_id", it) }
                folderId?.let { put("folder_id", it) }
                put("count", count)
            }
        }
    }
}
```

Note: this mirrors `StudyViewModel`'s `Event`/`LoggableEvent` pattern. If `LoggableEvent.params` has a more specific type than `Map<String, Any?>`, match it (look at `StudyViewModel.kt`); the `buildMap<String, Any?>` type argument may need adjusting to compile.

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.review.ReviewViewModelTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/review/ReviewViewModel.kt app/src/test/java/nart/simpleanki/feature/review/ReviewViewModelTest.kt
git commit -m "Add ReviewViewModel: build read-only review pool for deck/folder"
```

---

### Task 3: `ReviewScreen` (carousel UI)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/review/ReviewScreen.kt`

Build-verified + previews (no Compose UI unit tests, per codebase convention).

- [ ] **Step 1: Create the screen**

Create `app/src/main/java/nart/simpleanki/feature/review/ReviewScreen.kt`:

```kotlin
package nart.simpleanki.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.FlipCard
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReviewScreen(
    deckId: String?,
    onDone: () -> Unit,
    folderId: String? = null,
    viewModel: ReviewViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId, folderId = folderId)) },
) {
    val state by viewModel.uiState.collectAsState()
    ReviewContent(state = state, onDone = onDone)
}

/** Stateless review carousel, decoupled from the ViewModel for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(state: ReviewUiState, onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { state.cards.size })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.cards.isNotEmpty()) {
                        Text("${pagerState.currentPage + 1} of ${state.cards.size}")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Quit") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.cards.isEmpty() -> EmptyReview(onDone)
                else -> {
                    // Flip resets when the page changes (mirrors iOS clearing flips on scroll).
                    var revealed by remember(pagerState.currentPage) { mutableStateOf(false) }
                    var showHint by remember { mutableStateOf(true) }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        FlipCard(
                            card = state.cards[page],
                            revealed = page == pagerState.currentPage && revealed,
                            onFlip = { revealed = true; showHint = false },
                            modifier = Modifier.fillMaxSize().padding(20.dp),
                        )
                    }
                    if (showHint) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tap to flip",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReview(onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "No cards to review here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone) { Text("Close") }
    }
}

private fun previewCard(id: String, front: String, back: String) = Card(
    id = id, front = front, back = back, deckId = "d",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.Review.value,
)

@Preview(name = "Review · populated", showBackground = true)
@Composable
private fun ReviewPopulatedPreview() {
    AzriTheme {
        ReviewContent(
            state = ReviewUiState(
                loading = false,
                cards = listOf(
                    previewCard("1", "hola", "hello"),
                    previewCard("2", "adiós", "goodbye"),
                ),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Review · empty", showBackground = true)
@Composable
private fun ReviewEmptyPreview() {
    AzriTheme {
        ReviewContent(state = ReviewUiState(loading = false, cards = emptyList()), onDone = {})
    }
}
```

Note: `HorizontalPager`/`rememberPagerState(pageCount = { … })` are in `androidx.compose.foundation.pager` (stable). If the project's Compose version exposes a different `rememberPagerState` signature, adapt minimally (e.g. `rememberPagerState(initialPage = 0, pageCount = { state.cards.size })`) and report the change.

- [ ] **Step 2: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/review/ReviewScreen.kt
git commit -m "Add ReviewScreen: horizontal flip-card carousel"
```

---

### Task 4: Wire entry points (DI, nav, deck + folder detail)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/folderdetail/FolderDetailScreen.kt`

Build-verified.

- [ ] **Step 1: Register `ReviewViewModel` in Koin**

In `app/src/main/java/nart/simpleanki/di/AppModule.kt`:

1a. Add the import alongside the other feature-VM imports (e.g. near `import nart.simpleanki.feature.study.StudyViewModel`):

```kotlin
import nart.simpleanki.feature.review.ReviewViewModel
```

1b. Immediately after the existing `StudyViewModel` `viewModel { params -> … }` block (the one that does `val args = params.get<StudyArgs>()`), add a parallel block (it reuses the same `StudyArgs`):

```kotlin
    viewModel { params ->
        val args = params.get<StudyArgs>()
        ReviewViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            logManager = get(),
        )
    }
```

- [ ] **Step 2: Add nav routes and pass `onReview` callbacks**

In `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`:

2a. Add the import (near `import nart.simpleanki.feature.study.StudyScreen`):

```kotlin
import nart.simpleanki.feature.review.ReviewScreen
```

2b. In the `composable("deck/{deckId}") { … }` block, the `DeckDetailScreen(…)` call currently passes `onStudy = { nav.navigate("study/$deckId") }`. Add an `onReview` argument to that call:

```kotlin
                    onReview = { nav.navigate("review/$deckId") },
```

2c. In the `composable("folder/{folderId}") { … }` block, the `FolderDetailScreen(…)` call currently passes `onNewDeck`/`onEditFolder`. Add:

```kotlin
                    onReview = { nav.navigate("reviewFolder/$folderId") },
```

2d. Add two new route composables next to the existing `study/...` routes (e.g. right after the `studyFolder/{folderId}` composable):

```kotlin
            composable("review/{deckId}") { entry ->
                ReviewScreen(
                    deckId = entry.arguments?.getString("deckId").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }
            composable("reviewFolder/{folderId}") { entry ->
                ReviewScreen(
                    deckId = null,
                    folderId = entry.arguments?.getString("folderId").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }
```

- [ ] **Step 3: Add the deck-detail "Review" action**

In `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt`:

3a. Add two imports (alongside the existing ones):

```kotlin
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.OutlinedButton
```

3b. Add `onReview` to the outer `DeckDetailScreen` parameter list (after `onStudy: () -> Unit,`):

```kotlin
    onReview: () -> Unit,
```

and pass it down in the `DeckDetailContent(…)` call inside `DeckDetailScreen` (after `onStudy = onStudy,`):

```kotlin
        onReview = onReview,
```

3c. Add `onReview` to the `DeckDetailContent` parameter list **with a default** (so previews and the instrumented test keep compiling). After `onStudy: () -> Unit,` add:

```kotlin
    onReview: () -> Unit = {},
```

3d. In `DeckDetailContent`, the header `Column` (the one with `verticalArrangement = Arrangement.spacedBy(12.dp)`) contains the `when { studyable > 0 -> Button(...); state.total > 0 -> AllCaughtUp(...); else -> Unit }` block. Immediately **after** that `when { … }` block (and still inside the same `Column`), add an always-present Review action shown whenever the deck has cards:

```kotlin
                if (state.total > 0) {
                    OutlinedButton(
                        onClick = onReview,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Filled.Style, contentDescription = null)
                        Text(
                            "Review",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
```

(The `spacedBy(12.dp)` arrangement spaces it from the Study/AllCaughtUp block automatically — no Spacer needed.)

- [ ] **Step 4: Add the folder-detail "Review" action**

In `app/src/main/java/nart/simpleanki/feature/folderdetail/FolderDetailScreen.kt`:

4a. Add the import:

```kotlin
import androidx.compose.material.icons.filled.Style
```

4b. Add `onReview` to the outer `FolderDetailScreen` parameter list (after `onOpenDeck: (String) -> Unit,`):

```kotlin
    onReview: () -> Unit,
```

and pass it into the `FolderDetailContent(…)` call (after `onOpenDeck = onOpenDeck,`):

```kotlin
        onReview = onReview,
```

4c. Add `onReview` to `FolderDetailContent`'s parameter list **with a default** (so previews keep compiling). After `onOpenDeck: (String) -> Unit,` add:

```kotlin
    onReview: () -> Unit = {},
```

4d. In `FolderDetailContent`'s `TopAppBar` `actions = { … }` block, add a Review action as the first item (before the Edit/New-deck icons):

```kotlin
                    IconButton(onClick = onReview) { Icon(Icons.Filled.Style, "Review folder") }
```

- [ ] **Step 5: Verify everything compiles (app + tests)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (The `onReview` defaults on the two `*Content` composables keep existing previews and the instrumented `DeckDetailContentTest` compiling.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt app/src/main/java/nart/simpleanki/feature/folderdetail/FolderDetailScreen.kt
git commit -m "Wire Review mode entry points: deck detail, folder detail, nav, DI"
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

- Open a deck with cards → a "Review" button shows under Study (and under "You're all caught up!" when nothing is due). Tap it → a horizontal carousel; swipe between cards, tap to flip (front returns when you swipe to the next card), "{i} of {n}" updates, Quit returns. No rating buttons appear; the study queue/due counts are unchanged afterward.
- A deck whose `reviewFilter` is Originals/Reverses only shows the matching direction; a deck with `shuffled` on shows a shuffled order.
- Open a folder → the top-bar Review action starts a review across all its decks' cards (shuffled). An empty deck/folder → "No cards to review here." + Close.
