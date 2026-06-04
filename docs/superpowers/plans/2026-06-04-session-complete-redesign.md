# Session-Complete UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bare study "session complete" screen with an iOS-parity summary — 🎉 pop-in header, accuracy-keyed message, Reviewed/Accuracy/Duration stat row, proportional rating-distribution bar, and a Finish button.

**Architecture:** Pure JVM-testable stat helpers (`SessionStats.kt`) hold the logic (accuracy, message, duration formatting); a new `SessionSummary.kt` Compose file holds the visuals + subcomponents; `StudyViewModel` gains session-duration tracking via its already-injected `now: () -> Long`. Mirrors the iOS decomposition (`SessionStatistics` model + `Components/`).

**Tech Stack:** Kotlin, Jetpack Compose Material3, `animateFloatAsState`/`spring`, `material-icons-extended`, JUnit4 + coroutines-test (existing fakes).

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

---

### Task 1: Pure stat helpers (`SessionStats.kt`)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/study/SessionStats.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/SessionStatsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/nart/simpleanki/feature/study/SessionStatsTest.kt`:

```kotlin
package nart.simpleanki.feature.study

import nart.simpleanki.core.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsTest {

    @Test fun accuracy_typicalMix_isCorrectRounded() {
        // good+easy = 15 of 25 reviews → 60%
        val counts = mapOf(Rating.Again to 4, Rating.Hard to 6, Rating.Good to 10, Rating.Easy to 5)
        assertEquals(60, sessionAccuracy(counts))
    }

    @Test fun accuracy_allEasy_is100() {
        assertEquals(100, sessionAccuracy(mapOf(Rating.Easy to 10)))
    }

    @Test fun accuracy_noReviews_isZero() {
        assertEquals(0, sessionAccuracy(emptyMap()))
    }

    @Test fun accuracy_rounds_toNearest() {
        // 5 good of 7 = 71.43% → 71
        assertEquals(71, sessionAccuracy(mapOf(Rating.Good to 5, Rating.Again to 2)))
        // 6 good of 7 = 85.71% → 86
        assertEquals(86, sessionAccuracy(mapOf(Rating.Good to 6, Rating.Again to 1)))
    }

    @Test fun message_matchesThresholds() {
        assertEquals("Outstanding session!", motivationalMessage(100))
        assertEquals("Outstanding session!", motivationalMessage(90))
        assertEquals("Great work, keep it up!", motivationalMessage(70))
        assertEquals("Solid effort, you're improving!", motivationalMessage(50))
        assertEquals("Every review makes you stronger!", motivationalMessage(49))
        assertEquals("Every review makes you stronger!", motivationalMessage(0))
    }

    @Test fun duration_formatsAbbreviated() {
        assertEquals("0s", formattedDuration(0))
        assertEquals("42s", formattedDuration(42_000))
        assertEquals("5m", formattedDuration(300_000))
        assertEquals("5m 12s", formattedDuration(312_000))
    }

    @Test fun duration_negative_clampsToZero() {
        assertEquals("0s", formattedDuration(-1_000))
    }
}
```

- [ ] **Step 2: Run the tests to verify they FAIL**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.SessionStatsTest"`
Expected: FAIL — compile error `Unresolved reference: sessionAccuracy` (functions don't exist yet).

- [ ] **Step 3: Implement the helpers**

Create `app/src/main/java/nart/simpleanki/feature/study/SessionStats.kt`:

```kotlin
package nart.simpleanki.feature.study

import nart.simpleanki.core.domain.model.Rating
import kotlin.math.roundToInt

/** (good + easy) / total reviews x 100, rounded to nearest Int. 0 when there are no reviews. */
fun sessionAccuracy(ratingCounts: Map<Rating, Int>): Int {
    val total = ratingCounts.values.sum()
    if (total == 0) return 0
    val correct = (ratingCounts[Rating.Good] ?: 0) + (ratingCounts[Rating.Easy] ?: 0)
    return (correct * 100.0 / total).roundToInt()
}

/** Accuracy-keyed encouragement, mirroring the iOS thresholds. */
fun motivationalMessage(accuracy: Int): String = when {
    accuracy >= 90 -> "Outstanding session!"
    accuracy >= 70 -> "Great work, keep it up!"
    accuracy >= 50 -> "Solid effort, you're improving!"
    else -> "Every review makes you stronger!"
}

/** Abbreviated duration: "0s", "42s", "5m", "5m 12s". Drops a leading 0m and a trailing 0s. */
fun formattedDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes == 0L -> "${seconds}s"
        seconds == 0L -> "${minutes}m"
        else -> "${minutes}m ${seconds}s"
    }
}
```

- [ ] **Step 4: Run the tests to verify they PASS**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.SessionStatsTest"`
Expected: PASS (all 7 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/SessionStats.kt app/src/test/java/nart/simpleanki/feature/study/SessionStatsTest.kt
git commit -m "Add session stat helpers (accuracy, message, duration)"
```

---

### Task 2: Session duration tracking in `StudyViewModel`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test inside `class StudyViewModelTest` (after `reveal_thenRate_advances_andPersistsFsrs`):

```kotlin
    @Test
    fun finishingSession_stampsElapsedDuration() = runTest {
        var clock = now
        val repo = CardRepository(FakeCardDao(), now = { clock })
        repo.upsert(newCard("c1"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { clock }), FakeSettingsRepository(), now = { clock })
        runCurrent()
        // Session started at `now`; advance the clock 3s, then rate the only card to finish.
        clock = now + 3_000
        vm.onRate(Rating.Good)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.finished)
        assertEquals(3_000L, s.durationMillis)
    }
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: FAIL — compile error `Unresolved reference: durationMillis`.

- [ ] **Step 3: Add `durationMillis` to `StudyUiState`**

In `StudyViewModel.kt`, add the field to `StudyUiState` (place it right after `finished`):

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
    /** Wall-clock millis from session start to finish; stamped once when the session finishes. */
    val durationMillis: Long = 0,
)
```

- [ ] **Step 4: Record the session start and stamp the duration on finish**

In `StudyViewModel.kt`, add a private field next to the other private state (right after `private val queue = ArrayDeque<Card>()`):

```kotlin
    private var sessionStartMillis: Long = 0L
```

At the very start of `load()` (before `val settings = ...`), record the start time:

```kotlin
    private suspend fun load() {
        sessionStartMillis = now()
        val settings = settingsRepository.settings.first()
```

In `load()`, add `durationMillis` to the `StudyUiState(...)` it builds (so the nothing-to-study path is stamped too):

```kotlin
        _uiState.value = StudyUiState(
            loading = false,
            current = first,
            remaining = queue.size,
            ratingIntervals = intervalsFor(first),
            finished = queue.isEmpty(),
            durationMillis = if (queue.isEmpty()) now() - sessionStartMillis else 0,
        )
```

In `onRate()`, add `durationMillis` to the `prev.copy(...)` so it's stamped when the last card is rated:

```kotlin
        _uiState.value = prev.copy(
            current = next,
            isRevealed = false,
            completed = prev.completed + 1,
            remaining = queue.size,
            ratingCounts = counts,
            ratingIntervals = intervalsFor(next),
            finished = next == null,
            durationMillis = if (next == null) now() - sessionStartMillis else prev.durationMillis,
        )
```

Do not change anything else (analytics events, flip-hint, scheduling all stay as-is).

- [ ] **Step 5: Run the test to verify it PASSES**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: PASS (all `StudyViewModelTest` methods green, including the new one).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Track session duration in StudyViewModel"
```

---

### Task 3: Session-complete UI (`SessionSummary.kt`) + wire into `StudyScreen`

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/study/SessionSummary.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`

Build-verified (Compose UI, previews). Create the new file AND remove the old `SessionSummary` from `StudyScreen.kt` in the SAME task — two same-package functions with the same name/signature would otherwise be a redeclaration error.

- [ ] **Step 1: Create `SessionSummary.kt`**

Create `app/src/main/java/nart/simpleanki/feature/study/SessionSummary.kt`:

```kotlin
package nart.simpleanki.feature.study

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.ui.theme.AzriTheme

// iOS rating palette (shared with the study rating buttons): again=pink, hard=orange, good=indigo, easy=mint.
private val RatingColors = mapOf(
    Rating.Again to Color(0xFFFF2D55),
    Rating.Hard to Color(0xFFFF9500),
    Rating.Good to Color(0xFF5856D6),
    Rating.Easy to Color(0xFF00C7BE),
)

/** Study "session complete" summary — mirrors iOS SessionSummaryView (streak omitted). */
@Composable
fun SessionSummary(state: StudyUiState, onDone: () -> Unit) {
    val accuracy = sessionAccuracy(state.ratingCounts)
    val haptics = LocalHapticFeedback.current
    var appeared by remember { mutableStateOf(false) }
    val emojiScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "emojiScale",
    )
    val emojiAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "emojiAlpha",
    )
    LaunchedEffect(Unit) {
        appeared = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text("🎉", fontSize = 64.sp, modifier = Modifier.scale(emojiScale).alpha(emojiAlpha))
        Spacer(Modifier.height(16.dp))
        Text("Session Complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            motivationalMessage(accuracy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        SessionStatsRow(
            reviewed = state.completed,
            accuracy = accuracy,
            durationLabel = formattedDuration(state.durationMillis),
        )

        if (state.completed > 0) {
            Spacer(Modifier.height(32.dp))
            RatingDistributionBar(state.ratingCounts)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Finish", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun SessionStatsRow(reviewed: Int, accuracy: Int, durationLabel: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(Icons.Outlined.CheckCircle, Color(0xFF34C759), reviewed.toString(), "Reviewed")
        VerticalDivider(Modifier.height(44.dp))
        StatItem(Icons.Outlined.TrackChanges, accuracyColor(accuracy), "$accuracy%", "Accuracy")
        VerticalDivider(Modifier.height(44.dp))
        StatItem(Icons.Outlined.Schedule, Color(0xFFFF9500), durationLabel, "Duration")
    }
}

@Composable
private fun StatItem(icon: ImageVector, iconColor: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RatingDistributionBar(ratingCounts: Map<Rating, Int>) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
            Rating.entries.forEach { rating ->
                val count = ratingCounts[rating] ?: 0
                if (count > 0) {
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(RatingColors.getValue(rating)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Rating.entries.forEach { rating ->
                LegendItem(rating.name, ratingCounts[rating] ?: 0, RatingColors.getValue(rating))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Accuracy -> color, mirroring iOS accuracyColor (>=80 mint, >=60 indigo, >=40 pink, else orange). */
private fun accuracyColor(accuracy: Int): Color = when {
    accuracy >= 80 -> Color(0xFF00C7BE)
    accuracy >= 60 -> Color(0xFF5856D6)
    accuracy >= 40 -> Color(0xFFFF2D55)
    else -> Color(0xFFFF9500)
}

@Preview(name = "Summary · good session", showBackground = true)
@Composable
private fun SessionSummaryGoodPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 25, durationMillis = 312_000,
                ratingCounts = mapOf(Rating.Again to 4, Rating.Hard to 6, Rating.Good to 10, Rating.Easy to 5),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Summary · low accuracy", showBackground = true)
@Composable
private fun SessionSummaryLowPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 10, durationMillis = 180_000,
                ratingCounts = mapOf(Rating.Again to 5, Rating.Hard to 2, Rating.Good to 2, Rating.Easy to 1),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Summary · all easy", showBackground = true)
@Composable
private fun SessionSummaryAllEasyPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 10, durationMillis = 180_000,
                ratingCounts = mapOf(Rating.Easy to 10),
            ),
            onDone = {},
        )
    }
}
```

- [ ] **Step 2: Remove the old `SessionSummary` from `StudyScreen.kt`**

In `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`, delete the entire old private function (the call site `SessionSummary(state, onDone)` in `StudyContent` will now resolve to the new public one in `SessionSummary.kt`, same package):

```kotlin
@Composable
private fun SessionSummary(state: StudyUiState, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("${state.completed} cards reviewed", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Rating.entries.forEach { rating ->
            val count = state.ratingCounts[rating] ?: 0
            if (count > 0) Text("${rating.name}: $count")
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}
```

Also delete the now-orphaned `StudySummaryPreview` from `StudyScreen.kt` (its three-line text summary is superseded by the new previews in `SessionSummary.kt`):

```kotlin
@Preview(name = "Study · summary", showBackground = true)
@Composable
private fun StudySummaryPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(
                loading = false, finished = true, completed = 12,
                ratingCounts = mapOf(Rating.Again to 2, Rating.Good to 8, Rating.Easy to 2),
            ),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}
```

Leave `StudyContent` (which calls `SessionSummary(state, onDone)`), `StudyCard`, `RatingButton`, `previewStudyCard`, and the question/answer previews untouched. After deleting the function, if the compiler reports any now-unused imports in `StudyScreen.kt`, remove them (do not leave dead imports).

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `VerticalDivider` is unresolved, it lives in `androidx.compose.material3` — confirm the import `androidx.compose.material3.VerticalDivider`; the project already uses `HorizontalDivider` from the same package, so it is available.)

- [ ] **Step 4: Run the full app unit test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/SessionSummary.kt app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt
git commit -m "Redesign session-complete UI to match iOS"
```

---

## Final verification

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (if an emulator is available)**

Finish a study session:
- 🎉 pops in (scale + fade) with a haptic.
- "Session Complete" + an accuracy-appropriate message.
- The Reviewed / Accuracy / Duration row shows correct values (accuracy %, duration like "1m 20s").
- The rating-distribution bar segments are proportional to the Again/Hard/Good/Easy counts, with a matching legend.
- Finish returns to the previous screen.
