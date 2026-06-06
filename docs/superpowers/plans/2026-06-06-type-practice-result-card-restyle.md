# Type Practice Result Card Restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Type Practice wrong-answer `ResultSheet` from a Duolingo-style edge-to-edge pink banner into Azri's own card aesthetic — a white hairline-bordered card with an `INCORRECT` status pill and color reserved for the diff glyphs.

**Architecture:** Presentation-only change to `TypePracticeScreen.kt`. Extract the pill body from `DirectionPill` into a shared `StatusPill(text, color)` (so the periwinkle "TYPE THE BACK" pill and the new pink "INCORRECT" pill are the same component), then rewrite `ResultSheet`'s visual treatment. No ViewModel/domain/test/behavior change — same `AnimatedVisibility` rise, same `AnswerDiff` rendering, same Continue / "I was right" callbacks and `isNotBlank()` guard.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 + animation), Koin.

**Verification reality:** No Compose UI-test target exists. Per task the gate is: `:app:compileDebugKotlin` compiles, `:app:testDebugUnitTest` stays green (these tests don't touch the screen), and the existing reveal `@Preview`s render. Final confirmation is a user on-device screenshot.

All Gradle commands run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android` and MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.

**Commit rules:** No "claude" in commit messages; no Co-Authored-By/attribution trailer. Never `git add -A`; stage only the named file. Never stage `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` or `.superpowers/`.

---

## File Structure

- **Modify:** `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt` — the only file touched. Adds `StatusPill`, rewrites `DirectionPill` to delegate to it, and restyles `ResultSheet`.

---

### Task 1: Restyle the result sheet into a status-pill card

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

No import changes are required: the new code reuses APIs already imported, and the badge removal frees no imports (`Box`, `clip`, `CircleShape`, `background`, `Color`, `Icon`, `Icons.Default.Close` all remain used by `PromptCard`, the Scaffold content `Box`, the `TopAppBar`, and `ComboChip`). Do not add or remove any imports.

- [ ] **Step 1: Extract `StatusPill` and make `DirectionPill` delegate to it**

Find the current `DirectionPill` composable:

```kotlin
@Composable
private fun DirectionPill(typeFront: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(
            if (typeFront) "TYPE THE FRONT" else "TYPE THE BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
```

Replace it with these two composables (a shared `StatusPill` plus a thin `DirectionPill`):

```kotlin
/** A small uppercase status chip (rounded, tinted by [color]) — used for the direction tag and the
 *  wrong-answer INCORRECT marker so they read as one component. */
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

@Composable
private fun DirectionPill(typeFront: Boolean) {
    StatusPill(if (typeFront) "TYPE THE FRONT" else "TYPE THE BACK", MaterialTheme.colorScheme.primary)
}
```

- [ ] **Step 2: Restyle `ResultSheet`**

Replace the entire `ResultSheet` composable (its kdoc comment through its closing brace) with:

```kotlin
/** Wrong-answer result card: rises into the space freed by the dismissed keyboard, in Azri's card
 *  style (white surface + hairline border, an INCORRECT status pill, color only in the diff),
 *  showing the correct answer vs. what the user typed, with Continue / "I was right". */
@Composable
private fun ResultSheet(
    state: TypePracticeUiState,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pink = RatingColors.Again
    val mint = RatingColors.Easy
    val typedMatch = MaterialTheme.colorScheme.onSurfaceVariant
    val diff = remember(state.revealedAnswer, state.lastTyped) {
        AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)
    }
    // Starts false then targets true, so the slide-in replays on every wrong answer. This relies on
    // ResultSheet being unmounted between reveals (via AnswerBar's early-return), which re-inits the
    // remembered state — keep that gating if this is ever refactored, or the entrance won't replay.
    val enter = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = enter,
        enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        StatusPill("INCORRECT", pink)
                        Spacer(Modifier.height(10.dp))
                        Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = typedMatch)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildAnnotatedString {
                                diff.expected.forEach { seg ->
                                    when (seg.kind) {
                                        AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = mint)) { append(seg.text) }
                                        AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = pink, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                                    }
                                }
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (state.lastTyped.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("You typed", style = MaterialTheme.typography.labelMedium, color = typedMatch)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildAnnotatedString {
                                    diff.typed.forEach { seg ->
                                        when (seg.kind) {
                                            AnswerDiff.Kind.Match -> withStyle(SpanStyle(color = typedMatch)) { append(seg.text) }
                                            AnswerDiff.Kind.Mismatch -> withStyle(SpanStyle(color = pink, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Continue") }
                if (state.canOverride) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) { Text("I was right") }
                }
            }
        }
    }
}
```

This drops: the pink `0.08f` fill, the `topStart/topEnd`-only rounding, the edge-to-edge banner, the ✕ circle badge (`Row`/`Box`/`clip`/`background`/`Icon(Close)`), and the pink `titleSmall` heading. The diff content now sits in a white hairline-bordered card with an `INCORRECT` pill; Continue / "I was right" sit below the card.

- [ ] **Step 3: Compile, run tests, assemble**

Run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Confirm no unused-import warnings introduced and no unresolved references (`StatusPill` is referenced by both `DirectionPill` and `ResultSheet`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Restyle Type Practice wrong-answer result into an Azri card"
```

---

## Self-Review

**Spec coverage:**
- `StatusPill(text, color)` extracted; `DirectionPill` delegates (DRY) → Step 1. ✓
- Contained white card: `surface` color + `outlineVariant` hairline border + `RoundedCornerShape(20.dp)`, inset via the outer Column padding → Step 2. ✓
- `INCORRECT` pink pill (`RatingColors.Again`) at top-left → Step 2. ✓
- Quiet "Correct answer" `labelMedium`/`onSurfaceVariant` label; expected diff `headlineSmall` (mint/pink-underline), left-aligned → Step 2. ✓
- "You typed" line gated by `isNotBlank()` (blank-typed omits it) → Step 2. ✓
- Continue (periwinkle primary) + "I was right" (when `canOverride`) below the card → Step 2. ✓
- Removed pink fill, top-only rounding, edge-to-edge banner, ✕ badge, loud heading → Step 2. ✓
- `AnimatedVisibility` rise and `remember`-keyed diff preserved → Step 2. ✓
- No VM/domain/test change; unit tests stay green → Step 3 runs `:app:testDebugUnitTest`. ✓
- FSRS-decoupling untouched (only one screen file changed). ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code. ✓

**Type consistency:** `StatusPill(text: String, color: Color)` defined in Step 1 and called in Step 1 (`DirectionPill`) and Step 2 (`ResultSheet`) with matching argument types. `RatingColors.Again/Easy`, `AnswerDiff.diff(typed=, expected=)`, `AnswerDiff.Kind.Match/Mismatch`, `MaterialTheme.colorScheme.{surface,outlineVariant,onSurfaceVariant,background}`, `state.revealedAnswer/lastTyped/canOverride` all match the current file. No imports added/removed. ✓
