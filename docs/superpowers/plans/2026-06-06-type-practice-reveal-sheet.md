# Type Practice Wrong-Answer Reveal Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a wrong answer in Type Practice, drop the keyboard and raise a roomy pink "result sheet" (correct answer vs. what you typed) into the freed bottom half, so the comparison is fully legible.

**Architecture:** Presentation-only change to `TypePracticeScreen.kt`. (1) A `LaunchedEffect(state.revealing)` clears the text field's focus when a wrong answer is revealed, which dismisses the IME; the existing `LaunchedEffect(state.cardTick)` re-focuses on advance, bringing the keyboard back. (2) `AnswerBar` branches at the top on `state.revealing`: when revealing it renders a new `ResultSheet` (no text field) holding the char-diff + Continue / "I was right"; otherwise it renders the persistent focused field + Check/Don't-know (or the "Correct!" celebrate text). The inline diff is removed from `PromptArea` and the old `RevealDiff` composable is deleted (its rendering moves into `ResultSheet`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3 + animation), Koin. No ViewModel/domain/test changes — the VM already exposes `revealing`, `revealedAnswer`, `lastTyped`, `canOverride`, `cardTick`.

**Verification reality:** This codebase has no Compose UI-test target, so the gate for each task is: `:app:compileDebugKotlin` compiles, `:app:testDebugUnitTest` stays green (regression guard — these tests don't touch the screen), and the `@Preview`s render. Final on-device confirmation is a user screenshot.

All Gradle commands run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android` and MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.

**Commit rules:** No "claude" in commit messages; no Co-Authored-By/attribution trailer. Never `git add -A`; stage only the files named in each task. Never stage `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` or `.superpowers/`.

---

## File Structure

- **Modify:** `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt` — the only file touched. Adds a focus-clear effect, restructures `AnswerBar`, adds `ResultSheet`, trims `PromptArea`, deletes `RevealDiff`, adds a preview.

---

### Task 1: Drop the keyboard when a wrong answer is revealed

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

Clearing focus from the `OutlinedTextField` dismisses the IME. We do this whenever `state.revealing` turns true. The existing `LaunchedEffect(state.cardTick)` already re-requests focus on advance, so the keyboard returns automatically on the next card. This task is independently observable: even before the sheet exists, the keyboard now drops on a wrong answer.

- [ ] **Step 1: Add the `LocalFocusManager` import**

In the import block (alphabetical, near the other `androidx.compose.ui.platform`/`focus` imports — place it after line 59 `import androidx.compose.ui.focus.focusRequester`), add:

```kotlin
import androidx.compose.ui.platform.LocalFocusManager
```

- [ ] **Step 2: Acquire the focus manager and add the reveal effect**

In `TypePracticeContent`, find these existing lines (around 126-127):

```kotlin
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.cardTick) { if (inSession) runCatching { focus.requestFocus() } }
```

Replace them with:

```kotlin
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.cardTick) { if (inSession) runCatching { focus.requestFocus() } }
    // On a wrong answer, release focus so the IME slides away and the result sheet has room.
    // On advance, cardTick changes and the effect above re-focuses, bringing the keyboard back.
    LaunchedEffect(state.revealing) { if (state.revealing) focusManager.clearFocus(force = true) }
```

- [ ] **Step 3: Compile and run the regression test suite**

Run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. The unit tests are unaffected by this screen change and must remain green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Drop the keyboard when a Type Practice wrong answer is revealed"
```

---

### Task 2: Raise a result sheet in the bottom bar on reveal

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

Replace the in-field reveal controls with a dedicated `ResultSheet` that occupies the bottom bar (no text field), and remove the inline diff from `PromptArea`. The diff rendering from the old `RevealDiff` moves into `ResultSheet`; `RevealDiff` is deleted.

- [ ] **Step 1: Add the animation imports**

In the import block, add these alongside the existing `androidx.compose.animation.*` imports (after line 3 `import androidx.compose.animation.AnimatedContent`):

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
```

(`fadeIn` and `tween` are already imported.)

- [ ] **Step 2: Trim `PromptArea` — remove the inline diff**

Find the current `PromptArea` body (around lines 250-273). Replace the whole function with this version, which drops the `if (state.revealing) { Spacer; RevealDiff(state) }` block:

```kotlin
private fun PromptArea(state: TypePracticeUiState) {
    val card = state.current ?: return
    val typeFront = state.direction == TypeDirection.TypeFront
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = state.cardTick,
            transitionSpec = {
                (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "card",
        ) { _ ->
            PromptCard(card, typeFront, celebrating = state.celebrating)
        }
    }
}
```

- [ ] **Step 3: Delete the standalone `RevealDiff` composable**

Find and delete the entire `RevealDiff` function (the block starting with the comment `/** The char-level diff shown in the upper zone ... */` and the `@Composable private fun RevealDiff(state: TypePracticeUiState) { ... }` that follows — currently lines 327-370). Its rendering is recreated inside `ResultSheet` in Step 5.

- [ ] **Step 4: Restructure `AnswerBar` to branch on `revealing`**

Replace the entire `AnswerBar` function (currently lines 372-432) with:

```kotlin
/** The bottom thumb-rail. While typing/celebrating: a persistent (always-focused) input + actions,
 *  above the keyboard. On a wrong answer it is replaced by the ResultSheet (no field). */
@Composable
private fun AnswerBar(
    state: TypePracticeUiState,
    focus: FocusRequester,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.revealing) {
        ResultSheet(state = state, onContinue = onContinue, onOverride = onOverride, modifier = modifier)
        return
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Always mounted + focused while typing/celebrating so the IME stays open; the VM ignores
            // input while celebrating, so it is effectively read-only then.
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                label = { Text("Type the answer") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            Spacer(Modifier.height(10.dp))
            if (state.celebrating) {
                Text("Correct!", style = MaterialTheme.typography.titleMedium, color = RatingColors.Easy)
                Spacer(Modifier.height(58.dp))
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Check") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDontKnow, modifier = Modifier.fillMaxWidth()) { Text("Don't know") }
            }
        }
    }
}
```

- [ ] **Step 5: Add the `ResultSheet` composable**

Immediately after the `AnswerBar` function, add:

```kotlin
/** Wrong-answer result sheet: rises into the space freed by the dismissed keyboard, showing the
 *  correct answer vs. what the user typed (char-diff), with Continue / "I was right". */
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
    val enter = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = enter,
        enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = pink.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, pink.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(pink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Correct answer", style = MaterialTheme.typography.titleSmall, color = pink)
                }
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(16.dp))
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

- [ ] **Step 6: Add a blank-typed reveal preview**

After the existing `TypeRevealPreview` (around line 510-514), add a second reveal preview for the "Don't know" case (blank `lastTyped`, no override), which exercises the omitted "You typed" line:

```kotlin
@Preview(name = "Type · revealed (blank)", showBackground = true)
@Composable
private fun TypeRevealBlankPreview() = PreviewWrap(
    previewState(revealing = true, revealedAnswer = "How are you?", lastTyped = "", canOverride = false),
)
```

- [ ] **Step 7: Compile, run tests, assemble**

Run:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Confirm no unresolved references (the deleted `RevealDiff` is no longer called from `PromptArea`; `ResultSheet` is called from `AnswerBar`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Raise a result sheet for Type Practice wrong answers"
```

---

## Self-Review

**Spec coverage:**
- Keyboard drops on reveal → Task 1 (`LaunchedEffect(state.revealing)` clears focus). ✓
- Keyboard returns on advance → existing `LaunchedEffect(state.cardTick)` re-focus, unchanged. ✓
- `AnswerBar` two-way branch (revealing → ResultSheet, else → field) → Task 2 Step 4. ✓
- `ResultSheet`: ✕ badge + "Correct answer", expected diff (mint/pink-underline), "you typed" struck-through, Continue, "I was right" when `canOverride`, blank-typed omits the line → Task 2 Step 5. ✓
- Diff removed from `PromptArea`; `RevealDiff` deleted → Task 2 Steps 2-3. ✓
- Sheet entrance `slideInVertically + fadeIn` → Task 2 Step 5 (`AnimatedVisibility`). ✓
- Previews (reveal with override + blank-typed) → existing `TypeRevealPreview` + Task 2 Step 6. ✓
- No VM/domain/test change; unit tests stay green → verification steps run `:app:testDebugUnitTest`. ✓
- FSRS-decoupling untouched (no scheduling/log code touched). ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code. ✓

**Type consistency:** `ResultSheet(state, onContinue, onOverride, modifier)` defined in Step 5 matches its call in Step 4. `AnswerDiff.diff(typed=, expected=)`, `AnswerDiff.Kind.Match/Mismatch`, `RatingColors.Again/Easy`, `state.revealedAnswer/lastTyped/canOverride` all match the existing code read from the file. `MutableTransitionState`/`AnimatedVisibility`/`slideInVertically` imports added in Step 1. ✓
