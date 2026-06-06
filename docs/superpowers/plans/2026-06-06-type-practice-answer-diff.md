# Type Practice Char-Level Answer Diff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a wrong typed answer, render a character-level colored diff in the Type Practice reveal — the correct answer with missed characters underlined, and the user's input with wrong/extra characters struck through.

**Architecture:** A new pure `AnswerDiff` (LCS over characters, case-insensitive / accent-sensitive) returns per-character `Match`/`Mismatch` segments for both the expected and typed strings. `RevealPanel` (the only UI change) computes the diff from the already-present `revealedAnswer` + `lastTyped` state and renders two `AnnotatedString`s. No ViewModel/state/matcher/log/mastery change.

**Tech Stack:** Kotlin, Jetpack Compose (`buildAnnotatedString`/`SpanStyle`/`TextDecoration`), JUnit4.

**Branch:** `feature/type-practice-mode` (extends the Type Practice feature already on this branch).

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Commit rule:** No "claude" mention in commit messages; no Co-Authored-By / attribution trailer. NEVER `git add -A` / `git add .` — add explicit paths only, so the untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` is never staged.

---

## File Structure
- `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerDiff.kt` (create) — pure LCS diff; the single unit holding all diff logic.
- `app/src/test/java/nart/simpleanki/core/domain/typing/AnswerDiffTest.kt` (create) — pure JVM tests.
- `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt` (modify) — `RevealPanel` renders the diff (the only UI change; imports added).

---

## Task 1: `AnswerDiff` (pure character-level diff)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerDiff.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/typing/AnswerDiffTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/domain/typing/AnswerDiffTest.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.typing.AnswerDiff.Kind.Match
import nart.simpleanki.core.domain.typing.AnswerDiff.Kind.Mismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerDiffTest {
    private fun seg(text: String, kind: AnswerDiff.Kind) = AnswerDiff.Segment(text, kind)

    @Test fun missingCharInExpected() {
        val r = AnswerDiff.diff(typed = "helo", expected = "hello")
        assertEquals(listOf(seg("hel", Match), seg("l", Mismatch), seg("o", Match)), r.expected)
        assertEquals(listOf(seg("helo", Match)), r.typed)
    }

    @Test fun extraCharInTyped() {
        val r = AnswerDiff.diff(typed = "helllo", expected = "hello")
        assertEquals(listOf(seg("hello", Match)), r.expected)
        assertTrue(r.typed.any { it.kind == Mismatch })
    }

    @Test fun emptyTyped_allExpectedMismatch() {
        val r = AnswerDiff.diff(typed = "", expected = "cat")
        assertEquals(listOf(seg("cat", Mismatch)), r.expected)
        assertTrue(r.typed.isEmpty())
    }

    @Test fun caseInsensitiveMatches() {
        val r = AnswerDiff.diff(typed = "HELLO", expected = "hello")
        assertEquals(listOf(seg("hello", Match)), r.expected)
        assertEquals(listOf(seg("HELLO", Match)), r.typed)
    }

    @Test fun accentIsAMismatch() {
        val r = AnswerDiff.diff(typed = "cafe", expected = "café")
        assertEquals(listOf(seg("caf", Match), seg("é", Mismatch)), r.expected)
        assertEquals(listOf(seg("caf", Match), seg("e", Mismatch)), r.typed)
    }

    @Test fun noCommonChars_allMismatch() {
        val r = AnswerDiff.diff(typed = "xyz", expected = "abc")
        assertEquals(listOf(seg("abc", Mismatch)), r.expected)
        assertEquals(listOf(seg("xyz", Mismatch)), r.typed)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.AnswerDiffTest"`
Expected: COMPILE FAILURE (`AnswerDiff` does not exist).

- [ ] **Step 3: Implement `AnswerDiff.kt`**

Create `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerDiff.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

/**
 * Character-level diff between a typed answer and the expected answer, for the wrong-answer reveal.
 * Matching is case-insensitive (the answer check is too) but accent-sensitive, so "é" vs "e" is a
 * mismatch. Returns, for each of the expected and typed strings, a list of coalesced [Segment]s
 * marking which runs are on the longest common subsequence ([Kind.Match]) and which differ
 * ([Kind.Mismatch] — i.e. missing chars in the expected string, extra/wrong chars in the typed one).
 */
object AnswerDiff {
    enum class Kind { Match, Mismatch }
    data class Segment(val text: String, val kind: Kind)
    data class Result(val expected: List<Segment>, val typed: List<Segment>)

    fun diff(typed: String, expected: String): Result {
        val a = typed
        val b = expected
        val n = a.length
        val m = b.length
        // dp[i][j] = LCS length of a[i..] and b[j..] (case-insensitive char equality).
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i].matchesIgnoreCase(b[j])) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val aMatch = BooleanArray(n)
        val bMatch = BooleanArray(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (a[i].matchesIgnoreCase(b[j])) {
                aMatch[i] = true
                bMatch[j] = true
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++
            } else {
                j++
            }
        }
        return Result(expected = segmentsOf(b, bMatch), typed = segmentsOf(a, aMatch))
    }

    private fun Char.matchesIgnoreCase(other: Char): Boolean =
        this == other || lowercaseChar() == other.lowercaseChar()

    /** Coalesces consecutive chars of [s] with the same match-status into [Segment]s. */
    private fun segmentsOf(s: String, match: BooleanArray): List<Segment> {
        val out = mutableListOf<Segment>()
        var k = 0
        while (k < s.length) {
            val kind = if (match[k]) Kind.Match else Kind.Mismatch
            val start = k
            while (k < s.length && (if (match[k]) Kind.Match else Kind.Mismatch) == kind) k++
            out += Segment(s.substring(start, k), kind)
        }
        return out
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.AnswerDiffTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/typing/AnswerDiff.kt \
        app/src/test/java/nart/simpleanki/core/domain/typing/AnswerDiffTest.kt
git commit -m "Add AnswerDiff for character-level answer comparison"
```

---

## Task 2: Render the diff in `RevealPanel`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

No unit test (Compose UI; the logic is covered by Task 1). Verified by compile + the existing `TypeRevealPreview` (which supplies a wrong typed string, so it renders the diff).

- [ ] **Step 1: Add imports**

In `TypePracticeScreen.kt`, add these imports (alongside the existing ones):
```kotlin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import nart.simpleanki.core.domain.typing.AnswerDiff
```

- [ ] **Step 2: Replace the `RevealPanel` composable**

Replace the ENTIRE existing `RevealPanel` composable with this version. It keeps the Continue / "I was right" buttons unchanged and swaps the two plain `Text`s (the correct answer and "You typed: …") for diff-colored ones:
```kotlin
@Composable
private fun RevealPanel(state: TypePracticeUiState, onContinue: () -> Unit, onOverride: () -> Unit) {
    val diff = remember(state.revealedAnswer, state.lastTyped) {
        AnswerDiff.diff(typed = state.lastTyped, expected = state.revealedAnswer)
    }
    val matchColor = MaterialTheme.colorScheme.primary
    val missColor = MaterialTheme.colorScheme.error
    val typedMatchColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Correct answer",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                diff.expected.forEach { seg ->
                    when (seg.kind) {
                        AnswerDiff.Kind.Match ->
                            withStyle(SpanStyle(color = matchColor)) { append(seg.text) }
                        AnswerDiff.Kind.Mismatch ->
                            withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.Underline)) { append(seg.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (state.lastTyped.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You typed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    diff.typed.forEach { seg ->
                        when (seg.kind) {
                            AnswerDiff.Kind.Match ->
                                withStyle(SpanStyle(color = typedMatchColor)) { append(seg.text) }
                            AnswerDiff.Kind.Mismatch ->
                                withStyle(SpanStyle(color = missColor, textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
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
            TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) {
                Text("I was right")
            }
        }
    }
}
```
(`remember`, `MaterialTheme`, `Modifier`, `Alignment`, `Spacer`, `height`, `fillMaxWidth`, `TextAlign`, `Button`, `TextButton`, `Text`, `dp` are all already imported in this file. Do not change any other composable, and leave the existing previews as-is — `TypeRevealPreview` already exercises the diff path.)

- [ ] **Step 3: Verify it compiles + full unit suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass (the suite gains the 6 `AnswerDiffTest` tests from Task 1).

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Render char-level diff in the Type Practice wrong-answer reveal"
```

---

## Final verification
- [ ] `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL, all unit tests green.
- [ ] Confirm no commit message mentions "claude" and none carry a Co-Authored-By/attribution trailer: `git log --format='%B' origin/main..HEAD | grep -i -E "claude|co-authored-by"` → no output.
- [ ] Confirm the untracked realtime-study-queue plan was never staged: `git status --short` still shows `?? docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.
- [ ] (Optional, emulator) In a Type Practice session, type a wrong answer → the reveal shows the correct answer with the missed characters underlined in the error color and your input with the wrong/extra characters struck through; "Don't know" shows the whole answer marked as missed with no "You typed" line.
