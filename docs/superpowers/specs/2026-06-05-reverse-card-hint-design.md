# Card Editor: Reverse-Card Hint — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/card-editor-bottom-toolbar` (joins the open PR #18 — completes the reverse-toggle UX from that change).

## Goal

When the reverse-card toggle in the editor's bottom app bar is **on**, show a small persistent
inline hint in the form so the user knows a second (reversed) card will be created. Moving the
"Also create reverse card" control from a labeled `FilterChip` to an icon-only `IconToggleButton`
(PR #18) removed its text; this restores that meaning.

## Background

In `CardFormScreen.kt`, `CardFormContent` renders the form `Column` (deck dropdown, Front, Back,
optional image-preview block, optional audio block). The reverse toggle is an `IconToggleButton`
in the `BottomAppBar`, shown only for new cards (`!state.isEdit`), bound to `state.createReverse`
via `onToggleReverse`. `createReverse` already exists in `CardFormUiState` and is cleared on the
post-save reset. No state changes are needed.

## Decision

A **persistent inline caption** (chosen over a transient snackbar or a tooltip): clearest, shows the
ongoing state, and sits right above the bar where the toggle lives.

## Component

In `CardFormContent`, add as the **last child** of the form `Column` (after the audio block):

```kotlin
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

- Guarded by `!state.isEdit` for clarity (the toggle — and thus `createReverse == true` — only
  occurs on new cards anyway).
- All referenced symbols (`Row`, `Arrangement`, `Alignment`, `Icon`, `Icons.Default.SwapHoriz`,
  `Modifier.size`, `Text`, `MaterialTheme`) are already imported in the file — no new imports.

## Data flow

`onToggleReverse(true)` → `state.createReverse = true` → the caption composes; toggling off or the
post-save reset (`createReverse = false`) removes it. Unchanged elsewhere.

## Testing

- **`CardFormContentTest`** (androidTest; compile-verified, optionally run on the connected
  emulator): add a test that a new-card `CardFormContent` with `createReverse = true` displays the
  hint text (`onNodeWithText` substring "reverse card … will also be created" — assert the exact
  string used above), and that it is absent when `createReverse = false`.
- **Preview:** the existing `CardFormNewPreview` already sets `createReverse = true`, so it renders
  the hint with no new preview needed.

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope

- Any change to reverse-card creation logic, the toggle itself, state, or sync.
- A snackbar/tooltip variant (explicitly not chosen).
- Showing the hint in edit mode (reverse toggling is new-cards-only).
