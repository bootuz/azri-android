# Deck Detail "All Caught Up" State — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/session-complete-redesign` (continuing on the current branch, per request).

## Goal

On the deck-detail screen, when a deck has cards but **nothing is new or due to study right
now**, replace the Study button with an encouraging "You're all caught up!" message instead of
offering a button that opens an immediately-empty study session.

## Background: current behavior

`DeckDetailScreen.kt` renders, under a Total/Due/New stats card, a single Study button:

```kotlin
Button(
    onClick = onStudy,
    enabled = state.total > 0,
    ...
) { Icon(School); Text(if (state.dueCount > 0) "Study ${state.dueCount} due" else "Study") }
```

`DeckDetailUiState` exposes `dueCount` (non-New cards with `fsrsDue <= now`), `newCount` (New
cards), and `total` (all cards). The study queue is built from **new + due** cards. So when a
deck has cards but `newCount == 0 && dueCount == 0` ("all caught up"), the button is still
enabled, says "Study", and tapping it opens an empty session that immediately shows the
session-complete summary with 0 reviewed — an anticlimactic dead end.

## Decision (from brainstorming)

- When nothing is studyable, **show an encouraging message only** (no study action). The button
  is replaced, not merely relabeled.
- A future "review/cram mode" (study on demand without spaced repetition) is **out of scope**;
  when it lands, the caught-up state will gain a "Review anyway" action.

## The three header states

Define `studyable = state.newCount + state.dueCount` (the cards the study queue would serve right
now). The Total/Due/New stats card always renders; only the action slot beneath it changes:

1. `studyable > 0` → the **Study button**, unchanged ("Study N due" when `dueCount > 0`, else
   "Study"), enabled, calling `onStudy`.
2. `studyable == 0 && state.total > 0` → the new **`AllCaughtUp`** message (replaces the button).
3. `state.total == 0` → render nothing in the action slot. The list body already shows
   "No cards yet. Tap + to add one." This also removes today's pointless **disabled** Study
   button on empty decks.

## Components

### `AllCaughtUp` (new private composable in `DeckDetailScreen.kt`)

A centered block occupying the action slot:
- 🎉 emoji (or a check icon), modest size.
- **"You're all caught up!"** — `titleMedium`, semibold, `onSurface`.
- "Nothing to review in this deck right now." — `bodyMedium`, `onSurfaceVariant`, centered.
- An optional next-review line: `nextReviewLabel(cards, now)?.let { Text("Next review $it") }`
  (`bodyMedium`/`labelMedium`, `onSurfaceVariant`).

### `nextReviewLabel(cards, now): String?` (new pure helper in `DeckDetailScreen.kt`)

Returns the relative time until the soonest **future** review, or `null` when none exists:
- Consider only non-New cards (`CardState.fromValue(fsrsState) != New`) with `fsrsDue > now`.
- If none, return `null` (the line is omitted).
- Otherwise take the minimum `fsrsDue`, and return `"in ${IntervalFormatter.format(min - now)}"`
  (e.g. `"in 3d"`), reusing the existing `IntervalFormatter` already imported in this file.

In the caught-up state (`studyable == 0 && total > 0`) every card is a non-New review scheduled
in the future, so this normally yields a value; the `null` path is defensive.

### Wiring (`DeckDetailContent`)

Replace the single `Button(...)` block in the header `Column` with a `when`:

```kotlin
val studyable = state.newCount + state.dueCount
when {
    studyable > 0 -> Button(onClick = onStudy, ...) { /* existing Study button */ }
    state.total > 0 -> AllCaughtUp(cards = state.cards, now = now)
    else -> Unit  // empty deck: the list body shows "No cards yet."
}
```

No `onStudy` contract change; no `DeckDetailViewModel` / `DeckDetailUiState` change.

## Data flow

`DeckDetailViewModel` already computes `newCount`/`dueCount`/`cards` from the card repository →
`DeckDetailContent` derives `studyable` and routes to Study button / `AllCaughtUp` / nothing →
`AllCaughtUp` derives its next-review line from `cards` + `now` via `nextReviewLabel`.

## Error handling

Pure presentation over already-loaded state. `nextReviewLabel` returns `null` rather than
throwing when there is no future review (e.g. an all-New or empty card list), so the line is
simply omitted. No new I/O or failure modes.

## Testing

- **`nextReviewLabel(cards, now)`** (pure, JVM unit test):
  - `null` when the list is empty.
  - `null` when all cards are New (no scheduled reviews).
  - `null` when the only non-New cards are already due (`fsrsDue <= now`).
  - picks the **soonest** future due among several non-New cards (ignoring New and past-due ones).
- **`DeckDetailContent`** — `@Preview` for the caught-up deck (Total > 0, Due 0, New 0, future
  reviews) showing the message; existing studyable and empty previews stay. Build-verified.
- If `DeckDetailContentTest` (instrumented) exists, add an assertion that the caught-up state
  shows "You're all caught up!" and does not show the Study button; keep existing assertions
  green.

## Out of scope

The future review/cram mode ("study these cards anytime without spaced repetition"); any change
to the study queue, scheduling, or the empty-deck "No cards yet" body; changes to the Today/global
study screen (which already has its own all-caught-up CTA).
