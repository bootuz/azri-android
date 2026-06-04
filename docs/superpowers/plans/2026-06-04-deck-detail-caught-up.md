# Deck Detail "All Caught Up" State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On deck detail, when a deck has cards but nothing is new or due, replace the Study button with an encouraging "You're all caught up!" message.

**Architecture:** A pure, JVM-testable `nextReviewLabel(cards, now)` helper computes the soonest future review; `DeckDetailContent` swaps its action slot between the Study button / a new `AllCaughtUp` composable / nothing, gated on `newCount + dueCount` and `total`. No ViewModel or UiState changes (the data is already exposed).

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit4 (JVM unit test for the helper; instrumented test compile-verified).

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Note:** the emulator is currently unavailable, so instrumented (`androidTest`) changes are COMPILE-verified only (`compileDebugAndroidTestKotlin`), not run.

---

### Task 1: `nextReviewLabel` pure helper

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailStats.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/deckdetail/DeckDetailStatsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/nart/simpleanki/feature/deckdetail/DeckDetailStatsTest.kt`:

```kotlin
package nart.simpleanki.feature.deckdetail

import nart.simpleanki.core.domain.fsrs.IntervalFormatter
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckDetailStatsTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun card(id: String, state: CardState, due: Long) = Card(
        id = id, front = "f$id", back = "b$id", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = state.value,
    )

    @Test fun emptyList_isNull() {
        assertNull(nextReviewLabel(emptyList(), now))
    }

    @Test fun allNew_isNull() {
        // New cards are never "reviews", even with a future due value.
        assertNull(nextReviewLabel(listOf(card("1", CardState.New, now + day)), now))
    }

    @Test fun onlyPastDue_isNull() {
        // Already-due review cards are not a FUTURE review.
        assertNull(nextReviewLabel(listOf(card("1", CardState.Review, now - 1_000L)), now))
    }

    @Test fun picksSoonestFutureReview_ignoringNewAndPastDue() {
        val cards = listOf(
            card("1", CardState.Review, now + 3 * day),     // +3d
            card("2", CardState.Learning, now + 1 * day),   // +1d (soonest future review)
            card("3", CardState.New, now + 1_000L),         // New -> ignored
            card("4", CardState.Review, now - 5_000L),      // past due -> ignored
        )
        assertEquals("in ${IntervalFormatter.format(day)}", nextReviewLabel(cards, now))
    }
}
```

- [ ] **Step 2: Run the tests to verify they FAIL**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.deckdetail.DeckDetailStatsTest"`
Expected: FAIL — compile error `Unresolved reference: nextReviewLabel`.

- [ ] **Step 3: Implement the helper**

Create `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailStats.kt`:

```kotlin
package nart.simpleanki.feature.deckdetail

import nart.simpleanki.core.domain.fsrs.IntervalFormatter
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState

/**
 * Relative time until the soonest FUTURE review among non-New cards, e.g. "in 3d".
 * Null when there is no scheduled future review (empty list, all New, or all already due).
 * Unknown FSRS state values are treated as New (excluded), matching `dueLabel` in the screen.
 */
internal fun nextReviewLabel(cards: List<Card>, now: Long): String? {
    val soonest = cards
        .filter { (CardState.fromValue(it.fsrsState) ?: CardState.New) != CardState.New && it.fsrsDue > now }
        .minOfOrNull { it.fsrsDue } ?: return null
    return "in ${IntervalFormatter.format(soonest - now)}"
}
```

- [ ] **Step 4: Run the tests to verify they PASS**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.deckdetail.DeckDetailStatsTest"`
Expected: PASS (all 4 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailStats.kt app/src/test/java/nart/simpleanki/feature/deckdetail/DeckDetailStatsTest.kt
git commit -m "Add nextReviewLabel helper for deck detail"
```

---

### Task 2: "All caught up" UI in `DeckDetailScreen`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt`
- Modify: `app/src/androidTest/java/nart/simpleanki/feature/deckdetail/DeckDetailContentTest.kt`

Build-verified (Compose UI + previews; the instrumented test is compile-verified, not run).

- [ ] **Step 1: Add two imports to `DeckDetailScreen.kt`**

Add these imports (keep all existing ones):

```kotlin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
```

- [ ] **Step 2: Replace the Study `Button` block with a state `when`**

In `DeckDetailContent`, the header `Column` currently ends with this block:

```kotlin
                Button(
                    onClick = onStudy,
                    enabled = state.total > 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null)
                    Text(
                        if (state.dueCount > 0) "Study ${state.dueCount} due" else "Study",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
```

Replace it entirely with:

```kotlin
                val studyable = state.newCount + state.dueCount
                when {
                    studyable > 0 -> Button(
                        onClick = onStudy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null)
                        Text(
                            if (state.dueCount > 0) "Study ${state.dueCount} due" else "Study",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    state.total > 0 -> AllCaughtUp(cards = state.cards, now = now)
                    else -> Unit // empty deck: the list body below shows "No cards yet."
                }
```

(The Study button is now only shown when there is something studyable, so it no longer needs `enabled = …`; it is always enabled when present.)

- [ ] **Step 3: Add the `AllCaughtUp` composable**

In `DeckDetailScreen.kt`, add this private composable (place it just after `DeckDetailContent`, before `SwipeToDeleteCard`):

```kotlin
/** Encouraging state shown when a deck has cards but nothing is new or due to study. */
@Composable
private fun AllCaughtUp(cards: List<Card>, now: Long) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("🎉", style = MaterialTheme.typography.headlineMedium)
        Text(
            "You're all caught up!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Nothing to review in this deck right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        nextReviewLabel(cards, now)?.let { label ->
            Text(
                "Next review $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 4: Add a caught-up `@Preview`**

In `DeckDetailScreen.kt`, add this preview after the existing `DeckDetailEmptyPreview`:

```kotlin
@Preview(name = "Deck detail · caught up", showBackground = true)
@Composable
private fun DeckDetailCaughtUpPreview() {
    val now = 1_000_000_000_000L
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(
                deckId = "d1", deckName = "Spanish 101",
                cards = listOf(
                    previewCard("1", "hola", "hello", CardState.Review, fsrsDue = now + 2 * 86_400_000L),
                    previewCard("2", "gracias", "thank you", CardState.Review, fsrsDue = now + 5 * 86_400_000L),
                ),
                dueCount = 0, newCount = 0,
            ),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
            now = now,
        )
    }
}
```

- [ ] **Step 5: Add an instrumented-test assertion for the caught-up state**

In `app/src/androidTest/java/nart/simpleanki/feature/deckdetail/DeckDetailContentTest.kt`:

(a) add the `assertDoesNotExist` import next to the existing test imports:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
```

(b) add a review-card helper next to the existing `card(...)` helper:

```kotlin
    private fun reviewCard(id: String, due: Long) = Card(
        id = id, front = "f$id", back = "b$id", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = CardState.Review.value,
    )
```

(c) add this test method to the class:

```kotlin
    @Test
    fun caughtUp_showsMessage_andNoStudyButton() {
        val now = 1_000_000_000_000L
        composeRule.setContent {
            DeckDetailContent(
                state = DeckDetailUiState(
                    deckId = "d1", deckName = "Spanish",
                    cards = listOf(reviewCard("c1", due = now + 86_400_000L)),
                    dueCount = 0, newCount = 0,
                ),
                onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
                now = now,
            )
        }
        composeRule.onNodeWithText("You're all caught up!").assertIsDisplayed()
        composeRule.onNodeWithText("Study").assertDoesNotExist()
    }
```

- [ ] **Step 6: Verify it compiles (main + instrumented test sources)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (The emulator is unavailable, so the instrumented test is compile-verified only, not executed.)

- [ ] **Step 7: Run the full app unit test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt app/src/androidTest/java/nart/simpleanki/feature/deckdetail/DeckDetailContentTest.kt
git commit -m "Show all-caught-up message when nothing is due in a deck"
```

---

## Final verification

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available)**

- Open a deck whose cards are all scheduled in the future (Due 0 / New 0): the Study button is replaced by "You're all caught up!", "Nothing to review in this deck right now.", and "Next review in Xd".
- Open a deck with due or new cards: the Study button shows as before ("Study N due" / "Study") and starts a session.
- Open an empty deck: no Study button, body shows "No cards yet. Tap + to add one."
