# Gamified Type Practice Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the in-session Type Practice screen into a gamified "split-zones" layout — top progress bar + live combo chip, prompt hero card, bottom-anchored answer zone, a mint success-flash on correct answers, and per-card motion — reusing the app's rating-color palette and staying FSRS-decoupled.

**Architecture:** A shared `RatingColors` palette (DRY). The pure `TypePracticeSession` exposes its live `combo`. `TypePracticeViewModel` adds `combo`/`total`/`celebrating` to `UiState` and, on a correct answer, holds a ~400ms "celebrating" phase (mint flash) before auto-advancing. `TypePracticeScreen` is restructured into the gamified layout (top progress + combo chip, prompt hero card, bottom answer zone, mint celebrate, pink diff, per-card slide-in).

**Tech Stack:** Kotlin, Jetpack Compose (Material3, animation), coroutines, JUnit4 + coroutines-test.

**Branch:** `feature/type-practice-mode`.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Commit rule:** No "claude" mention in commit messages; no Co-Authored-By / attribution trailer. NEVER `git add -A` / `git add .` — add explicit paths only (the untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` and the gitignored `.superpowers/` must never be staged).

---

## File Structure
- `app/src/main/java/nart/simpleanki/ui/theme/RatingColors.kt` (create) — shared rating palette.
- `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt` (modify) — use `RatingColors`.
- `app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt` (modify) — expose `currentCombo`.
- `app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt` (modify) — combo test.
- `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt` (modify) — combo/total/celebrating phase.
- `app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt` (modify) — virtual-time + celebrate tests.
- `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt` (modify) — full gamified restructure.

---

## Task 1: Shared `RatingColors` palette

**Files:**
- Create: `app/src/main/java/nart/simpleanki/ui/theme/RatingColors.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`

No unit test (a pure color-constants object; comparing `androidx.compose.ui.graphics.Color` in a plain JVM test is brittle). Verified by compile + the existing `StudyViewModelTest` still passing.

- [ ] **Step 1: Create `RatingColors.kt`**
```kotlin
package nart.simpleanki.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS-derived spaced-repetition rating colors, shared across study modes so "a correct typed answer"
 * and an "Easy" review read as the same outcome. Single source of truth (previously inline literals
 * in StudyScreen).
 */
object RatingColors {
    val Again = Color(0xFFFF2D55)   // wrong / incorrect
    val Hard = Color(0xFFFF9500)
    val Good = Color(0xFF5856D6)
    val Easy = Color(0xFF00C7BE)    // correct / success
}
```

- [ ] **Step 2: Use it in `StudyScreen.kt`**

Add the import (next to the other `nart.simpleanki...` imports):
```kotlin
import nart.simpleanki.ui.theme.RatingColors
```
Replace the four inline color literals in the rating-button `Row` (currently `Color(0xFFFF2D55)`, `Color(0xFFFF9500)`, `Color(0xFF5856D6)`, `Color(0xFF00C7BE)`) with the shared values:
```kotlin
                        RatingButton("Again", state.ratingIntervals[Rating.Again], RatingColors.Again, Modifier.weight(1f)) { onRate(Rating.Again) }
                        RatingButton("Hard", state.ratingIntervals[Rating.Hard], RatingColors.Hard, Modifier.weight(1f)) { onRate(Rating.Hard) }
                        RatingButton("Good", state.ratingIntervals[Rating.Good], RatingColors.Good, Modifier.weight(1f)) { onRate(Rating.Good) }
                        RatingButton("Easy", state.ratingIntervals[Rating.Easy], RatingColors.Easy, Modifier.weight(1f)) { onRate(Rating.Easy) }
```
If `androidx.compose.ui.graphics.Color` is now unused in `StudyScreen.kt`, remove its import; otherwise leave it.

- [ ] **Step 3: Verify compile + study tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: BUILD SUCCESSFUL; StudyViewModelTest passes.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/nart/simpleanki/ui/theme/RatingColors.kt \
        app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt
git commit -m "Extract shared RatingColors palette"
```

---

## Task 2: Expose the live combo from `TypePracticeSession`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt`

- [ ] **Step 1: Write the failing test**

Append inside `class TypePracticeSessionTest` (the `card(id, back)` helper already sets `front = "f-$id"`):
```kotlin
    @Test fun currentCombo_incrementsOnCorrect_resetsOnWrong() {
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b"), card("c3", "c")))
        assertEquals(0, s.currentCombo)
        s.submit("a"); assertEquals(1, s.currentCombo)
        s.submit("b"); assertEquals(2, s.currentCombo)
        s.submit("nope"); assertEquals(0, s.currentCombo)   // wrong first-try resets the combo
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypePracticeSessionTest"`
Expected: COMPILE FAILURE (`currentCombo` does not exist).

- [ ] **Step 3: Expose `currentCombo`**

In `TypePracticeSession.kt`, add this getter next to the other public `val`s (e.g. after `canOverride`):
```kotlin
    /** The live combo (consecutive first-try corrects; resets to 0 on any wrong submit). */
    val currentCombo: Int get() = combo
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypePracticeSessionTest"`
Expected: PASS (all existing + the new test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt \
        app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt
git commit -m "Expose live combo from TypePracticeSession"
```

---

## Task 3: ViewModel — combo, total, and the celebrating phase

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt`

### Step 1: Write the failing tests (replace the whole test file)

The celebrate phase uses `delay`, so the test class moves to a `StandardTestDispatcher` and drives virtual time (`advanceUntilIdle`). Replace the ENTIRE contents of `TypePracticeViewModelTest.kt` with:
```kotlin
package nart.simpleanki.feature.typepractice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeTypingLogDao
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.typing.TypeDirection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypePracticeViewModelTest {
    private val now = 1_700_000_000_000L
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String, back: String, front: String = "f-$id") = Card(
        id = id, front = front, back = back, deckId = "A",
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    private fun model(vararg cards: Card): Pair<TypePracticeViewModel, TypingLogRepository> {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        // seed
        kotlinx.coroutines.runBlocking {
            deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
            cards.forEach { cardRepo.upsert(it) }
        }
        return TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now }) to logRepo
    }

    @Test
    fun correctAnswer_celebrates_thenAdvances_andAppendsOneLog() = runTest(dispatcher.scheduler) {
        val (vm, logRepo) = model(card("c1", "answer"), card("c2", "two"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.awaitingDirection)

        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()
        assertEquals("c1", vm.uiState.value.current!!.id)
        assertEquals(2, vm.uiState.value.total)

        vm.onInput("answer")
        vm.onSubmit()
        // celebrating is set synchronously, BEFORE the delayed advance runs
        assertTrue(vm.uiState.value.celebrating)
        assertEquals("c1", vm.uiState.value.current!!.id)   // still showing the just-answered card
        assertEquals(1, vm.uiState.value.combo)

        advanceUntilIdle()                                  // runs the ~400ms delay
        assertFalse(vm.uiState.value.celebrating)
        assertEquals("c2", vm.uiState.value.current!!.id)   // advanced

        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertTrue(logs.single().correct)
    }

    @Test
    fun progress_tracksClearedOverTotal() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"), card("c2", "b"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.total)
        assertEquals(2, vm.uiState.value.remaining)         // 0 cleared

        vm.onInput("a"); vm.onSubmit(); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.total)
        assertEquals(1, vm.uiState.value.remaining)         // 1 cleared → progress 1/2
    }

    @Test
    fun wrongAnswer_resetsComboChip_andReveals() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "answer"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("nope"); vm.onSubmit()
        assertTrue(vm.uiState.value.revealing)
        assertEquals(0, vm.uiState.value.combo)
        assertEquals("answer", vm.uiState.value.revealedAnswer)
        assertFalse(vm.uiState.value.celebrating)
    }

    @Test
    fun inputIgnoredWhileCelebrating() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"), card("c2", "b"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("a"); vm.onSubmit()
        assertTrue(vm.uiState.value.celebrating)
        vm.onInput("ignored")                               // ignored during the flash
        assertEquals("a", vm.uiState.value.input)
        vm.onSubmit()                                       // no-op during the flash
        assertEquals("c1", vm.uiState.value.current!!.id)
    }

    @Test
    fun lastCardCorrect_finishesAfterCelebrate() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("a"); vm.onSubmit()
        assertTrue(vm.uiState.value.celebrating)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertEquals(1, vm.uiState.value.report!!.completed)
    }

    @Test
    fun typeFront_typesTheFront() = runTest(dispatcher.scheduler) {
        val (vm, logRepo) = model(card("c1", back = "definition"))   // front is "f-c1"
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeFront)
        advanceUntilIdle()
        vm.onInput("f-c1"); vm.onSubmit(); advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertTrue(logRepo.observeLogs().first().single().correct)
    }
}
```

### Step 2: Run to verify failure
Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.typepractice.TypePracticeViewModelTest"`
Expected: COMPILE FAILURE / assertion failures (`celebrating`, `combo`, `total` don't exist; no celebrate phase).

### Step 3: Add the fields to `TypePracticeUiState`
In `TypePracticeViewModel.kt`, add three fields to `TypePracticeUiState` (after `cardTick`):
```kotlin
    /** Live combo for the chip (consecutive first-try correct; 0 resets on a miss). */
    val combo: Int = 0,
    /** Session pool size, for the progress bar (progress = (total - remaining)/total). */
    val total: Int = 0,
    /** True during the brief mint success flash before auto-advancing. */
    val celebrating: Boolean = false,
```

### Step 4: Add the celebrate constant + pool-size field
At the top of `TypePracticeViewModel.kt` (below the imports, above `data class TypePracticeUiState`), add:
```kotlin
/** How long the mint success flash holds before auto-advancing. */
private const val CELEBRATE_MS = 400L
```
In the `TypePracticeViewModel` class body, add a field next to `baseCards`:
```kotlin
    private var poolTotal = 0
```

### Step 5: Set `poolTotal` in `startSession`
In `startSession`, right after `pool` is built (before/after `previouslyMastered`), set it:
```kotlin
        poolTotal = pool.size
```

### Step 6: Rewrite `onSubmit` for the celebrate phase
Replace the entire `onSubmit` function with:
```kotlin
    fun onSubmit() {
        if (!::session.isInitialized || _uiState.value.celebrating) return
        val typed = _uiState.value.input
        val answered = session.current                      // capture BEFORE submit advances the queue
        when (val r = session.submit(typed)) {
            SubmitResult.Correct -> {
                logManager.track(Event.Answered(true))
                _uiState.value = _uiState.value.copy(
                    celebrating = true,
                    current = answered,                     // keep showing the just-answered card
                    input = typed,                          // shown in mint, disabled
                    combo = session.currentCombo,           // popped +1
                    revealing = false,
                )
                viewModelScope.launch {
                    kotlinx.coroutines.delay(CELEBRATE_MS)
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
```

### Step 7: Guard `onInput` / `onDontKnow` against the flash + reset the chip on Don't-know
Replace `onInput` and `onDontKnow` with:
```kotlin
    fun onInput(text: String) {
        if (_uiState.value.celebrating) return
        _uiState.value = _uiState.value.copy(input = text)
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
```

### Step 8: Surface combo/total/celebrating in `renderAdvance`
In `renderAdvance`, add three fields to the `prev.copy(...)` (alongside the existing ones):
```kotlin
            combo = session.currentCombo,
            total = poolTotal,
            celebrating = false,
```

### Step 9: Run tests to verify they pass
Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "nart.simpleanki.feature.typepractice.TypePracticeViewModelTest"`
Expected: BUILD SUCCESSFUL; all 6 tests pass.

### Step 10: Commit
```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt \
        app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt
git commit -m "Add combo, progress total, and the celebrating success phase to Type Practice VM"
```

---

## Task 4: Gamified screen restructure (full rewrite)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

No unit test (Compose UI; logic covered by Tasks 2–3). Verified by compile + previews.

- [ ] **Step 1: Replace the ENTIRE file** `TypePracticeScreen.kt` with:
```kotlin
package nart.simpleanki.feature.typepractice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.typing.AnswerDiff
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.TypeDirection
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.MediaImage
import nart.simpleanki.ui.theme.AzriTheme
import nart.simpleanki.ui.theme.RatingColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TypePracticeScreen(
    deckId: String,
    onDone: () -> Unit,
    viewModel: TypePracticeViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId)) },
) {
    val state by viewModel.uiState.collectAsState()
    TypePracticeContent(
        state = state,
        onChooseDirection = viewModel::chooseDirection,
        onInput = viewModel::onInput,
        onSubmit = viewModel::onSubmit,
        onDontKnow = viewModel::onDontKnow,
        onContinue = viewModel::onContinue,
        onOverride = viewModel::onOverride,
        onRestart = viewModel::restart,
        onDone = onDone,
    )
}

/** Stateless gamified Type-Practice UI, decoupled from the ViewModel for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePracticeContent(
    state: TypePracticeUiState,
    onChooseDirection: (TypeDirection) -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    val inSession = !state.loading && !state.awaitingDirection && !state.finished
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                title = {
                    if (inSession) {
                        val target = if (state.total > 0) (state.total - state.remaining).toFloat() / state.total else 0f
                        val progress by animateFloatAsState(targetValue = target, animationSpec = tween(300), label = "progress")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            color = if (state.celebrating) RatingColors.Easy else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        Text(if (state.finished) "Done" else "Type Practice")
                    }
                },
                actions = { if (inSession) ComboChip(state.combo) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.awaitingDirection -> DirectionChooser(onChooseDirection)
                state.finished -> SessionReportView(state.report, onRestart, onDone)
                else -> PracticeCard(state, onInput, onSubmit, onDontKnow, onContinue, onOverride)
            }
        }
    }
}

private val ComboAmber = Color(0xFFFF9500)

@Composable
private fun ComboChip(combo: Int) {
    val active = combo >= 1
    val pop = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 1) { pop.snapTo(1.25f); pop.animateTo(1f, tween(180)) }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) ComboAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(end = 8.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = "Combo",
                tint = if (active) ComboAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$combo",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) ComboAmber else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectionChooser(onChoose: (TypeDirection) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("What do you want to type?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the side you'll produce from memory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        DirectionOption("Type the back", "See the front → type the back (the answer)") { onChoose(TypeDirection.TypeBack) }
        Spacer(Modifier.height(12.dp))
        DirectionOption("Type the front", "See the back → type the front") { onChoose(TypeDirection.TypeFront) }
    }
}

@Composable
private fun DirectionOption(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PracticeCard(
    state: TypePracticeUiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
) {
    val card = state.current ?: return
    val typeFront = state.direction == TypeDirection.TypeFront
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.cardTick) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.revealing, state.celebrating) {
        if (state.revealing || state.celebrating) keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Upper zone: prompt hero card — scrolls if long, slides per card.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state.cardTick,
                transitionSpec = {
                    (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                },
                label = "card",
            ) { _ ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PromptCard(card, typeFront, celebrating = state.celebrating)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Lower zone (thumb-rail): answer / celebrate / reveal.
        when {
            state.celebrating -> CorrectInput(state.input)
            state.revealing -> RevealPanel(state, onContinue, onOverride)
            else -> AnswerInput(state, focus, onInput, onSubmit, onDontKnow)
        }
    }
}

@Composable
private fun PromptCard(card: Card, typeFront: Boolean, celebrating: Boolean) {
    val mint = RatingColors.Easy
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (celebrating) mint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (celebrating) BorderStroke(1.5.dp, mint) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (celebrating) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "Correct", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
            } else {
                DirectionPill(typeFront)
                Spacer(Modifier.height(14.dp))
            }
            if (!typeFront) {
                card.image?.let { name ->
                    MediaImage(name, card.imagePath, Modifier.fillMaxWidth().height(160.dp))
                    Spacer(Modifier.height(16.dp))
                }
            }
            Text(
                if (typeFront) card.back else card.front,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            card.audioName?.let { name ->
                Spacer(Modifier.height(16.dp))
                AudioPlayButton(name, card.audioPath)
            }
        }
    }
}

@Composable
private fun DirectionPill(typeFront: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(
            if (typeFront) "TYPE THE FRONT" else "TYPE THE BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AnswerInput(
    state: TypePracticeUiState,
    focus: FocusRequester,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            label = { Text("Type the answer") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            enabled = state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Check") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDontKnow, modifier = Modifier.fillMaxWidth()) { Text("Don't know") }
    }
}

@Composable
private fun CorrectInput(typed: String) {
    val mint = RatingColors.Easy
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, mint),
        color = mint.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            typed,
            color = mint,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun RevealPanel(state: TypePracticeUiState, onContinue: () -> Unit, onOverride: () -> Unit) {
    val diff = remember(state.revealedAnswer, state.lastTyped) {
        AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)
    }
    val matchColor = RatingColors.Easy        // got it right = mint
    val missColor = RatingColors.Again        // wrong/missing = pink
    val typedMatchColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                diff.expected.forEach { seg ->
                    when (seg.kind) {
                        AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = matchColor)) { append(seg.text) }
                        AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (state.lastTyped.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("You typed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    diff.typed.forEach { seg ->
                        when (seg.kind) {
                            AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = typedMatchColor)) { append(seg.text) }
                            AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Continue") }
        if (state.canOverride) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) { Text("I was right") }
        }
    }
}

@Composable
private fun SessionReportView(report: SessionReport?, onRestart: () -> Unit, onDone: () -> Unit) {
    val r = report ?: SessionReport(0, 0, 0, 0, 0)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Practice complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        ReportRow("Cards", r.completed.toString())
        ReportRow("First-try accuracy", "${r.firstTryAccuracy}%")
        ReportRow("Best combo", r.bestCombo.toString())
        ReportRow("Newly mastered", r.newlyMastered.toString())
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Practice again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.large) { Text("Done") }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

private fun previewState(
    current: Card? = previewCard,
    input: String = "",
    revealing: Boolean = false,
    celebrating: Boolean = false,
    finished: Boolean = false,
    awaitingDirection: Boolean = false,
    report: SessionReport? = null,
    revealedAnswer: String = "",
    lastTyped: String = "",
    canOverride: Boolean = false,
    direction: TypeDirection? = TypeDirection.TypeBack,
) = TypePracticeUiState(
    loading = false, awaitingDirection = awaitingDirection, direction = direction, current = current,
    input = input, revealing = revealing, revealedAnswer = revealedAnswer, lastTyped = lastTyped,
    canOverride = canOverride, remaining = 3, total = 5, combo = 3, finished = finished,
    report = report, celebrating = celebrating,
)

@Composable
private fun PreviewWrap(state: TypePracticeUiState) {
    AzriTheme {
        TypePracticeContent(
            state = state,
            onChooseDirection = {}, onInput = {}, onSubmit = {}, onDontKnow = {},
            onContinue = {}, onOverride = {}, onRestart = {}, onDone = {},
        )
    }
}

@Preview(name = "Type · prompt", showBackground = true)
@Composable
private fun TypePromptPreview() = PreviewWrap(previewState(input = "How are"))

@Preview(name = "Type · prompt (type front)", showBackground = true)
@Composable
private fun TypeFrontPromptPreview() = PreviewWrap(previewState(input = "¿Cómo", direction = TypeDirection.TypeFront))

@Preview(name = "Type · celebrating", showBackground = true)
@Composable
private fun TypeCelebratingPreview() = PreviewWrap(previewState(input = "How are you?", celebrating = true))

@Preview(name = "Type · revealed (wrong)", showBackground = true)
@Composable
private fun TypeRevealPreview() = PreviewWrap(
    previewState(revealing = true, revealedAnswer = "How are you?", lastTyped = "how is you", canOverride = true),
)

@Preview(name = "Type · direction chooser", showBackground = true)
@Composable
private fun TypeDirectionChooserPreview() = PreviewWrap(previewState(awaitingDirection = true, current = null))

@Preview(name = "Type · report", showBackground = true)
@Composable
private fun TypeReportPreview() = PreviewWrap(
    previewState(current = null, finished = true, report = SessionReport(completed = 12, firstTryCorrect = 9, firstTryAccuracy = 75, bestCombo = 5, newlyMastered = 3)),
)
```

- [ ] **Step 2: Verify it compiles + the whole suite + APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass. (If `Icons.Filled.LocalFireDepartment` is unresolved, confirm material-icons-extended is on the classpath — it is, since `Icons.Filled.School`/`Style`/`Keyboard` are already used; otherwise it's a typo.)

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Restyle Type Practice into the gamified split-zones layout"
```

---

## Final verification
- [ ] `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL, all unit tests green.
- [ ] No commit message mentions "claude" / carries an attribution trailer: `git log --format='%B' origin/main..HEAD | grep -i -E "claude|co-authored-by"` → no output.
- [ ] `git status --short` still shows only `?? docs/superpowers/plans/2026-06-04-realtime-study-queue.md` (the `.superpowers/` companion dir is gitignored).
- [ ] (Optional, emulator) Run a Type Practice session: progress bar fills as cards clear; the 🔥 combo chip climbs and pops, resets on a miss; a correct answer flashes the card mint with a ✓ then auto-advances with a slide; a wrong answer shows the pink char-diff; reduced-motion (system animations off) degrades gracefully.
