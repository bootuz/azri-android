# Card Editor: In-Editor Deck Selector + Autofocus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add cards straight from the Study tab's "Add more cards" button into a card editor that has an in-place deck selector, with the Front field autofocused on open and after each save.

**Architecture:** A new no-arg `cardForm` nav route opens `CardFormScreen` with `deckId = null`, putting `CardFormViewModel` into "picker mode": it observes `DeckRepository.observeDecks()`, requires a deck selection before save, and preserves the chosen deck across rapid-entry saves. The Front field uses a `FocusRequester` re-fired on open and on each `savedTick` bump. Fixed-deck mode (deck-detail "Add card") is unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`ExposedDropdownMenuBox`, `FocusRequester`), Koin (`viewModel { parametersOf(...) }`), navigation-compose, JUnit4 + coroutines-test.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

---

## File Structure

- `feature/cardform/CardFormViewModel.kt` (modify) — `DeckOption` type; `pickDeck`/`decks`/`selectedDeckId` state; nullable `deckId`; `deckRepository`; deck loading; `onSelectDeck`; `canSave` deck gate; `save()` uses selected deck + preserves it on reset.
- `app/src/test/.../feature/cardform/CardFormViewModelTest.kt` (modify) — picker-mode tests.
- `di/AppModule.kt` (modify) — `CardFormArgs.deckId` nullable; pass `deckRepository` into the VM.
- `feature/cardform/CardFormScreen.kt` (modify) — nullable `deckId`; deck dropdown; Front autofocus; `onSelectDeck` wiring; previews.
- `ui/navigation/AzriNavHost.kt` (modify) — new `cardForm` route; pass `onAddCards` to `StudyQueueScreen`.
- `feature/queue/StudyQueueScreen.kt` (modify) — `onAddCards` param threaded to `HeroCard`; "Add more cards" button uses it.

---

## Task 1: CardFormViewModel — picker mode

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these imports to the top of `CardFormViewModelTest.kt` (after the existing imports):

```kotlin
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.domain.model.Deck
```

Add these tests inside `class CardFormViewModelTest`:

```kotlin
@Test
fun pickerMode_loadsDecks_andRequiresDeckBeforeSave() = runTest {
    val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
    deckRepo.upsert(Deck(id = "d1", name = "French", dateCreated = now, lastModified = now))
    deckRepo.upsert(Deck(id = "d2", name = "Spanish", dateCreated = now, lastModified = now))
    val cardRepo = CardRepository(FakeCardDao(), now = { now })
    val vm = CardFormViewModel(
        deckId = null, cardRepository = cardRepo, mediaManager = media(),
        deckRepository = deckRepo, now = { now },
    )
    runCurrent()

    assertTrue(vm.uiState.value.pickDeck)
    assertEquals(listOf("French", "Spanish"), vm.uiState.value.decks.map { it.name })

    // Front + Back filled, but no deck chosen yet → cannot save.
    vm.onFrontChange("bonjour"); vm.onBackChange("hello")
    assertFalse(vm.uiState.value.canSave)

    vm.onSelectDeck("d2")
    assertTrue(vm.uiState.value.canSave)
}

// Note: `DeckRepository(FakeDeckDao(), now = { now })` is constructed inline in each test;
// there is no shared helper. Seed decks via `deckRepo.upsert(Deck(...))`.

@Test
fun pickerMode_save_persistsToSelectedDeck_andPreservesDeckOnReset() = runTest {
    val dao = FakeCardDao()
    val cardRepo = CardRepository(dao, now = { now })
    val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
    deckRepo.upsert(Deck(id = "d1", name = "French", dateCreated = now, lastModified = now))
    deckRepo.upsert(Deck(id = "d2", name = "Spanish", dateCreated = now, lastModified = now))
    val vm = CardFormViewModel(
        deckId = null, cardRepository = cardRepo, mediaManager = media(),
        deckRepository = deckRepo, idGenerator = ids("c-1"), now = { now },
    )
    runCurrent()
    vm.onSelectDeck("d2")
    vm.onFrontChange("bonjour"); vm.onBackChange("hello")
    vm.save(); runCurrent()

    // Card landed in the selected deck d2 (not d1).
    assertEquals(1, dao.observeByDeck("d2").first().size)
    assertEquals(0, dao.observeByDeck("d1").first().size)
    // Form reset for the next card, but the deck selection sticks.
    assertEquals(1, vm.uiState.value.savedTick)
    assertEquals("", vm.uiState.value.front)
    assertEquals("d2", vm.uiState.value.selectedDeckId)
    assertTrue(vm.uiState.value.pickDeck)
    assertEquals(2, vm.uiState.value.decks.size)
}

@Test
fun fixedDeckMode_hasNoPicker_andSavesWithoutManualSelection() = runTest {
    val dao = FakeCardDao()
    val cardRepo = CardRepository(dao, now = { now })
    val vm = CardFormViewModel("d1", cardRepo, media(), idGenerator = ids("c-1"), now = { now })
    assertFalse(vm.uiState.value.pickDeck)
    assertEquals("d1", vm.uiState.value.selectedDeckId)
    vm.onFrontChange("hello"); vm.onBackChange("hola")
    assertTrue(vm.uiState.value.canSave)   // no manual deck pick needed
    vm.save(); runCurrent()
    assertEquals(1, dao.observeByDeck("d1").first().size)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest"`
Expected: COMPILE FAILURE — `pickDeck`, `decks`, `selectedDeckId`, `onSelectDeck`, and the `deckRepository =` / nullable `deckId` constructor params don't exist yet.

- [ ] **Step 3: Add `DeckOption` and the new state fields**

In `CardFormViewModel.kt`, add the import:
```kotlin
import nart.simpleanki.core.data.repository.DeckRepository
```

Add the `DeckOption` type just above `data class CardFormUiState`:
```kotlin
/** A deck choice for the in-editor selector (queue-path "picker mode"). */
data class DeckOption(val id: String, val name: String)
```

Add three fields to `CardFormUiState` (place them after `audioPath`/`uploadingAudio`, before `savedTick`):
```kotlin
    /** True ⇒ the user picks the destination deck in-editor (opened from the Study tab). */
    val pickDeck: Boolean = false,
    val decks: List<DeckOption> = emptyList(),
    val selectedDeckId: String? = null,
```

Change `canSave` to require a deck:
```kotlin
    val canSave: Boolean
        get() = front.isNotBlank() && back.isNotBlank() &&
            !uploadingImage && !uploadingAudio && selectedDeckId != null
```

- [ ] **Step 4: Make `deckId` nullable, inject `deckRepository`, load decks, add `onSelectDeck`**

Change the constructor signature (first param nullable; add `deckRepository` after `mediaManager`):
```kotlin
class CardFormViewModel(
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val mediaManager: MediaManager,
    private val deckRepository: DeckRepository? = null,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {
```

Change the initial state to seed picker mode and the default selection:
```kotlin
    private val _uiState = MutableStateFlow(
        CardFormUiState(
            isEdit = editingCardId != null,
            pickDeck = deckId == null,
            selectedDeckId = deckId,
        ),
    )
```

In `init { ... }`, after the existing `if (editingCardId != null) { ... }` block, add deck loading:
```kotlin
        if (deckId == null) {
            viewModelScope.launch {
                deckRepository?.observeDecks()?.collect { decks ->
                    _uiState.value = _uiState.value.copy(
                        decks = decks.map { DeckOption(it.id, it.name) },
                    )
                }
            }
        }
```

Add the action (place it next to `onFrontChange`/`onBackChange`):
```kotlin
    fun onSelectDeck(id: String) {
        _uiState.value = _uiState.value.copy(selectedDeckId = id)
    }
```

- [ ] **Step 5: Use the selected deck in `save()` and preserve it on reset**

In `save()`, the new-card branch currently calls `newCard(baseId, state.front, state.back, ...)` which reads the class field `deckId`. Capture the selected deck once at the top of `save()` and pass it through. Replace the `private fun newCard(...)` signature and its `Card(...)` so the deck comes from an explicit parameter:

Change `newCard` to take an explicit `deckId`:
```kotlin
    private fun newCard(
        id: String, deckId: String, front: String, back: String, isReverse: Boolean, pairId: String?,
        image: String?, imagePath: String?, audioName: String?, audioPath: String?,
    ): Card {
        val t = now()
        return Card(
            id = id, front = front, back = back, deckId = deckId,
            dateCreated = t, lastModified = t, fsrsDue = t, fsrsState = CardState.New.value,
            image = image, imagePath = imagePath, audioName = audioName, audioPath = audioPath,
            pairId = pairId, isReverse = isReverse,
        )
    }
```

In `save()`, inside the `else` (new-card) branch, read the selected deck and pass it into both `newCard(...)` calls (original and reverse). At the start of the `else` branch add:
```kotlin
                val targetDeckId = state.selectedDeckId ?: return@launch
```
Then update the two `newCard(...)` calls to pass `targetDeckId` as the new second argument, e.g.:
```kotlin
                cardRepository.upsert(
                    newCard(
                        baseId, targetDeckId, state.front, state.back, isReverse = false,
                        pairId = if (state.createReverse) baseId else null,
                        image = state.imageName, imagePath = state.imagePath,
                        audioName = state.audioName, audioPath = state.audioPath,
                    ),
                )
                if (state.createReverse) {
                    cardRepository.upsert(
                        newCard(
                            idGenerator(), targetDeckId, state.back, state.front,
                            isReverse = true, pairId = baseId,
                            image = null, imagePath = null, audioName = null, audioPath = null,
                        ),
                    )
                }
```

Change the reset at the end of the new-card branch to preserve deck context:
```kotlin
                _uiState.value = CardFormUiState(
                    isEdit = false,
                    savedTick = state.savedTick + 1,
                    pickDeck = state.pickDeck,
                    decks = state.decks,
                    selectedDeckId = state.selectedDeckId,
                )
```

(The edit branch — `existing != null` — is unchanged.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest"`
Expected: PASS — all existing fixed-deck tests plus the three new picker-mode tests are green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt \
        app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt
git commit -m "Add deck-picker mode to CardFormViewModel"
```

---

## Task 2: Wire nullable deckId through CardFormArgs + Koin

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

There is no JVM unit test for Koin wiring in this project; this task is verified by a compiling build (Task 3 also depends on it).

- [ ] **Step 1: Make `CardFormArgs.deckId` nullable**

In `AppModule.kt:77`, change:
```kotlin
data class CardFormArgs(val deckId: String? = null, val cardId: String? = null)
```

- [ ] **Step 2: Pass `deckRepository` into the VM**

In the `viewModel { params -> ... CardFormViewModel(...) }` block (around `AppModule.kt:229`), update the construction to pass the deck repository:
```kotlin
    viewModel { params ->
        val a = params.get<CardFormArgs>()
        CardFormViewModel(
            deckId = a.deckId,
            cardRepository = get(),
            mediaManager = get(),
            deckRepository = get(),
            editingCardId = a.cardId,
            logManager = get(),
        )
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Allow null deckId in CardFormArgs and inject DeckRepository"
```

---

## Task 3: Deck selector UI + Front autofocus

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt`

- [ ] **Step 1: Make `CardFormScreen.deckId` nullable and pass `onSelectDeck`**

Change the `CardFormScreen` signature's `deckId: String` to `deckId: String?`:
```kotlin
@Composable
fun CardFormScreen(
    deckId: String?,
    cardId: String?,
    onClose: () -> Unit,
    viewModel: CardFormViewModel = koinViewModel { parametersOf(CardFormArgs(deckId, cardId)) },
) {
```

In the `CardFormContent(...)` call inside `CardFormScreen`, add the deck-selection callback:
```kotlin
        onSelectDeck = viewModel::onSelectDeck,
```

- [ ] **Step 2: Add the new imports**

Add to the import block at the top of `CardFormScreen.kt`:
```kotlin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
```

- [ ] **Step 3: Add `onSelectDeck` param to `CardFormContent`**

In the `CardFormContent(...)` parameter list, add (after `onBackChange`):
```kotlin
    onSelectDeck: (String) -> Unit,
```

- [ ] **Step 4: Add the deck dropdown and Front autofocus**

Inside `CardFormContent`'s body, just before the `Scaffold(...)` call, add the focus requester and effects:
```kotlin
    val frontFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { frontFocus.requestFocus() } }
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) runCatching { frontFocus.requestFocus() }
    }
```

Inside the form `Column` (the one with `verticalArrangement = Arrangement.spacedBy(16.dp)`), add the deck dropdown as the **first** child, above the Front `OutlinedTextField`:
```kotlin
            if (state.pickDeck) {
                var deckMenuExpanded by remember { mutableStateOf(false) }
                val selectedDeckName =
                    state.decks.firstOrNull { it.id == state.selectedDeckId }?.name ?: ""
                ExposedDropdownMenuBox(
                    expanded = deckMenuExpanded,
                    onExpandedChange = { deckMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedDeckName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deck") },
                        placeholder = { Text("Select a deck") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deckMenuExpanded) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = deckMenuExpanded,
                        onDismissRequest = { deckMenuExpanded = false },
                    ) {
                        state.decks.forEach { deck ->
                            DropdownMenuItem(
                                text = { Text(deck.name) },
                                onClick = { onSelectDeck(deck.id); deckMenuExpanded = false },
                            )
                        }
                    }
                }
            }
```

Attach the focus requester to the **Front** `OutlinedTextField` by adding it to its modifier:
```kotlin
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Front") },
                placeholder = { Text("Question") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(frontFocus),
            )
```

- [ ] **Step 5: Update the previews**

Both existing previews call `CardFormContent(...)` without `onSelectDeck` — add `onSelectDeck = {}` to each. Then add two picker-mode previews after the existing ones:
```kotlin
@Preview(name = "Card form · pick deck (empty)", showBackground = true)
@Composable
private fun CardFormPickDeckPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                pickDeck = true,
                decks = listOf(DeckOption("d1", "French"), DeckOption("d2", "Spanish")),
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}

@Preview(name = "Card form · pick deck (selected)", showBackground = true)
@Composable
private fun CardFormPickDeckSelectedPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                front = "bonjour", back = "hello",
                pickDeck = true, selectedDeckId = "d2",
                decks = listOf(DeckOption("d1", "French"), DeckOption("d2", "Spanish")),
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}
```

(Remember to also add `onSelectDeck = {}` to the existing `CardFormNewPreview` and `CardFormRecordingPreview` calls.)

- [ ] **Step 6: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt
git commit -m "Add in-editor deck selector and Front autofocus"
```

---

## Task 4: Navigation route + "Add more cards" button

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/queue/StudyQueueScreen.kt`

- [ ] **Step 1: Thread `onAddCards` through `StudyQueueScreen` → `HeroCard`**

In `StudyQueueScreen.kt`, add `onAddCards: () -> Unit = {}` to the `StudyQueueScreen(...)` parameter list (after `onGoToLibrary`), and pass it into the `StudyQueueContent(...)` call:
```kotlin
        onAddCards = onAddCards,
```

Add `onAddCards: () -> Unit = {}` to the `StudyQueueContent(...)` parameter list (after `onGoToLibrary`). Find where `HeroCard(state, onStudyAll, onGoToLibrary)` is called (around line 180) and add the new arg:
```kotlin
            item { HeroCard(state, onStudyAll, onGoToLibrary, onAddCards) }
```

Update the `HeroCard` signature:
```kotlin
private fun HeroCard(
    state: StudyQueueUiState,
    onStudyAll: () -> Unit,
    onGoToLibrary: () -> Unit,
    onAddCards: () -> Unit,
) {
```

In the **all-caught-up** branch (the `else` branch with the "All caught up" text and the "Add more cards" `OutlinedButton`), change that button's `onClick` from `onGoToLibrary` to `onAddCards`:
```kotlin
                OutlinedButton(
                    onClick = onAddCards,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        "Add more cards",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
```

(The brand-new branch's "Go to Library" button keeps `onClick = onGoToLibrary` — do not change it.)

- [ ] **Step 2: Add the `cardForm` route and pass `onAddCards`**

In `AzriNavHost.kt`, in the `StudyQueueScreen(...)` call inside `composable(QUEUE) { ... }` (around line 177), add the new callback:
```kotlin
                StudyQueueScreen(
                    onStudyAll = { nav.navigate("studyAll") },
                    onStudyDeck = { nav.navigate("study/$it") },
                    onStudyFolder = { nav.navigate("studyFolder/$it") },
                    onGoToLibrary = { nav.switchTab(LIBRARY) },
                    onAddCards = { nav.navigate("cardForm") },
                    onOpenPaywall = { showPaywall = true },
                )
```

Add the no-arg `cardForm` route next to the existing `cardForm/{deckId}` route (around line 268):
```kotlin
            composable("cardForm") {
                CardFormScreen(
                    deckId = null,
                    cardId = null,
                    onClose = { nav.popBackStack() },
                )
            }
```

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt \
        app/src/main/java/nart/simpleanki/feature/queue/StudyQueueScreen.kt
git commit -m "Open card editor from the 'Add more cards' empty state"
```

---

## Final verification

- [ ] **Step 1: Full unit-test suite + debug build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 2 (optional, emulator): manual smoke test**

If an emulator is available: Study tab → reach the "All caught up" state → tap **Add more cards** → the editor opens with the keyboard up and Front focused; a **Deck** dropdown shows; Save is disabled until a deck is picked; after saving, the form clears, the deck stays selected, and Front re-focuses. Press back → reopening shows no deck pre-selected.
