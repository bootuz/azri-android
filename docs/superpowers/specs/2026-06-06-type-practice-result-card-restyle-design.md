# Type Practice — Result Card Restyle — Design

**Date:** 2026-06-06
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/type-practice-mode`.
**Parent specs:** `2026-06-06-type-practice-reveal-sheet-design.md`,
`2026-06-06-type-practice-gamified-redesign-design.md`.

## Goal

Restyle the wrong-answer `ResultSheet` from the Duolingo-style edge-to-edge pink banner into Azri's
own restrained card aesthetic. Same content and behavior — only the visual treatment changes.

## Background (current state this builds on)

`ResultSheet` in `TypePracticeScreen.kt` (added by the reveal-sheet work) currently renders, inside
the Scaffold `bottomBar` while `state.revealing`:

- an `AnimatedVisibility` (`slideInVertically { it } + fadeIn`, 250ms) wrapping
- a `Surface` with `RatingColors.Again.copy(alpha = 0.08f)` fill, a `BorderStroke(1.dp, Again 0.5f)`,
  `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`, edge-to-edge `fillMaxWidth`,
- a header `Row`: a 26dp **✕ circle badge** (`Again` background, white `Icons.Default.Close`) +
  "Correct answer" `titleSmall` in pink,
- the **expected** char-diff (`headlineSmall`; matches `RatingColors.Easy`, mismatches `Again` +
  underline),
- when `state.lastTyped.isNotBlank()`: a "You typed" label + the **typed** diff (matches
  `onSurfaceVariant`, mismatches `Again` + line-through),
- a full-width **Continue** `Button` and, when `state.canOverride`, an **"I was right"** `TextButton`.

The app's design language (from `ui/theme/Color.kt` / `Theme.kt`): periwinkle primary `SAPrimary`
`#8299E6`; white card surfaces (`surface`) with ~12% hairline `outlineVariant` borders; elevated
`surfaceVariant` `#F2F2F4`; soft periwinkle container `#E6EAFB`; text `#262626`/`#666666`. The
**prompt hero card** (`PromptCard`) is the canonical card: `Surface(color = surface,
border = BorderStroke(1.dp, outlineVariant), shape = RoundedCornerShape(20.dp))`. The **`DirectionPill`**
is a soft periwinkle pill: `Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha = 0.14f))`
holding `labelSmall` text in `primary`, `letterSpacing = 1.sp`, padding `h = 10.dp, v = 4.dp`.

## Decision (from brainstorming, incl. the visual companion)

**Option B — "status-pill card".** The result lives in a contained white hairline-bordered card (the
prompt-card look), color reserved for meaning (the diff glyphs) plus one small **pink `INCORRECT`
pill** — the same pill component as the periwinkle `DirectionPill`, in the wrong-answer color.
Rejected: A (no status accent — risks reading ambiguous) and C (left-edge stripe — weaker tie to an
existing pattern).

## Components (presentation-only — `TypePracticeScreen.kt` only)

### 1. `StatusPill(text: String, color: Color)` — extracted, shared (DRY)

Extract the pill body currently inlined in `DirectionPill` into a reusable composable:

```kotlin
@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
```

`DirectionPill` is rewritten to delegate (behavior-identical):

```kotlin
@Composable
private fun DirectionPill(typeFront: Boolean) =
    StatusPill(if (typeFront) "TYPE THE FRONT" else "TYPE THE BACK", MaterialTheme.colorScheme.primary)
```

### 2. `ResultSheet` — restyled

Keep the `AnimatedVisibility(slideInVertically { it } + fadeIn, 250ms)` rise and the
`remember(revealedAnswer, lastTyped)` diff. Replace the banner `Surface` + header with:

- An outer `Surface(color = colorScheme.background)` (the bar background), `Column` padded
  `horizontal = 20.dp, vertical = 12.dp`, containing:
  - **The card:** `Surface(color = colorScheme.surface, border = BorderStroke(1.dp,
    colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp), fillMaxWidth)`, inner `Column`
    padded `16.dp`, left-aligned:
    - `StatusPill("INCORRECT", RatingColors.Again)`,
    - `Spacer(10.dp)`, "Correct answer" `labelMedium` in `onSurfaceVariant`,
    - `Spacer(4.dp)`, the **expected** diff `headlineSmall` (matches `RatingColors.Easy`, mismatches
      `RatingColors.Again` + `TextDecoration.Underline`) — left-aligned (no `textAlign`),
    - when `state.lastTyped.isNotBlank()`: `Spacer(10.dp)`, "You typed" `labelMedium`
      `onSurfaceVariant`, `Spacer(2.dp)`, the **typed** diff `bodyMedium` (matches `onSurfaceVariant`,
      mismatches `RatingColors.Again` + `TextDecoration.LineThrough`).
  - `Spacer(12.dp)`, the full-width **Continue** `Button` (default = periwinkle primary,
    `height = 50.dp`, `shape = shapes.large`, `onContinue`),
  - when `state.canOverride`: `Spacer(4.dp)`, **"I was right"** `TextButton` (`onOverride`).

**Removed:** the pink `0.08f` fill, the `topStart/topEnd`-only rounding, the edge-to-edge banner, the
✕ circle badge (`Box`/`clip`/`background`/`Icon(Close)`), and the pink `titleSmall` heading.

## Data flow

Unchanged. `state.revealing` → `ResultSheet` mounts → slide-in → user reads → Continue/"I was right"
→ VM advances. Color now derives only from the diff glyphs and the `INCORRECT` pill.

## Error handling / edge cases

- **`lastTyped` blank (Don't know):** card omits the "You typed" line (existing `isNotBlank()` guard).
- **Long answers:** left-aligned diff in a padded card wraps naturally (the banner centered it).
- **`canOverride` false:** no "I was right" button (existing guard).
- All keyboard-drop / advance / celebrate behavior is untouched (separate code paths).

## Testing

Gradle prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

- **Unit tests:** unchanged (no VM/domain change). `:app:testDebugUnitTest` stays green.
- **Compile + previews:** `:app:compileDebugKotlin`; the existing `TypeRevealPreview` (override,
  non-blank typed) and `TypeRevealBlankPreview` (blank typed) now render the restyled card.
- **On-device:** user screenshot confirms the result reads as an Azri card (white, hairline border,
  `INCORRECT` pill, periwinkle Continue), not the pink banner.
- **Regression:** `DirectionPill` still renders the periwinkle "TYPE THE BACK"/"TYPE THE FRONT" pill
  identically (now via `StatusPill`); the prompt/celebrate paths are unchanged.

## Out of scope

- The correct/celebrate flow, the prompt card, the keyboard-drop behavior, the combo chip, progress.
- The `AnswerMatcher` and `AnswerDiff` algorithm (only where its output is styled).
- The session report and direction chooser.
- Any persisted state, FSRS coupling, sync, logs, or mastery change (Type Practice stays
  FSRS-decoupled).

## Commit / process rules

- No "claude" in commit messages; no Co-Authored-By / attribution trailer.
- Never `git add -A`. Do not stage `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` nor
  `.superpowers/`.
- Gradle commands prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.
