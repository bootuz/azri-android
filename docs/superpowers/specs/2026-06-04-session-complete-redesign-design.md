# Session-Complete UI Redesign — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Ports:** the iOS `SimpleAnkiSwiftUI/.../Core/SpacedRepetition/SessionSummaryView.swift` (+ its
`Components/SessionStatsRow`, `Components/RatingDistributionBar`, and the `SessionStatistics`
model helpers).

## Goal

Replace the bare Android study "session complete" screen (a title, a "N cards reviewed" line, a
plain text list of rating counts, and a Done button) with a polished summary matching iOS: a 🎉
header with a spring pop-in, a motivational message keyed to accuracy, a three-stat row
(Reviewed / Accuracy / Duration), a proportional rating-distribution bar with legend, and a
full-width Finish button — with a success haptic on appear.

## Background: what iOS does

`SessionSummaryView` (top → bottom): 🎉 (64pt, scale+fade spring on appear) → "Session Complete"
(bold) → motivational message (accuracy-keyed) → `SessionStatsRow` (Reviewed / Accuracy /
Duration, divided) → `StreakBadge` (only if streak > 0) → `RatingDistributionBar` (only if
reviewed > 0) → "Finish" button. `onAppear` runs a spring animation + a success notification
haptic. `SessionStatistics` provides `accuracy` = (good+easy)/total×100, `accuracyColor`
(≥80 mint, ≥60 indigo, ≥40 pink, else orange), `motivationalMessage`, and `formattedDuration`
(abbreviated minutes/seconds, drop leading zero).

## Decisions (from brainstorming)

- **Match iOS** layout and content.
- **Omit the streak badge for v1** — Android has no streak system anywhere in the codebase, so
  there is no data to show. A real streak feature is separate, future work.
- **Add session duration** — requires a small `StudyViewModel` change to record the session
  start time (uses the already-injected `now: () -> Long`, so it stays deterministic in tests).
- **Include the entrance animation (🎉 pop-in) and a haptic** for parity. Compose's
  `HapticFeedback` lacks an iOS-style "success notification"; use the closest available
  (`HapticFeedbackType.LongPress`) as a light approximation.
- **Use the app's standard full-width button** (not iOS's glass-gradient style).

## Components

### `feature/study/SessionStats.kt` (new, pure, JVM-testable — no Compose imports)

```kotlin
/** (good + easy) / total × 100, rounded to Int. 0 when there are no reviews. */
fun sessionAccuracy(ratingCounts: Map<Rating, Int>): Int

/** Accuracy-keyed encouragement, mirroring iOS thresholds. */
fun motivationalMessage(accuracy: Int): String  // >=90 / >=70 / >=50 / else

/** Abbreviated duration: "42s", "5m 12s", "5m". Drops a leading 0m; 0 -> "0s". */
fun formattedDuration(millis: Long): String
```

`motivationalMessage`:
- `>= 90` → "Outstanding session!"
- `>= 70` → "Great work, keep it up!"
- `>= 50` → "Solid effort, you're improving!"
- else → "Every review makes you stronger!"

### `feature/study/SessionSummary.kt` (new Compose file)

`@Composable fun SessionSummary(state: StudyUiState, onDone: () -> Unit)` plus private
`SessionStatsRow`, `RatingDistributionBar`, and the UI-side `accuracyColor(accuracy): Color`
helper. Removed from `StudyScreen.kt`, which now just calls this. Mirrors iOS's `Components/`
split and keeps `StudyScreen.kt` focused.

Layout (vertically centered, Finish pinned at the bottom via weight spacers):
- **🎉** `Text` at 64sp. On first composition a `LaunchedEffect` sets `appeared = true`, driving
  `animateFloatAsState` for scale (0.5 → 1, spring `dampingRatio = 0.7f, stiffness` medium) and
  alpha (0 → 1). The same `LaunchedEffect` fires
  `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`.
- **"Session Complete"** — `titleLarge`, bold.
- **Motivational message** — `bodyMedium`, `onSurfaceVariant`. `motivationalMessage(accuracy)`.
- **`SessionStatsRow`** — a `Row` of three items separated by vertical `Divider`s (`height(44.dp)`).
  Each item: a `Column` of [`Row` of icon + value(`titleMedium`, semibold)] over a
  caption label (`bodySmall`, `onSurfaceVariant`):
  - **Reviewed** — `Icons.Outlined.CheckCircle`, green `#34C759`, value `state.completed`.
  - **Accuracy** — `Icons.Outlined.TrackChanges`, `accuracyColor(accuracy)`, value `"$accuracy%"`.
  - **Duration** — `Icons.Outlined.Schedule`, orange `#FF9500`, value
    `formattedDuration(state.durationMillis)`.
- **`RatingDistributionBar`** (only if `state.completed > 0`):
  - A `Row` of height 12dp, clipped to a fully-rounded shape (`RoundedCornerShape(50)`), with one
    `Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))` per rating whose
    `count > 0` (Again `#FF2D55`, Hard `#FF9500`, Good `#5856D6`, Easy `#00C7BE`).
  - A legend `Row` (one item per rating regardless of count): a 6dp colored `Box`/dot +
    `"Again N"` etc., `bodySmall`, `onSurfaceVariant`.
- **Finish** button — full width, `height(60.dp)`, calls `onDone`.

`accuracyColor(accuracy)`: `>= 80` mint `#00C7BE` · `>= 60` indigo `#5856D6` · `>= 40` pink
`#FF2D55` · else orange `#FF9500`.

### `feature/study/StudyViewModel.kt` + `StudyUiState` (modify)

- Add a private `sessionStartMillis: Long`, set to `now()` at the start of `load()`.
- Add `val durationMillis: Long = 0` to `StudyUiState`.
- Set `durationMillis = now() - sessionStartMillis` at the moment the session finishes:
  - in `onRate()` when `next == null` (the rate-to-empty path), and
  - in `load()` when the queue is empty (the nothing-to-study path).
- `reviewedCards` is the existing `completed`; `ratingCounts` is the existing map. No other VM
  changes; analytics events and the flip-hint behavior are untouched.

## Data flow

Study session runs → on finish, `StudyViewModel` stamps `durationMillis` and sets
`finished = true` → `StudyContent` routes to `SessionSummary(state, onDone)` →
`SessionSummary` derives `accuracy = sessionAccuracy(state.ratingCounts)`, picks
`motivationalMessage` + `accuracyColor`, renders the stats row, the distribution bar (if any
reviews), and Finish → tapping Finish calls `onDone` (existing navigation back).

## Error handling

Pure presentation. Zero-review edge (`completed == 0`, empty `ratingCounts`): accuracy → 0,
message → "Every review makes you stronger!", the distribution bar is hidden, duration still
renders. No new I/O or failure modes.

## Testing

- **`SessionStatsTest`** (JVM): `sessionAccuracy` (typical mix; all-easy → 100; 0 reviews → 0);
  all four `motivationalMessage` thresholds (incl. boundaries 90/70/50); `formattedDuration`
  (seconds-only "42s", minutes+seconds "5m 12s", exact minute "5m", zero "0s").
- **`StudyViewModelTest`**: finishing a session (rate the last card) stamps `durationMillis` to
  the elapsed time with a controlled `now()`; the empty-queue load path also stamps it; existing
  `completed` / `ratingCounts` assertions still hold.
- **`SessionSummary` + subcomponents**: build-verified + `@Preview`s mirroring iOS's three
  (good session, low accuracy, all-easy). No Compose UI unit tests (codebase convention).

## Out of scope (this project)

A streak system / `StreakBadge`; per-card timing or average review time; milestone celebrations;
persisting session history/statistics; the iOS glass-gradient button style; changing the study
flow or scheduling.
