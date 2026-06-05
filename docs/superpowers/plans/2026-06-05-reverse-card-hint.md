# Card Editor: Reverse-Card Hint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a persistent inline caption ("A reverse card (Back → Front) will also be created.") in the card editor while the bottom-bar reverse toggle is on.

**Architecture:** Add one conditional `Row` as the last child of `CardFormContent`'s form `Column`, gated by `state.createReverse && !state.isEdit`. No state changes — `createReverse` already exists. Add an androidTest assertion.

**Tech Stack:** Kotlin, Jetpack Compose Material3 1.4.0; Compose UI test (compile-verified; optionally run on the connected emulator).

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

---

## File Structure

- `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt` (modify) — add the hint `Row`.
- `app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt` (modify) — assert the hint shows when reverse is on and is hidden otherwise.

All referenced Compose symbols (`Row`, `Arrangement`, `Alignment`, `Icon`, `Icons.Default.SwapHoriz`, `Modifier.size`, `Text`, `MaterialTheme`) are already imported in `CardFormScreen.kt` — no new imports.

---

## Task 1: Add the reverse-card hint

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt`
- Modify: `app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt`

- [ ] **Step 1: Add the test assertions**

In `CardFormContentTest.kt`, add this new test inside `class CardFormContentTest` (after the existing tests):

```kotlin
    @Test
    fun newCard_reverseOn_showsHint() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false, createReverse = true),
                onFrontChange = {},
                onBackChange = {},
                onSelectDeck = {},
                isRecording = false,
                onToggleReverse = {},
                onAddImage = {},
                onRemoveImage = {},
                onToggleRecording = {},
                onRemoveAudio = {},
                onSave = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("A reverse card (Back → Front) will also be created.")
            .assertExists()
    }

    @Test
    fun newCard_reverseOff_hidesHint() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false, createReverse = false),
                onFrontChange = {},
                onBackChange = {},
                onSelectDeck = {},
                isRecording = false,
                onToggleReverse = {},
                onAddImage = {},
                onRemoveImage = {},
                onToggleRecording = {},
                onRemoveAudio = {},
                onSave = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("A reverse card (Back → Front) will also be created.")
            .assertDoesNotExist()
    }
```

(`onNodeWithText` is already imported in this test file; `CardFormUiState` is in the same package, no import needed.)

- [ ] **Step 2: Confirm the androidTest compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (The hint string node is added to the UI in Step 3; this confirms the test compiles.)

- [ ] **Step 3: Add the hint Row to `CardFormContent`**

In `CardFormScreen.kt`, the form `Column` currently ends with the "Audio attached" block:
```kotlin
            // Audio attached
            if (state.audioName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioPlayButton(state.audioName, state.audioPath)
                    Text("Audio attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRemoveAudio) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove audio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
```
Immediately **after** that `if (state.audioName != null) { ... }` block (and still inside the `Column`), add the hint as the Column's last child:
```kotlin
            // Reverse-card hint: shown while the bottom-bar reverse toggle is on (new cards only).
            if (state.createReverse && !state.isEdit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "A reverse card (Back → Front) will also be created.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```

- [ ] **Step 4: Verify both compile targets**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Run the unit-test suite + debug APK (no regression)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt \
        app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt
git commit -m "Show a hint when a reverse card will be created"
```
IMPORTANT: Do NOT mention "claude" in the commit message and do NOT add any Co-Authored-By / attribution trailer. Do NOT `git add` other files (there is an unrelated untracked docs plan file in the tree — leave it).

---

## Manual verification (optional, emulator connected)

Open the editor (Study tab → Add more cards). Tap the **reverse toggle** (the swap icon in the bottom bar): the caption "A reverse card (Back → Front) will also be created." appears at the bottom of the form; tapping it off removes the caption. Saving a new card (which resets `createReverse`) also clears it.
