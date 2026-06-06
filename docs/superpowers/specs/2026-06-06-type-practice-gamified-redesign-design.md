# Type Practice — Gamified Redesign — Design

**Date:** 2026-06-06
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/type-practice-mode` (extends the Type Practice feature on this branch).
**Parent specs:** `2026-06-05-type-in-the-answer-design.md`, `2026-06-06-type-practice-answer-diff-design.md`.

## Goal

Redesign the in-session Type Practice screen into a focused, "Duolingo-style" gamified layout: a
top progress bar + live combo chip, a prompt hero card pinned up top, a bottom-anchored answer zone,
a mint **success flash** on correct answers, and per-card motion. It fixes the current screen's
dead-space/balance problem and adds lightweight game feel, while staying fully **FSRS-decoupled** and
reusing the app's existing rating-color palette.

## Background (current state this builds on)

- **`feature/typepractice/TypePracticeScreen.kt`**: today a single top-aligned `Column` — "PROMPT"
  label, prompt text, audio button, an `OutlinedTextField`, a "Check" button, "Don't know" — leaving
  the bottom ~half of the screen empty. The wrong-answer `RevealPanel` shows the char-diff (mint/pink
  per this redesign) + Continue + "I was right". A direction chooser precedes card 1; a session
  report follows.
- **`TypePracticeViewModel` / `TypePracticeUiState`**: drives the pure `TypePracticeSession`; on a
  **correct** submit it currently calls `renderAdvance()` immediately. `UiState` has `current`,
  `input`, `revealing`, `revealedAnswer`, `lastTyped`, `canOverride`, `remaining`, `finished`,
  `report`, `cardTick`, `awaitingDirection`, `direction`.
- **`TypePracticeSession`**: tracks `combo` (consecutive first-try correct, resets to 0 on a wrong
  submit) and `bestCombo`, but only `bestCombo` surfaces (via `report()`); `remaining` is exposed.
- **Rating colors** (currently inline literals in `StudyScreen.kt`, iOS-derived): Again =
  `0xFFFF2D55` (pink), Hard = `0xFFFF9500` (orange), Good = `0xFF5856D6` (indigo), Easy =
  `0xFF00C7BE` (mint).

## Decisions (from brainstorming, incl. the visual companion)

- **Layout = "split zones"** (prompt pinned upper, answer pinned to the bottom thumb-rail).
- **Reward chip = live combo** (🔥 N), reusing the session's `combo`; resets to 0 on a miss.
- **Correct-answer feedback = "quick flash, auto-advance"**: mint glow + ✓ on the card, the input
  shown in mint, the combo pops +1, then **auto-advance after ~400ms** (no tap).
- **Colors reuse the rating palette, no new token**: **correct/success = mint `0xFF00C7BE`** (Easy),
  **wrong/incorrect = pink `0xFFFF2D55`** (Again, replacing the diff's generic `error` color). The
  🔥 combo flame stays amber. Extract the 4 rating colors into a shared `RatingColors`.
- **Per-card motion = slide-in + fade** (~250ms); reduced-motion respected via the system animation
  scale (Compose honors it automatically).
- **Scope = in-session states only** (prompt + reveal). Chooser & report keep their layout.

## Components

### 1. `ui/theme/RatingColors.kt` (new — DRY the palette)

A single source of truth for the iOS-derived rating colors:
```kotlin
object RatingColors {
    val Again = Color(0xFFFF2D55)   // wrong / incorrect
    val Hard = Color(0xFFFF9500)
    val Good = Color(0xFF5856D6)
    val Easy = Color(0xFF00C7BE)    // correct / success
}
```
`StudyScreen.kt`'s `RatingButton` literals are replaced with `RatingColors.*`. Type Practice uses
`RatingColors.Easy` (success) and `RatingColors.Again` (wrong/diff).

### 2. `core/domain/typing/TypePracticeSession.kt` (small)

Expose the **live combo** for the chip (currently only `bestCombo` surfaces):
```kotlin
val currentCombo: Int get() = combo
```
No behavior change — `combo` is already maintained (incremented on first-try correct, zeroed on any
wrong submit).

### 3. `feature/typepractice/TypePracticeViewModel.kt` + `TypePracticeUiState`

Add to `TypePracticeUiState`:
- `combo: Int = 0` — the live combo for the chip.
- `total: Int = 0` — the session pool size, for the progress bar.
- `celebrating: Boolean = false` — true during the ~400ms mint success flash.

`startSession(...)`: record `total = pool.size`; seed `combo`/`total` into the first render.

`renderAdvance()`: also set `combo = session.currentCombo`, `total = total`, `celebrating = false`.
Progress is derived in the UI as `if (total > 0) (total - remaining).toFloat() / total else 0f`.

**Correct submit — the celebrating phase** (the one real behavior change): `session.submit` clears
the card immediately, so `onSubmit` captures `session.current` **before** calling `submit`, then on
`Correct` shows the celebrating state, waits, and advances:
```kotlin
fun onSubmit() {
    if (_uiState.value.celebrating) return          // ignore taps during the flash
    val typed = _uiState.value.input
    val answered = session.current                  // capture BEFORE submit advances the queue
    when (val r = session.submit(typed)) {
        SubmitResult.Correct -> {
            logManager.track(Event.Answered(true))
            _uiState.value = _uiState.value.copy(
                celebrating = true,
                current = answered,                 // keep showing the just-answered card
                input = typed,                      // shown in mint, disabled
                combo = session.currentCombo,       // popped +1
                revealing = false,
            )
            viewModelScope.launch {
                delay(CELEBRATE_MS)                 // ~400ms
                renderAdvance()                     // shows session.current (next) or finishes
                if (session.isFinished) logComplete()
            }
        }
        is SubmitResult.Wrong -> { /* unchanged: reveal, combo already 0 in session */ }
    }
}
```
`CELEBRATE_MS` is a tunable constant (~400). `onInput`/`onSubmit`/`onDontKnow` early-return while
`celebrating` (mirroring the existing `revealing` / `isInitialized` guards). Wrong / `onContinue` / `onOverride` / `onDontKnow`
paths are unchanged except they also refresh `combo` (which the session has zeroed on a wrong
first-try). `now`/dispatcher stays injectable so tests drive virtual time.

### 4. `feature/typepractice/TypePracticeScreen.kt` (major restructure, presentation only)

- **Top bar:** `✕` close (left) · a thin **progress bar** (`LinearProgressIndicator`, animated fill,
  mint while celebrating else primary) · a **combo chip** (right): muted at 0 (space reserved, no
  layout shift), amber 🔥 N with a scale-pop on increment at ≥1.
- **Prompt hero card** (upper): a rounded surface card with the **direction pill** ("TYPE THE BACK"
  / "TYPE THE FRONT" from `state.direction`), the **large prompt text** (non-typed side), and a
  **big circular audio button** when the card has audio (both directions), reusing `AudioPlayButton`;
  the front image shows on the prompt only in `TypeBack`. While `celebrating`: mint border/glow + a
  ✓ badge.
- **Bottom-anchored answer zone:** the `OutlinedTextField` (auto-focused on `cardTick`; mint + the
  typed text + disabled while celebrating), the primary **Check** (enabled on non-blank, hidden
  during celebrate/reveal), and **Don't know**. When `state.revealing`: the existing `RevealPanel`
  (char-diff now in `RatingColors.Again`/mint + Continue + "I was right").
- **Per-card transition:** wrap the prompt card in `AnimatedContent` keyed on `state.cardTick`
  (slide-in + fade); Compose honors the system animation scale, so reduced-motion is respected.
- **Previews:** prompt (TypeBack + TypeFront), celebrating, reveal, report, direction chooser.

## Data flow

Correct submit → VM captures the answered card → `celebrating` UiState (mint glow, ✓, combo +1,
progress unchanged until advance) → `delay(~400ms)` → `renderAdvance()` shows the next card (slide-in)
or the report; progress fills by `(total − remaining)/total`. Wrong submit → reveal (pink diff) in
the bottom zone, combo chip shows 0. Combo and progress are read from the session on every render.

## Error handling / edge cases

- **Last card correct:** after the celebrate delay, `session.isFinished` → `renderAdvance()` shows
  the report and `logComplete()` fires (once, after the delay).
- **Close (✕) mid-celebrate:** `onDone` pops back; the pending `delay` coroutine is cancelled with
  `viewModelScope`. No double advance.
- **Input gating:** `onSubmit`/`onInput`/`onDontKnow` no-op while `celebrating` (mirrors the existing
  `revealing`/`isInitialized` guards), so a double-submit during the flash can't skip a card.
- **Combo at 0:** chip is muted and reserves its width (no layout shift when it appears).
- **Reduced motion:** the slide/pop/progress animations collapse automatically when the system
  animation scale is 0; the ~400ms success hold is functional feedback (kept) — it's a pause, not a
  decorative animation.
- **`total == 0`** (empty pool): progress = 0; the session finishes immediately (existing behavior).

## Testing

Gradle prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

- **`TypePracticeSessionTest`**: `currentCombo` reflects live consecutive first-try correct and
  resets to 0 on a wrong submit.
- **`TypePracticeViewModelTest`** (virtual time via `StandardTestDispatcher` + `advanceTimeBy`):
  - a correct submit enters `celebrating = true` with the just-answered card, `combo` popped, and
    one log appended; after `advanceTimeBy(CELEBRATE_MS)` it advances to the next card with
    `celebrating = false`.
  - `total` is the pool size; progress derivation `(total - remaining)/total` is correct after a
    clear.
  - the last-card-correct path lands on `finished`/report after the delay.
  - input is ignored while `celebrating`.
  - (existing correct-path tests updated to drive virtual time.)
- **Screen**: compile + previews (prompt both directions, celebrating, reveal, report, chooser).
  Reduced-motion / one-handed reach validated on the emulator (optional).
- **`StudyScreen` regression**: still compiles after switching to `RatingColors`; rating buttons
  render the same colors.

## Out of scope

- The direction chooser & session-report **layouts** (report may reference `RatingColors`, no
  restructure).
- The char-diff **algorithm** (only its colors change to mint/pink).
- Any persisted state, points/score economy, global-streak display, FSRS coupling, sync, matcher,
  logs, or mastery change.
- Haptics / sound effects.

## Commit / process rules

- No "claude" mention in commit messages; no Co-Authored-By / attribution trailer.
- Do not `git add` the unrelated untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md`,
  nor the gitignored `.superpowers/`.
