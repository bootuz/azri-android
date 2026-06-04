# Study Flip Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the additive "Show answer" reveal on the study screen with a true 3D flipping card (tap-to-flip) that matches iOS `FlipCardView`, guided by a per-session "tap to flip" hint.

**Architecture:** A new reusable, stateless `FlipCard` composable owns the Y-axis `graphicsLayer` flip animation and the two faces (question / answer). `StudyViewModel` gains a per-session `showFlipHint` flag (no persistence — the VM lives for the session). `StudyScreen` swaps its static card + button for `FlipCard` wrapped in `key(card.id)` (instant front on the next card) plus the hint / rating-button area.

**Tech Stack:** Kotlin, Jetpack Compose Material3, `Modifier.graphicsLayer`, `animateFloatAsState`, JUnit4 + coroutines-test (existing fakes).

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Design note (intentional refinement of the spec):** the `Card` model has a single image + single audio that act as the *question prompt*. So the **image renders on the FRONT face only** (iOS `cardBack` has no image); **audio replay renders on BOTH faces**. This is faithful to iOS `FlipCardView` and is not a missing back-image — do not "fix" it.

---

### Task 1: Per-session flip-hint state in `StudyViewModel`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test method inside `class StudyViewModelTest` (after `reveal_thenRate_advances_andPersistsFsrs`):

```kotlin
    @Test
    fun flipHint_showsUntilFirstReveal_thenStaysHiddenForSession() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), now = { now })
        runCurrent()

        // Hint visible at session start.
        assertTrue(vm.uiState.value.showFlipHint)

        // First flip hides the hint.
        vm.onReveal()
        assertTrue(vm.uiState.value.isRevealed)
        assertFalse(vm.uiState.value.showFlipHint)

        // Advancing to the next card keeps the hint hidden; new card is back on its front.
        vm.onRate(Rating.Good)
        runCurrent()
        assertFalse(vm.uiState.value.showFlipHint)
        assertFalse(vm.uiState.value.isRevealed)
        assertEquals("c2", vm.uiState.value.current?.id)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: FAIL — compile error `Unresolved reference: showFlipHint` (the property does not exist yet).

- [ ] **Step 3: Add the `showFlipHint` field to `StudyUiState`**

In `StudyViewModel.kt`, add the field to the `StudyUiState` data class (place it right after `isRevealed`):

```kotlin
data class StudyUiState(
    val loading: Boolean = true,
    val current: Card? = null,
    val isRevealed: Boolean = false,
    /** True until the first flip of the session; drives the "tap to flip" hint. Per-session only. */
    val showFlipHint: Boolean = true,
    val completed: Int = 0,
    val remaining: Int = 0,
    val ratingCounts: Map<Rating, Int> = emptyMap(),
    /** Next-due interval label per rating for the current card (e.g. Good -> "4d"), shown on the answer buttons. */
    val ratingIntervals: Map<Rating, String> = emptyMap(),
    val finished: Boolean = false,
)
```

- [ ] **Step 4: Set `showFlipHint = false` on first reveal**

In `StudyViewModel.kt`, change `onReveal()` to also clear the hint:

```kotlin
    fun onReveal() {
        if (_uiState.value.current != null) {
            _uiState.value = _uiState.value.copy(isRevealed = true, showFlipHint = false)
        }
    }
```

No other changes are needed: `load()` builds a fresh `StudyUiState(...)` (so `showFlipHint` defaults to `true` at session start), and `onRate()` uses `prev.copy(...)` (so `showFlipHint = false` carries forward for the rest of the session).

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: PASS (all `StudyViewModelTest` methods green, including the new one).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Add per-session flip-hint state to StudyViewModel"
```

---

### Task 2: Reusable `FlipCard` composable

**Files:**
- Create: `app/src/main/java/nart/simpleanki/ui/components/FlipCard.kt`

This task is build-verified (no Compose UI unit tests, per codebase convention). The `@Preview`s are the visual verification.

- [ ] **Step 1: Create `FlipCard.kt`**

Create `app/src/main/java/nart/simpleanki/ui/components/FlipCard.kt` with exactly this content:

```kotlin
package nart.simpleanki.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.ui.theme.AzriTheme

/**
 * Reusable 3D flip card for the study screen. Mirrors iOS `FlipCardView`.
 *
 * Tapping the card while [revealed] is false calls [onFlip]. The container rotates 0 -> 180
 * degrees on the Y axis; while the rotation is past 90 degrees the answer face is shown,
 * itself counter-rotated 180 degrees so its text reads upright instead of mirrored. The image
 * is the question prompt, so it appears on the front only; audio replay appears on both faces.
 *
 * Callers should wrap this in `key(card.id)` so the animation resets to the front (no
 * reverse-flip) when the next card appears.
 */
@Composable
fun FlipCard(
    card: Card,
    revealed: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "flip",
    )
    AzriCard(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(enabled = !revealed) { onFlip() },
    ) {
        if (rotation <= 90f) {
            CardFace(
                label = "QUESTION",
                text = card.front,
                textStyle = MaterialTheme.typography.headlineSmall,
                textColor = MaterialTheme.colorScheme.onSurface,
                imageName = card.image,
                imagePath = card.imagePath,
                audioName = card.audioName,
                audioPath = card.audioPath,
            )
        } else {
            // Counter-rotate so the answer reads upright rather than mirrored.
            CardFace(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                label = "ANSWER",
                text = card.back,
                textStyle = MaterialTheme.typography.titleLarge,
                textColor = MaterialTheme.colorScheme.primary,
                // Image is the question prompt — front only. Audio replays on the answer.
                imageName = null,
                imagePath = null,
                audioName = card.audioName,
                audioPath = card.audioPath,
            )
        }
    }
}

@Composable
private fun CardFace(
    label: String,
    text: String,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    imageName: String? = null,
    imagePath: String? = null,
    audioName: String? = null,
    audioPath: String? = null,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        imageName?.let { name ->
            MediaImage(name, imagePath, Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = textStyle, color = textColor, textAlign = TextAlign.Center)
        audioName?.let { name ->
            Spacer(Modifier.height(12.dp))
            AudioPlayButton(name, audioPath)
        }
    }
}

private val previewFlipCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "FlipCard · front", showBackground = true)
@Composable
private fun FlipCardFrontPreview() {
    AzriTheme {
        FlipCard(
            previewFlipCard, revealed = false, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(400.dp).padding(20.dp),
        )
    }
}

@Preview(name = "FlipCard · back", showBackground = true)
@Composable
private fun FlipCardBackPreview() {
    AzriTheme {
        FlipCard(
            previewFlipCard, revealed = true, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(400.dp).padding(20.dp),
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `card.image` / `card.imagePath` / `card.audioName` / `card.audioPath` are unresolved, confirm the exact field names against `StudyScreen.kt`'s current `StudyCard` — it uses `card.image`, `card.imagePath`, `card.audioName`, `card.audioPath` — and match them.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/components/FlipCard.kt
git commit -m "Add reusable FlipCard composable with 3D flip animation"
```

---

### Task 3: Wire `FlipCard` + hint into `StudyScreen`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`

Build-verified (Compose UI). Removes the "Show answer" button and the additive back reveal; the card itself flips on tap.

- [ ] **Step 1: Add the new imports**

In `StudyScreen.kt`, add these imports (keep the existing ones; `HorizontalDivider` becomes unused — remove its import):

```kotlin
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.key
```

Remove this now-unused import line:

```kotlin
import androidx.compose.material3.HorizontalDivider
```

- [ ] **Step 2: Replace the `StudyCard` composable**

Replace the entire `private fun StudyCard(...)` function body with:

```kotlin
@Composable
private fun StudyCard(state: StudyUiState, onReveal: () -> Unit, onRate: (Rating) -> Unit) {
    val card = state.current ?: return
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // key(card.id) resets the flip animation per card, so the next card appears on its
        // front instantly with no reverse-flip.
        key(card.id) {
            nart.simpleanki.ui.components.FlipCard(
                card = card,
                revealed = state.isRevealed,
                onFlip = onReveal,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (!state.isRevealed) {
            if (state.showFlipHint) {
                Row(
                    Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.Center,
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
            } else {
                // Keep the layout stable once the hint is gone.
                Spacer(Modifier.height(50.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // iOS rating colors (SwiftUI system): again=pink, hard=orange, good=indigo, easy=mint.
                RatingButton("Again", state.ratingIntervals[Rating.Again], Color(0xFFFF2D55), Modifier.weight(1f)) { onRate(Rating.Again) }
                RatingButton("Hard", state.ratingIntervals[Rating.Hard], Color(0xFFFF9500), Modifier.weight(1f)) { onRate(Rating.Hard) }
                RatingButton("Good", state.ratingIntervals[Rating.Good], Color(0xFF5856D6), Modifier.weight(1f)) { onRate(Rating.Good) }
                RatingButton("Easy", state.ratingIntervals[Rating.Easy], Color(0xFF00C7BE), Modifier.weight(1f)) { onRate(Rating.Easy) }
            }
        }
    }
}
```

Note: `onReveal` is now wired to the card tap (`onFlip`), not a button. The `Button` import stays — it is still used by `SessionSummary`.

- [ ] **Step 3: Add a hint-hidden preview**

In `StudyScreen.kt`, replace the existing `StudyQuestionPreview` with these two previews (the question state now carries an explicit `showFlipHint`, plus a variant with the hint gone):

```kotlin
@Preview(name = "Study · question (hint)", showBackground = true)
@Composable
private fun StudyQuestionPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(loading = false, current = previewStudyCard, isRevealed = false, showFlipHint = true, remaining = 5),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}

@Preview(name = "Study · question (no hint)", showBackground = true)
@Composable
private fun StudyQuestionNoHintPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(loading = false, current = previewStudyCard, isRevealed = false, showFlipHint = false, remaining = 5),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL with no `Unresolved reference` errors.

- [ ] **Step 5: Run the full app unit test suite to confirm nothing regressed**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt
git commit -m "Flip the study card on tap with a per-session hint"
```

---

## Final verification

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (if an emulator is available)**

Install and study a deck:
- The card shows the question with a "Tap to flip" hint + finger icon below it.
- Tapping the card flips it (3D Y-axis) to the answer (accent-colored), and the hint is replaced by the four rating buttons.
- The hint does **not** reappear on subsequent cards in the same session.
- Rating a card shows the next card already on its front (no backward flip).
