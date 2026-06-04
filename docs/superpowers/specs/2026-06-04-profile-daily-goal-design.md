# Daily Goal in Profile Settings — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/profile-daily-goal` (fresh branch off `main`; independent of PR #9).

## Goal

Make the daily-goal editor reachable from the Profile → Settings section, in addition to its
existing entry point on the Study Queue screen. Tapping a new "Daily goal" row opens the **same**
editor sheet that the Queue uses.

## Background: what already exists

- `feature/queue/DailyGoalSheet.kt` — `DailyGoalEditorSheet(onDismiss, viewModel = koinViewModel())`,
  a `ModalBottomSheet` wrapping the stateless `DailyGoalEditorContent` (goal-tracking toggle + two
  steppers for new/review targets + reset). Currently opened from the Queue's goal card.
- `feature/queue/DailyGoalViewModel.kt` — reads/writes `SettingsRepository`
  (`dailyGoalEnabled`, `newCardsTarget`, `reviewCardsTarget`).
- `SettingsRepository.kt` — `AppSettings.dailyGoalEnabled: Boolean`, and an existing extension
  `val AppSettings.dailyGoalTotal: Int get() = newCardsTarget + reviewCardsTarget`.
- `feature/profile/ProfileScreen.kt` — `ProfileScreen` (stateful, Koin) + `ProfileContent`
  (stateless, testable). The "Settings" `CategoryHeader` currently holds "Spaced repetition"
  (→ `onOpenFsrsSettings`) and "Notifications" (→ `onOpenNotifications`) rows, each a clickable
  `ListItem` with live supporting text.
- `feature/profile/ProfileViewModel.kt` — already `combine`s `settingsRepository.settings` (it
  surfaces `preset` and `themeMode`).

## Decision

Reuse the existing `DailyGoalEditorSheet` as a modal opened from a new Profile row — **no new
editor**, one source of truth. (Out of scope: a separate full-screen daily-goal editor.)

## Components

### `ProfileViewModel` / `ProfileUiState` (modify)

Surface the current goal so the row can show live supporting text. Add two fields to
`ProfileUiState`:

```kotlin
val dailyGoalEnabled: Boolean = false,
val dailyGoalTotal: Int = 0,
```

Populate them in the existing `combine` mapping from `settings`:

```kotlin
dailyGoalEnabled = settings.dailyGoalEnabled,
dailyGoalTotal = settings.dailyGoalTotal,   // existing extension: new + review targets
```

No new dependencies; `settings` is already a flow in the `combine`.

### `ProfileContent` (modify)

Add a "Daily goal" `ListItem` to the **Settings** section, placed between "Spaced repetition"
and "Notifications":

- `leadingContent`: `Icon(Icons.Filled.Flag, contentDescription = null)`.
- `headlineContent`: `Text("Daily goal")`.
- `supportingContent`: live —
  `Text(if (state.dailyGoalEnabled) "${state.dailyGoalTotal} cards/day" else "Off")`.
- `colors`: `ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)`
  (same as the sibling rows).
- `modifier = Modifier.clickable(onClick = onOpenDailyGoal)`.

Add a new parameter to `ProfileContent`: `onOpenDailyGoal: () -> Unit = {}` (defaulted, so the
existing previews and tests compile unchanged).

### `ProfileScreen` (modify)

Host the sheet (keeps DI out of the stateless `ProfileContent`):

```kotlin
var showDailyGoalSheet by remember { mutableStateOf(false) }
...
ProfileContent(
    ...,
    onOpenDailyGoal = { showDailyGoalSheet = true },
)
if (showDailyGoalSheet) {
    DailyGoalEditorSheet(onDismiss = { showDailyGoalSheet = false })
}
```

`DailyGoalEditorSheet` is imported from `nart.simpleanki.feature.queue` (same module, public).
Its `DailyGoalViewModel` is resolved via Koin inside the sheet — no wiring needed here.

## Data flow

`SettingsRepository.settings` → `ProfileViewModel.combine` → `ProfileUiState`
(`dailyGoalEnabled`, `dailyGoalTotal`) → the "Daily goal" row's supporting text. Tapping the row →
`onOpenDailyGoal` → `ProfileScreen` shows `DailyGoalEditorSheet` → edits flow through
`DailyGoalViewModel` → `SettingsRepository`, which feeds back into both the sheet and the Profile
row's live supporting text.

## Error handling

Pure presentation + an existing, self-contained editor. No new I/O or failure modes. The sheet
already handles its own persistence; dismissing simply hides it.

## Testing

- **`ProfileViewModel`** — if `ProfileViewModelTest` exists, add a test that `uiState` reflects
  `settings.dailyGoalEnabled` and `settings.dailyGoalTotal` (new + review). Otherwise the change
  is a one-line `combine` mapping covered by build verification.
- **`ProfileContent`** — add a `@Preview` variant with `dailyGoalEnabled = true,
  dailyGoalTotal = 30` showing the row's "30 cards/day" supporting text; existing previews stay
  (they render "Off"). If `ProfileContentTest` (instrumented) exists, add an assertion that
  "Daily goal" is displayed and clicking it invokes `onOpenDailyGoal`.
- The `DailyGoalEditorSheet`/`DailyGoalViewModel` are unchanged, so their existing coverage holds.

## Out of scope

A separate full-screen daily-goal editor; changing the editor's contents, steppers, defaults, or
the Queue entry point; notifications wiring; any change to how the goal affects the study queue
(`StudyQueueBuilder` already treats it as a soft goal).
