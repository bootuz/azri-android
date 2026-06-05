# Card Editor: Move Actions into a Bottom App Bar — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/card-editor-bottom-toolbar` — **stacked on `feature/card-editor-deck-selector`
(PR #17, still open at design time)**, which adds the deck-picker mode to the same `CardFormScreen`.
Retarget this PR onto `main` once #17 merges.

## Goal

Relocate the card editor's three attachment/setting actions — **Add image**, **Record audio**, and
**Also create reverse card** — out of inline chips in the scrollable form and into a Material
**`BottomAppBar`** at the bottom of the editor. Promote **Save** to the bar's primary-action FAB. The
bar floats above the keyboard so the actions stay reachable while typing.

## Background: current state

In `CardFormScreen.kt`, the stateless `CardFormContent` renders, inside a scrolling `Column`:
- The deck dropdown (picker mode), Front/Back fields.
- An inline **image preview** (with a remove button) when `state.imageName != null`.
- An inline **"Audio attached"** row (with a remove button) when `state.audioName != null`.
- A `FlowRow` of `AssistChip`s: **"Add image"** (hidden once an image is attached;
  shows a spinner while `uploadingImage`) and **"Record audio"** (hidden once audio is attached;
  Mic / Stop-while-recording / spinner-while-uploading).
- A reverse `FilterChip` **"Also create reverse card"** (only when `!state.isEdit`).
- **Save** is a `TextButton` in the top app bar `actions`, gated by `state.canSave`.

`CardFormContent` already receives every callback we need: `onAddImage`, `onToggleRecording`
(plus `isRecording`), `onToggleReverse: (Boolean) -> Unit`, `onRemoveImage`, `onRemoveAudio`,
`onSave`, `onBack`, `onSelectDeck`. `MainActivity` calls `enableEdgeToEdge()`, so `Modifier.imePadding()`
correctly lifts content above the IME.

## Decisions (from brainstorming)

- **Use the stable `androidx.compose.material3` `BottomAppBar`** (a *docked* bottom app bar), NOT the
  M3 Expressive *floating* toolbar. The Compose `HorizontalFloatingToolbar` is only in a
  `1.5.0-alpha` line; our BOM (`2025.10.00`) pins material3 to **1.4.0**, which ships only the
  floating-toolbar tokens, not the composable. (MDC-Android `1.14.0`'s `FloatingToolbarLayout` is a
  *View*, irrelevant to this all-Compose screen.) `BottomAppBar` needs no dependency change.
- **All three actions move into the bar.** Reverse becomes an `IconToggleButton` (new cards only).
- **Save becomes the bar's `floatingActionButton`** (primary action, right side); removed from the
  top app bar.
- **The bar floats above the keyboard** via `Modifier.imePadding()`.
- **Image/audio icons stay conditional** — hidden once attached (mirroring today's chips); the
  inline preview/remove blocks remain the way to change/remove an attachment.

## Scope

**One production file: `CardFormScreen.kt`.** No ViewModel/state changes; `CardFormUiState` and the
`CardFormContent` parameter list are **unchanged** (every needed callback already exists). Plus an
update to the existing `CardFormContentTest` assertions.

## Components

### Top app bar (`CardFormContent`)
- **Remove** the `Save` `TextButton` from `actions`. The top bar keeps the title
  (`if (state.isEdit) "Edit card" else "New card"`) and the back `IconButton`. (The `actions = { … }`
  lambda becomes empty and is dropped.)

### Content `Column` (`CardFormContent`)
- **Remove** the `FlowRow` of `AssistChip`s ("Add image", "Record audio") and the reverse
  `FilterChip` ("Also create reverse card").
- **Keep unchanged:** the deck dropdown, Front/Back fields (incl. the Front `FocusRequester`
  autofocus), the inline image preview + remove, and the inline "Audio attached" row + remove.

### New `Scaffold(bottomBar = { … })`
Add a `bottomBar` to the existing `Scaffold` in `CardFormContent`:

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
                        state.uploadingAudio -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        isRecording -> Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = MaterialTheme.colorScheme.error)
                        else -> Icon(Icons.Default.Mic, contentDescription = "Record audio")
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
            // M3 FABs have no `enabled` flag: show the disabled state via muted colors + a gated click.
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
}
```

Behavior is preserved exactly: the image/audio icons appear/hide and reflect uploading/recording
state as the chips do today; Save remains gated by `canSave` (now via the FAB's muted-disabled
treatment). The "Card saved" snackbar and the post-save reset are unchanged — after a new-card save,
`imageName`/`audioName`/`createReverse` clear, so the icons reappear automatically.

### Imports to add (`CardFormScreen.kt`)
`androidx.compose.material3.BottomAppBar`, `androidx.compose.material3.FloatingActionButton`,
`androidx.compose.material3.IconToggleButton`, `androidx.compose.foundation.layout.imePadding`.
(`size`, `IconButton`, `CircularProgressIndicator`, and the `Icons.Default.*` icons are already
imported — used by the existing chips/remove buttons — so they need no new import.) Remove
now-unused imports (`AssistChip`, `FilterChip`, `TextButton`, `FlowRow`, and the
`ExperimentalLayoutApi` opt-in on `CardFormContent`) **only if** no longer referenced anywhere in
the file — verify before deleting.

## Data flow

Unchanged. The bar's buttons invoke the same callbacks the chips did
(`onAddImage`/`onToggleRecording`/`onToggleReverse`/`onSave`); state still flows from
`CardFormViewModel` through `CardFormUiState`.

## Error handling

- Save FAB is a no-op while `!canSave` (gated `onClick`) and is visibly muted — no invalid save.
- `imePadding()` relies on the already-enabled edge-to-edge setup; if the IME inset weren't
  available the bar would simply dock at the bottom (graceful, no crash).
- Uploading/recording disabled states carry over from the chips (`enabled = !uploading…`).

## Testing

- **`CardFormContentTest`** (androidTest, compile-verified — emulator unavailable): the two existing
  tests assert on chip **text** (`onNodeWithText("Add image")`, `onNodeWithText("Also create reverse
  card")`). These become icon buttons, so update those assertions to
  `onNodeWithContentDescription("Add image")` / `onNodeWithContentDescription("Also create reverse
  card")` (exists / does-not-exist in edit mode). `CardFormContent`'s signature is unchanged, so no
  other call sites change.
- **Previews:** existing previews keep compiling (same signature) and now render the bottom bar. Add
  one `@Preview` exercising the bar with an attached image + audio (so the inline remove blocks show
  and the corresponding bar icons are hidden) to visually verify the conditional logic.
- **Manual (if emulator available):** open editor → bar shows Add image / Record audio / reverse
  toggle + Save FAB, floating above the keyboard; Save muted until valid; attach image → image icon
  hides, inline preview+remove appears; record audio → Stop icon (error tint) while recording.

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope

- The M3 Expressive *floating* toolbar / any `androidx.compose.material3` alpha bump.
- MDC-Android `FloatingToolbarLayout` (View) interop.
- Any change to attach/record/save logic, deck-picker mode, reverse-card creation, or sync.
- Changing how attachments are removed (the inline preview/remove blocks stay as-is).
