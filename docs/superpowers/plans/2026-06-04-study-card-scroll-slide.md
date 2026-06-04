# Study Card: Scroll + Slide-Up Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the study card's content scrollable for long text, and animate the rating buttons sliding up from the bottom on answer reveal.

**Architecture:** Two independent presentation changes — `FlipCard.kt`'s `CardFace` gets a "center-when-short, scroll-when-tall" wrapper (`BoxWithConstraints` + `verticalScroll` + `heightIn(min = maxHeight)`); `StudyScreen.kt`'s `StudyCard` replaces its `if/else` bottom slot with stacked `AnimatedVisibility`s so the rating row slides up + fades in. No state, ViewModel, or data changes.

**Tech Stack:** Kotlin, Jetpack Compose (`androidx.compose.animation`, `foundation.verticalScroll`, `BoxWithConstraints`). Build-verified (no unit tests for animation/layout).

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Note:** the emulator is currently unavailable, so instrumented (`androidTest`) sources are COMPILE-verified only, not run.

---

### Task 1: Scrollable card content (`FlipCard.kt` → `CardFace`)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/ui/components/FlipCard.kt`

Build-verified + a long-text preview.

- [ ] **Step 1: Add four imports**

In `FlipCard.kt`, add these imports (keep all existing ones):

```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 2: Make `CardFace` scrollable**

Replace the entire `private fun CardFace(...)` composable with this version (the only change is wrapping the `Column` in `BoxWithConstraints` and moving the layout/scroll modifiers onto the `Column`; the inner content is unchanged):

```kotlin
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
    // Center the content when it fits; scroll when it overflows (heightIn(min = maxHeight)
    // forces the column to at least fill the card so Arrangement.Center works, but lets it
    // grow past the viewport for long text). Mirrors iOS's ScrollView { ... }.minHeight.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(24.dp),
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
}
```

Note: the `modifier` parameter (the back face passes `Modifier.graphicsLayer { rotationY = 180f }`) now wraps the whole `BoxWithConstraints`, so the counter-rotation still applies to the entire face. `maxHeight` is the `BoxWithConstraintsScope` viewport height.

- [ ] **Step 3: Add a long-text preview**

In `FlipCard.kt`, add this preview data + preview after the existing `FlipCardBackPreview`:

```kotlin
private val previewLongCard = Card(
    id = "c2",
    front = "Explain the difference between the present perfect and the simple past tense, " +
        "with at least three examples of each, and describe when a learner should prefer one " +
        "over the other in everyday conversation. Then summarize the key rule in one sentence.",
    back = "The present perfect links a past action to the present; the simple past describes a " +
        "finished action at a definite time.",
    deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "FlipCard · long text", showBackground = true)
@Composable
private fun FlipCardLongTextPreview() {
    AzriTheme {
        FlipCard(
            previewLongCard, revealed = false, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(20.dp),
        )
    }
}
```

(The 300.dp height forces the long front text to overflow, so the preview demonstrates the scroll wrapper rather than clipping.)

- [ ] **Step 4: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/components/FlipCard.kt
git commit -m "Make flip card content scrollable for long text"
```

---

### Task 2: Slide-up rating buttons (`StudyScreen.kt` → `StudyCard`)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`

Build-verified.

- [ ] **Step 1: Add four animation imports**

In `StudyScreen.kt`, add these imports (keep all existing ones; `Box` is already imported):

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
```

- [ ] **Step 2: Replace the bottom-slot `if/else` with stacked `AnimatedVisibility`s**

In `StudyCard`, the block currently reads (from the `Spacer(Modifier.height(16.dp))` after the `key(card.id) { ... }` block to the end of the function's `Column`):

```kotlin
        Spacer(Modifier.height(16.dp))
        if (!state.isRevealed) {
            if (state.showFlipHint) {
                Row(
                    Modifier.fillMaxWidth().height(60.dp),
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
                Spacer(Modifier.height(60.dp))
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
```

Replace that entire block with:

```kotlin
        Spacer(Modifier.height(16.dp))
        // Fixed-height slot so the FlipCard above never shifts; the hint cross-fades and the
        // rating row slides up from the bottom edge on reveal.
        Box(Modifier.fillMaxWidth().height(60.dp)) {
            AnimatedVisibility(
                visible = !state.isRevealed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (state.showFlipHint) {
                    Row(
                        Modifier.fillMaxWidth().height(60.dp),
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
                }
            }
            AnimatedVisibility(
                visible = state.isRevealed,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = fadeOut(),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // iOS rating colors (SwiftUI system): again=pink, hard=orange, good=indigo, easy=mint.
                    RatingButton("Again", state.ratingIntervals[Rating.Again], Color(0xFFFF2D55), Modifier.weight(1f)) { onRate(Rating.Again) }
                    RatingButton("Hard", state.ratingIntervals[Rating.Hard], Color(0xFFFF9500), Modifier.weight(1f)) { onRate(Rating.Hard) }
                    RatingButton("Good", state.ratingIntervals[Rating.Good], Color(0xFF5856D6), Modifier.weight(1f)) { onRate(Rating.Good) }
                    RatingButton("Easy", state.ratingIntervals[Rating.Easy], Color(0xFF00C7BE), Modifier.weight(1f)) { onRate(Rating.Easy) }
                }
            }
        }
```

Behavior: the outer `Box` is always `60.dp` tall (so the card above is stable). When `!isRevealed`, the first `AnimatedVisibility` shows the hint (or nothing, when `showFlipHint` is false). On reveal, the hint fades out and the rating row slides up from the bottom edge (`slideInVertically(initialOffsetY = { it })`, clipped to the box) while fading in. On advancing to the next card (`isRevealed → false`), the rating row fades out and the hint fades back in.

- [ ] **Step 3: Verify it compiles (main + instrumented test sources)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (The instrumented `StudyContentTest` still composes the rating buttons when revealed — `AnimatedVisibility(visible = true)` renders its content — so it remains valid; it is compile-verified only, no emulator.)

- [ ] **Step 4: Run the full app unit test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt
git commit -m "Slide rating buttons up on answer reveal"
```

---

## Final verification

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available)**

- Study a card with long front/back text: the card content scrolls (drag) instead of clipping; a short card stays vertically centered. Tapping (not dragging) still flips.
- On tapping to reveal: the four rating buttons slide up from the bottom and fade in; the "Tap to flip" hint cross-fades out.
- Rating a card and advancing: the next card shows on its front with the hint/spacer; no leftover buttons.
