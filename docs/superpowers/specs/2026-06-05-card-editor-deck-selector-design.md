# Card Editor: In-Editor Deck Selector + Autofocus — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/card-editor-deck-selector` (off `main`).

## Goal

Let users add cards straight from the Study tab without detouring through the Library. The
empty-state **"Add more cards"** button opens the card editor directly; that editor gains a **deck
selector** so the user chooses the destination deck in place. The Front field is **autofocused** on
open and re-focused after each save, so the user can type immediately. On a new-card save the form
clears but the selected deck is preserved; leaving the editor resets the selection.

## Background: current state

- The card editor (`CardFormScreen` / `CardFormViewModel`) takes a **fixed** `deckId` supplied at
  construction via `CardFormArgs`, routed through `cardForm/{deckId}` (add) and
  `cardForm/{deckId}/{cardId}` (edit). There is no deck selector and no autofocus.
- On a **new-card** save the VM already keeps the editor open for rapid entry: it replaces state
  with a fresh `CardFormUiState(savedTick = savedTick + 1)` (clearing inputs) and re-fires a "Card
  saved" snackbar. Editing an existing card instead sets `finished = true` and closes.
- `StudyQueueScreen`'s empty `HeroCard` has **two** buttons, both calling `onGoToLibrary`:
  - Brand-new user (no decks/cards, `!hasWork && !hasAnyCards`): **"Go to Library"**.
  - All-caught-up (`!hasWork` but has cards): **"Add more cards"**.
- `DeckRepository.observeDecks(): Flow<List<Deck>>` already exposes a live deck list.
- An existing dropdown idiom lives in `ApkgImportScreen.FieldDropdown` /
  `CsvImportScreen`: `ExposedDropdownMenuBox` + read-only `OutlinedTextField` with
  `Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)` + `DropdownMenuItem` per option. We
  mirror it. (Material3 from `composeBom = 2025.10.00`.)

## Decisions (from brainstorming)

- **Deck selector appears on the queue path only.** Entering the editor from a deck
  (`deck-detail → Add card`, the `cardForm/{deckId}` route) keeps its fixed deck and shows **no**
  selector — current behavior unchanged. The selector appears only when the editor is opened with
  no deck context (the new `cardForm` route).
- **No pre-selection.** Picker mode starts with no deck chosen; the user must pick a deck before the
  first save. (Front still autofocuses so they can type the card while the deck is unpicked.)
- **Brand-new empty state stays on Library.** Only the "Add more cards" button is repointed at the
  editor; "Go to Library" is unchanged, because a brand-new user has no deck to add a card to.

## Components

### 1. Navigation (`ui/navigation/AzriNavHost.kt`)

- Add a **new no-arg route** `"cardForm"` (distinct pattern from `cardForm/{deckId}`):
  ```kotlin
  composable("cardForm") {
      CardFormScreen(deckId = null, cardId = null, onClose = { nav.popBackStack() })
  }
  ```
- In the `StudyQueueScreen(...)` call, add `onAddCards = { nav.navigate("cardForm") }`.

`CardFormScreen`'s `deckId` parameter becomes **nullable** (`deckId: String?`) and is forwarded into
`CardFormArgs`.

### 2. Empty state button (`feature/queue/StudyQueueScreen.kt`)

- `StudyQueueScreen` gains `onAddCards: () -> Unit = {}`; pass it through to `StudyQueueContent`
  and into `HeroCard`.
- `HeroCard` gains an `onAddCards: () -> Unit` parameter. The **all-caught-up** branch's
  "Add more cards" `OutlinedButton` switches its `onClick` from `onGoToLibrary` to `onAddCards`.
  The brand-new branch's "Go to Library" button keeps `onGoToLibrary`.
- `StudyQueueContent`'s signature adds `onAddCards: () -> Unit = {}` and forwards it to `HeroCard`.

### 3. `CardFormArgs` + Koin (`di/AppModule.kt`)

- `data class CardFormArgs(val deckId: String?, val cardId: String? = null)` — `deckId` now
  nullable.
- The Koin `viewModel { params -> ... }` block passes `deckRepository = get()`:
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

### 4. `CardFormViewModel` (`feature/cardform/CardFormViewModel.kt`)

New lightweight option type (same file):
```kotlin
data class DeckOption(val id: String, val name: String)
```

`CardFormUiState` gains three fields:
```kotlin
val pickDeck: Boolean = false,          // true ⇒ show the deck selector (queue path)
val decks: List<DeckOption> = emptyList(),
val selectedDeckId: String? = null,
```
`canSave` adds a deck requirement:
```kotlin
val canSave: Boolean
    get() = front.isNotBlank() && back.isNotBlank() &&
        !uploadingImage && !uploadingAudio && selectedDeckId != null
```
(In fixed-deck mode `selectedDeckId` is non-null from the start, so behavior is unchanged.)

Constructor: `deckId` becomes nullable, and a `deckRepository: DeckRepository? = null` is added
(only consulted in picker mode; defaulted to `null` so the existing fixed-deck call sites and tests
need no change — Koin always supplies a real instance):
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

Initial state and init:
```kotlin
private val _uiState = MutableStateFlow(
    CardFormUiState(
        isEdit = editingCardId != null,
        pickDeck = deckId == null,
        selectedDeckId = deckId,
    ),
)
```
```kotlin
init {
    if (editingCardId != null) { /* existing async card load, unchanged */ }
    if (deckId == null) {
        viewModelScope.launch {
            deckRepository?.observeDecks()?.collect { decks ->
                _uiState.value = _uiState.value.copy(
                    decks = decks.map { DeckOption(it.id, it.name) },
                )
            }
        }
    }
}
```

New action:
```kotlin
fun onSelectDeck(id: String) {
    _uiState.value = _uiState.value.copy(selectedDeckId = id)
}
```

`newCard` uses the selected deck (guaranteed non-null because `canSave` gates `save()`):
```kotlin
private fun newCard(...): Card {
    val t = now()
    return Card(
        id = id, front = front, back = back, deckId = _uiState.value.selectedDeckId!!,
        ...
    )
}
```
(Equivalently capture `state.selectedDeckId!!` into a local at the top of `save()` and thread it
through; pick whichever keeps `newCard` clean. The point: persist to the selected deck, not the
constructor arg.)

New-card reset (in `save()`) preserves the deck context:
```kotlin
_uiState.value = CardFormUiState(
    isEdit = false,
    savedTick = state.savedTick + 1,
    pickDeck = state.pickDeck,
    decks = state.decks,
    selectedDeckId = state.selectedDeckId,
)
```

**Reset on dismiss** needs no code: pressing back pops the `cardForm` route, the VM is cleared, and
the next open recreates it with `selectedDeckId = null`.

### 5. Deck selector UI + autofocus (`feature/cardform/CardFormScreen.kt`)

`CardFormContent` gains one new callback param `onSelectDeck: (String) -> Unit` (the deck list and
selection are read from `state`).

**Deck dropdown** — rendered as the **first** item in the form `Column` (above the Front field),
only when `state.pickDeck`:
```kotlin
if (state.pickDeck) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.decks.firstOrNull { it.id == state.selectedDeckId }?.name ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Deck") },
            placeholder = { Text("Select a deck") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.decks.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    onClick = { onSelectDeck(deck.id); expanded = false },
                )
            }
        }
    }
}
```

**Autofocus** — a `FocusRequester` on the Front `OutlinedTextField`:
```kotlin
val frontFocus = remember { FocusRequester() }
LaunchedEffect(Unit) { runCatching { frontFocus.requestFocus() } }
LaunchedEffect(state.savedTick) { if (state.savedTick > 0) runCatching { frontFocus.requestFocus() } }
```
Attach `.focusRequester(frontFocus)` to the Front field's `Modifier`. (`runCatching` guards the rare
"FocusRequester not attached yet" race so it can never crash; a missed focus is harmless.) These
effects live in `CardFormContent`; the existing snackbar `LaunchedEffect(state.savedTick)` stays in
`CardFormScreen` — both keyed on `savedTick`, independent.

`CardFormScreen` forwards `onSelectDeck = viewModel::onSelectDeck` into `CardFormContent`.

## Data flow

Study tab, all caught up → tap **"Add more cards"** → `nav.navigate("cardForm")` →
`CardFormScreen(deckId = null)` → VM enters picker mode, observes decks, Front autofocuses → user
types Front/Back, picks a deck (Save enabled) → save persists the card to the selected deck →
form clears, deck stays, Front re-focuses for the next card → back pops the route, selection resets.

## Error handling

- Picker mode with an empty deck list can't happen via this entry: "Add more cards" only shows when
  the user already has cards (hence ≥1 deck). If the list is somehow empty, Save stays disabled
  (no `selectedDeckId`) — safe, no crash.
- `frontFocus.requestFocus()` is wrapped in `runCatching` so a not-yet-attached requester is a
  no-op rather than a crash.
- Fixed-deck mode is untouched: `selectedDeckId` is non-null from construction, the selector never
  renders, and `deckRepository` is never consulted.

## Testing

- **`CardFormViewModelTest` (JVM, `FakeCardDao` + `FakeDeckDao`):**
  - Picker mode (`deckId = null`): seeded decks appear in `state.decks` as `DeckOption`s;
    `pickDeck == true`; `canSave` is false with Front+Back filled but no deck; selecting a deck
    flips `canSave` true; `save()` persists the card to the selected deck.
  - After a picker-mode save: `front`/`back` cleared, `savedTick` bumped, and `pickDeck`/`decks`/
    `selectedDeckId` preserved.
  - Fixed-deck regression: existing tests (constructing `CardFormViewModel("d1", repo, media(), …)`)
    stay green untouched — `selectedDeckId` defaults to the arg, `pickDeck == false`, `canSave`
    needs no manual pick.
- **Display:** `@Preview`s for picker mode — (a) no deck selected (Save disabled, placeholder
  "Select a deck"), (b) a deck selected. Compose screens are compile/preview-verified; the emulator
  is unavailable.

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope

- Showing the deck selector in fixed-deck mode (deck-detail "Add card") — intentionally unchanged.
- Remembering a default/last-used deck across sessions (decided: no pre-selection).
- Repointing the brand-new "Go to Library" button at the editor / a no-decks empty state inside the
  editor.
- Any change to edit-mode behavior, reverse-card creation, media attachment, or sync.
