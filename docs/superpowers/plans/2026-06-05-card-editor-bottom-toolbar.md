# Card Editor: Bottom App Bar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the card editor's Add image / Record audio / reverse-card actions into a stable Material `BottomAppBar`, with Save as the bar's primary FAB, floating above the keyboard.

**Architecture:** Pure presentational change in the stateless `CardFormContent` (in `CardFormScreen.kt`): remove the top-bar Save button, remove the inline attachment chips + reverse `FilterChip`, and add a `Scaffold(bottomBar = { BottomAppBar(...) })`. No ViewModel/state changes; `CardFormContent`'s parameter list is unchanged. The androidTest assertions switch from chip text to icon `contentDescription`.

**Tech Stack:** Kotlin, Jetpack Compose Material3 1.4.0 stable (`BottomAppBar`, `FloatingActionButton`, `IconToggleButton`, `Modifier.imePadding()`); Compose UI test (compile-verified — emulator unavailable).

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

---

## File Structure

- `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt` (modify) — remove top-bar Save; remove the `FlowRow` of `AssistChip`s and the reverse `FilterChip`; add `bottomBar` with the three actions + Save FAB; fix imports; add a preview.
- `app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt` (modify) — chip-text assertions → `onNodeWithContentDescription`.

Verification is build-only (no JVM logic to unit-test, emulator unavailable): both `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` must pass.

---

## Task 1: Move editor actions into a BottomAppBar

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt`
- Modify: `app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt`

- [ ] **Step 1: Update the androidTest assertions (chip text → contentDescription)**

In `CardFormContentTest.kt`, add this import alongside the existing `androidx.compose.ui.test.*` imports:
```kotlin
import androidx.compose.ui.test.onNodeWithContentDescription
```

In `newCard_showsReverseChip_andEditsInvokeCallbacks`, replace the two chip-text assertions:
```kotlin
        composeRule.onNodeWithText("Also create reverse card").assertExists() // FilterChip, new cards only
        composeRule.onNodeWithText("Add image").assertExists()
```
with contentDescription lookups (the buttons are now icons in the bottom bar):
```kotlin
        composeRule.onNodeWithContentDescription("Also create reverse card").assertExists() // toggle, new cards only
        composeRule.onNodeWithContentDescription("Add image").assertExists()
```
(Leave the `onNodeWithText("New card")` title assertion and the `onNodeWithText("Front").performTextInput("hola")` line unchanged — the top-bar title and the Front field label are still text.)

In `editCard_hidesReverseChip`, replace:
```kotlin
        composeRule.onNodeWithText("Also create reverse card").assertDoesNotExist()
```
with:
```kotlin
        composeRule.onNodeWithContentDescription("Also create reverse card").assertDoesNotExist()
```
(Leave `onNodeWithText("Edit card").assertIsDisplayed()` unchanged.)

- [ ] **Step 2: Confirm the androidTest still compiles**

This is a UI relocation verified by compilation (emulator unavailable), not runtime TDD. Confirm the updated test compiles:

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (`onNodeWithContentDescription` is a valid API regardless of which nodes exist at runtime; the matching `contentDescription`s are added to the UI in Step 6.)

- [ ] **Step 3: Fix imports in `CardFormScreen.kt`**

Add these imports:
```kotlin
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconToggleButton
```

Remove these imports **after** Steps 4–6 confirm they are no longer referenced anywhere in the file (they are used only by the code you delete in Step 5):
```kotlin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
```

Also change the opt-in annotation on `CardFormContent` from:
```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
```
to:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
```
(`ExperimentalMaterial3Api` is still required by the deck `ExposedDropdownMenuBox`/`menuAnchor`; `ExperimentalLayoutApi` was only for `FlowRow`.)

- [ ] **Step 4: Remove the Save button from the top app bar**

In `CardFormContent`'s `TopAppBar`, delete the `actions` lambda so the top bar keeps only the title and back arrow. The `TopAppBar(...)` becomes:
```kotlin
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit card" else "New card") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
```
(i.e. remove the `actions = { TextButton(onClick = onSave, enabled = state.canSave) { Text("Save") } },` line entirely.)

- [ ] **Step 5: Remove the inline attachment chips and the reverse FilterChip**

In the content `Column`, delete the entire `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ... }` block (the one containing the "Add image" and "Record audio" `AssistChip`s) **and** the `if (!state.isEdit) { FilterChip( ... "Also create reverse card" ... ) }` block that follows it.

Do NOT touch: the deck dropdown, the Front/Back `OutlinedTextField`s (and the Front `focusRequester`), the image-preview `Box` (with its remove `FilledIconButton`), or the "Audio attached" `Row` (with its remove `IconButton`). Those stay exactly as they are.

- [ ] **Step 6: Add the `bottomBar` to the Scaffold**

Add a `bottomBar` parameter to the `Scaffold(...)` in `CardFormContent` (alongside the existing `snackbarHost` and `topBar` params):
```kotlin
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.imePadding(),
                actions = {
                    if (state.imageName == null) {
                        IconButton(onClick = onAddImage, enabled = !state.uploadingImage) {
                            if (state.uploadingImage) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Image, contentDescription = "Add image")
                            }
                        }
                    }
                    if (state.audioName == null) {
                        IconButton(onClick = onToggleRecording, enabled = !state.uploadingAudio) {
                            when {
                                state.uploadingAudio ->
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                isRecording ->
                                    Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = MaterialTheme.colorScheme.error)
                                else ->
                                    Icon(Icons.Default.Mic, contentDescription = "Record audio")
                            }
                        }
                    }
                    if (!state.isEdit) {
                        IconToggleButton(
                            checked = state.createReverse,
                            onCheckedChange = onToggleReverse,
                        ) {
                            Icon(
                                if (state.createReverse) Icons.Default.Check else Icons.Default.SwapHoriz,
                                contentDescription = "Also create reverse card",
                            )
                        }
                    }
                },
                floatingActionButton = {
                    // M3 FABs have no `enabled` flag: show disabled via muted colors + a gated click.
                    val saveEnabled = state.canSave
                    FloatingActionButton(
                        onClick = { if (saveEnabled) onSave() },
                        containerColor = if (saveEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (saveEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
```

- [ ] **Step 7: Add a preview exercising the bar with attachments**

After the existing previews in `CardFormScreen.kt`, add:
```kotlin
@Preview(name = "Card form · with attachments", showBackground = true)
@Composable
private fun CardFormWithAttachmentsPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                front = "dog", back = "perro",
                imageName = "img.jpg", audioName = "audio.m4a",
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}
```
(This state has an image and audio attached, so the inline preview/remove blocks show and the bar's Add-image / Record-audio icons are correctly hidden — verifying the conditional logic. The existing previews are unchanged.)

- [ ] **Step 8: Verify both compile targets**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL for both. (If Kotlin reports an unused-import error or a still-referenced symbol you tried to delete, reconcile the import list per Step 3.)

- [ ] **Step 9: Verify the full unit-test suite + debug APK still build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass (this change has no unit-test impact, but confirm no regression).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt \
        app/src/androidTest/java/nart/simpleanki/feature/cardform/CardFormContentTest.kt
git commit -m "Move card-editor actions into a bottom app bar"
```
IMPORTANT: Do NOT mention "claude" in the commit message and do NOT add any Co-Authored-By / attribution trailer. Do NOT `git add` other files (there is an unrelated untracked docs plan file in the tree — leave it).

---

## Manual verification (optional, if an emulator is available)

Open the editor (Study tab → all caught up → Add more cards, or deck-detail → Add card):
- A bottom bar shows **Add image**, **Record audio**, and (new cards only) a **reverse** toggle on the left, with a **Save** FAB on the right.
- The bar floats **above the keyboard** (Front autofocuses, keyboard up, bar still visible).
- Save FAB is **muted/disabled** until Front+Back (and a deck in picker mode) are filled.
- Attach an image → the Add-image icon disappears and the inline image preview + remove appears.
- Start recording → the mic icon becomes a red **Stop** icon; stopping attaches audio and hides the icon.
- Save a new card → form clears, the bar's icons reappear, Front re-focuses.
