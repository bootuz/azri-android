# Type Practice — Char-Level Answer Diff — Design

**Date:** 2026-06-06
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/type-practice-mode` (extends the shipped-on-this-branch Type Practice feature).
**Parent spec:** `docs/superpowers/specs/2026-06-05-type-in-the-answer-design.md` (this pulls the
"char-level colored diff" item out of that spec's Phase-1 out-of-scope list).

## Goal

On a wrong typed answer, replace the plain reveal ("Correct answer: X" + "You typed: Y") with a
**character-level colored diff**: show the full correct answer with the characters the user missed
marked, and the user's input with the wrong/extra characters marked. This gives precise, at-a-glance
feedback on exactly what differed — the single most-requested polish for a type-the-answer mode.

## Background (existing code this builds on)

- **`feature/typepractice/TypePracticeScreen.kt` → `RevealPanel`**: currently renders the correct
  answer (`state.revealedAnswer`, in the primary color) and, when the user typed something,
  `"You typed: ${state.lastTyped}"` (in the error color). It also hosts the **Continue** and
  **"I was right"** buttons.
- **`TypePracticeUiState`** already carries both strings the diff needs: `revealedAnswer` (the
  direction-correct expected answer the VM fills on a wrong submit) and `lastTyped` (the user's
  input; empty for the "Don't know" path).
- **`core/domain/typing/AnswerMatcher`**: the answer check is **case-insensitive** (lowercases,
  trims, collapses whitespace, strips leading/trailing punctuation) but **accent-sensitive**
  (`café` ≠ `cafe`). The reveal only appears when this check fails.
- The codebase has a precedent for small pure domain units in `core/domain/typing/` (`AnswerMatcher`,
  `TypePracticeSession`, `TypingMastery`, `TypeDirection`), each Android-free and unit-tested.

## Decision (from brainstorming)

- **Diff matching mirrors the answer check: case-insensitive, accent-sensitive.** A character counts
  as matching when it is equal ignoring case (`Char.lowercaseChar()` equality), so `H` vs `h` is NOT
  flagged, but `é` vs `e` IS. The diff therefore highlights exactly what made the answer wrong.
  (Punctuation/whitespace are not specially aligned — they are rare in answers that reach the reveal,
  because the matcher already accepts edge-punctuation/whitespace-only differences.)
- **Two displays only** (matching the request): the full correct answer (diff-colored) + the user's
  input (diff-colored, hidden when empty).

## Components

### 1. `core/domain/typing/AnswerDiff.kt` (new, pure)

The single unit holding all diff logic. Android-free, unit-tested.

```kotlin
object AnswerDiff {
    enum class Kind { Match, Mismatch }
    data class Segment(val text: String, val kind: Kind)
    data class Result(val expected: List<Segment>, val typed: List<Segment>)

    fun diff(typed: String, expected: String): Result
}
```

- Computes a **longest-common-subsequence (LCS)** alignment over the two character sequences, using
  **case-insensitive** character equality (`this == other || lowercaseChar() == other.lowercaseChar()`).
- From the LCS, marks each character of `expected` and of `typed` as on-the-subsequence (`Match`) or
  not (`Mismatch`), then **coalesces** consecutive same-kind characters into `Segment`s.
- Semantics: in `expected`, `Mismatch` = a character the user **missed**; in `typed`, `Mismatch` =
  a **wrong/extra** character the user typed.
- Complexity is `O(n·m)` over the two answer strings — negligible for flashcard answers.

### 2. `feature/typepractice/TypePracticeScreen.kt` → `RevealPanel` (modified)

- Compute the diff once per reveal: `val diff = remember(state.revealedAnswer, state.lastTyped) { AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer) }`.
- Read theme colors in composable scope (not inside the builder), then render two `Text`s built with
  `buildAnnotatedString` + `withStyle(SpanStyle(...))`:
  - **Correct answer** (`titleLarge`, centered): `Match` runs in `colorScheme.primary`; `Mismatch`
    runs in `colorScheme.error` with `TextDecoration.Underline` (the chars you missed).
  - **Your input** (`bodyMedium`, centered, shown only when `state.lastTyped.isNotBlank()`): `Match`
    runs in `colorScheme.onSurfaceVariant`; `Mismatch` runs in `colorScheme.error` with
    `TextDecoration.LineThrough` (the chars that were wrong/extra).
- A small `"Correct answer"` / `"You typed"` label precedes each (as today). The **Continue** and
  **"I was right"** buttons are unchanged.

**No other change.** `TypePracticeUiState`, `TypePracticeViewModel`, the session, the matcher, the
typing log, and mastery are all untouched. The diff is pure presentation computed from existing state.

## Data flow

Wrong submit (already implemented) → VM sets `revealedAnswer` (direction-correct expected) +
`lastTyped` in `TypePracticeUiState` → `RevealPanel` computes `AnswerDiff.diff(lastTyped,
revealedAnswer)` and renders the two annotated strings. No new state, no new event.

## Error handling

- **Empty typed** ("Don't know"): `typed` is empty → `expected` renders entirely as `Mismatch`
  (whole answer underlined as missed); the "your input" line is hidden by the existing
  `isNotBlank()` guard. No crash.
- **Identical strings** can't reach the reveal (the matcher would have accepted them), so the diff is
  always over a genuine mismatch.
- Pure and bounded: no nullability, no I/O, no Android dependency — no new failure modes.

## Testing

Gradle commands prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

- **`AnswerDiffTest`** (JVM, the bulk of coverage):
  - **missing char** — `diff("helo", "hello")` → `expected` = `["hel"=Match, "l"=Mismatch, "o"=Match]`,
    `typed` = `["helo"=Match]`.
  - **extra char** — `diff("helllo", "hello")` → `expected` fully `Match`, `typed` has a `Mismatch`.
  - **empty typed** — `diff("", "cat")` → `expected` = `["cat"=Mismatch]`, `typed` empty.
  - **case-insensitive match** — `diff("HELLO", "hello")` → both fully `Match` (no false flag).
  - **accent mismatch** — `diff("cafe", "café")` → `expected` = `["caf"=Match, "é"=Mismatch]`,
    `typed` = `["caf"=Match, "e"=Mismatch]`.
  - **no common chars** — `diff("xyz", "abc")` → both entirely `Mismatch`.
- **`RevealPanel`**: verified by compile; the existing `TypeRevealPreview` (which supplies a wrong
  typed string) now renders the diff. No new preview required, though a preview already exercises it.
- **Build gate:** `:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.

## Out of scope

- Word-level diffing or "did you mean" typo suggestions.
- Diffing/annotating the prompt side or the report.
- Any change to `AnswerMatcher` normalization, the session, the typing log, mastery, or the VM.
- Special punctuation/whitespace alignment in the diff (only case is ignored; accents are kept).

## Commit / process rules

- No "claude" mention in commit messages; no Co-Authored-By / attribution trailer.
- Do not `git add` the unrelated untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.
