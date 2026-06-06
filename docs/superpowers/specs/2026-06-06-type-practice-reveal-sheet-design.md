# Type Practice — Wrong-Answer Reveal Sheet — Design

**Date:** 2026-06-06
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/type-practice-mode` (extends the Type Practice gamified redesign on this branch).
**Parent specs:** `2026-06-06-type-practice-gamified-redesign-design.md`, `2026-06-06-type-practice-answer-diff-design.md`.

## Goal

Make the wrong-answer **reveal** state legible. Today the keyboard is pinned up for the whole
session, so the content zone between the top bar and the bottom answer bar is short; the prompt hero
card consumes it and the char-diff comparison (correct answer vs. what you typed) gets crammed or
pushed out of view. Redesign the reveal so that on a wrong answer the keyboard **drops** and a
bottom **result sheet** rises into the freed space, giving the comparison room to read.

## Background (current state this builds on)

- **`TypePracticeScreen.kt`** (after the gamified redesign): a `Scaffold` with `topBar` (✕ + progress
  + combo chip), a `content` `PromptArea` (centered prompt hero card via `AnimatedContent(cardTick)`,
  with `RevealDiff` appended below it while `state.revealing`), and a `bottomBar` `AnswerBar`
  (`Modifier.imePadding()`) holding a **persistent, always-focused** `OutlinedTextField` plus a
  contextual block: `revealing` → Continue (+ "I was right" when `canOverride`); `celebrating` →
  "Correct!" text; else → Check + Don't know. A single `FocusRequester` is re-requested via
  `LaunchedEffect(state.cardTick)`.
- **The persistent-field invariant** (from the keyboard-flicker fix): the field stays mounted and
  focused so the IME never collapses between answers. This was needed because the field previously
  unmounted on *every* answer — including correct ones — causing the keyboard to drop and bounce
  back. The invariant only matters while the keyboard *should* stay up.
- **`TypePracticeViewModel` / `TypePracticeUiState`** already expose everything this redesign needs:
  `revealing`, `revealedAnswer`, `lastTyped`, `canOverride`, `cardTick`, `current`, `celebrating`,
  `direction`. **No VM or domain change is required.**
- **`AnswerDiff`** renders the char-level diff; **`RatingColors`**: `Easy` = mint `0xFF00C7BE`
  (matches), `Again` = pink `0xFFFF2D55` (misses / wrong).

## Decision (from brainstorming, incl. the visual companion)

**Option B — "keyboard drops, result sheet rises".** On a wrong answer, release the field's focus so
the IME slides away; the bottom bar transforms into a pink **result sheet** that occupies the freed
bottom half of the screen. The keyboard returns on advance to the next card. Rejected: A (card morphs
in place — keeps the keyboard but the comparison stays cramped) and C (compact banner above the
keyboard — keyboard frozen but smallest text). B wins because legibility is the entire purpose of the
reveal, and the keyboard only ever moves on a *wrong* answer — the moment the user slows down to
study. Correct answers keep the keyboard pinned for a fast typing rhythm.

## Components (presentation-only — `TypePracticeScreen.kt` only)

### 1. `AnswerBar` — two-way branch

Replace the "persistent field + contextual block" structure with a top-level branch on
`state.revealing`:

- **`revealing` → `ResultSheet(state)`** — a pink-tinted `Surface` (full bottom-bar width), **no text
  field**. Contents:
  - a small circular ✕ badge (`RatingColors.Again` background) + "Correct answer" heading.
  - the **expected** diff: `AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)`
    rendered with matches in `RatingColors.Easy`, misses in `RatingColors.Again` + underline
    (the existing `RevealDiff` expected styling, moved here).
  - when `state.lastTyped.isNotBlank()`: a "you typed" line, misses struck-through in
    `RatingColors.Again` (existing `RevealDiff` typed styling).
  - a full-width primary **Continue** (`onContinue`); when `state.canOverride`, an **"I was right"**
    text/secondary button (`onOverride`) — same callbacks the current reveal branch uses.
- **else (typing or celebrating) → the persistent focused `OutlinedTextField`** + the existing
  contextual block (`celebrating` → "Correct!" + spacer; else → Check enabled-on-non-blank +
  Don't know). Unchanged from today.

The field unmounting while `revealing` is intentional and consistent with the persistent-field
invariant: it is *what* dismisses the IME, and the field only needs to persist while the keyboard
should stay up (typing / celebrating), not during the reveal pause.

### 2. `PromptArea` — drop the inline diff

Remove the `if (state.revealing) { Spacer; RevealDiff(state) }` block from `PromptArea`; the diff now
lives in `ResultSheet`. `PromptArea` just renders the prompt hero card (`AnimatedContent(cardTick)`)
as today. The old `RevealDiff` composable is removed (its rendering is reused inside `ResultSheet`).

### 3. Keyboard drive

- `LaunchedEffect(state.revealing)`: when it becomes `true`, dismiss the IME via
  `focusManager.clearFocus(force = true)` (and/or `keyboardController?.hide()`); when it becomes
  `false`, do nothing here — the existing `LaunchedEffect(state.cardTick)` re-requests focus as the
  next card's field remounts, bringing the keyboard back.
- Keep the existing `runCatching { focus.requestFocus() }` guard for the re-focus on advance (the
  field remounts the same frame `cardTick` changes; `runCatching` tolerates a not-yet-attached
  requester). If on-device testing shows the keyboard fails to return, add a one-frame
  `withFrameNanos`/short `delay` before `requestFocus()`.

### 4. Motion

The `ResultSheet` enters with `slideInVertically { it } + fadeIn` (rising from the bottom) so it
reads as a sheet coming up as the keyboard leaves. Compose honors the system animation scale, so
reduced-motion is respected automatically.

## Data flow

Wrong submit → VM sets `revealing = true`, `revealedAnswer`, `lastTyped`, `canOverride` (unchanged) →
`LaunchedEffect(revealing)` clears focus → IME slides down, `imePadding()` collapses, the bottom bar
settles to the screen edge and swaps to `ResultSheet` (slide-in) → user reads the comparison →
Continue/"I was right" → VM advances, `cardTick` ticks, `revealing = false` → field remounts,
`LaunchedEffect(cardTick)` re-focuses → keyboard slides back up for the next card.

## Error handling / edge cases

- **`lastTyped` blank (Don't know):** `ResultSheet` shows the correct answer and omits the "you typed"
  line (existing `isNotBlank()` guard).
- **Last card wrong → Continue:** advance lands on `finished`/report; no field in the tree, keyboard
  stays down. No re-focus.
- **Re-focus race on advance:** mitigated by the existing `runCatching` guard; fallback is a
  one-frame delay (see §3).
- **Close (✕) during reveal:** `onDone` pops back; nothing pending to cancel.
- **Correct path:** untouched — focus retained, keyboard stays, mint flash + auto-advance.

## Testing

Gradle prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

- **Unit tests:** unchanged — there is no VM/domain change. `:app:testDebugUnitTest` must stay green.
- **Compile + previews:** `:app:compileDebugKotlin`; add/keep a reveal preview that renders
  `ResultSheet` (TypeBack, `canOverride = true`, non-blank `lastTyped`) and a blank-typed variant.
- **On-device:** user screenshot confirms the keyboard drops on a wrong answer, the result sheet is
  fully legible (correct answer + you-typed both visible), and the keyboard returns on the next card.
- **Regression:** the correct/celebrate flow still keeps the keyboard pinned (no flicker); the report
  and direction chooser are unchanged.

## Out of scope

- The correct/celebrate flow, the combo chip, the progress bar, the prompt card layout.
- The `AnswerMatcher`, the `AnswerDiff` algorithm (only where its output is rendered moves).
- The session report and direction chooser layouts.
- Any persisted state, FSRS coupling, sync, logs, or mastery change. (Type Practice stays
  FSRS-decoupled: zero scheduling/review-log writes.)

## Commit / process rules

- No "claude" mention in commit messages; no Co-Authored-By / attribution trailer.
- Never `git add -A`. Do not `git add` the untracked
  `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` nor the gitignored `.superpowers/`.
- Gradle commands prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.
