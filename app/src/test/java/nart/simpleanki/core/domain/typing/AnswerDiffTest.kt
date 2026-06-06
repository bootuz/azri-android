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
        assertEquals(listOf(seg("hell", Match), seg("l", Mismatch), seg("o", Match)), r.typed)
    }

    @Test fun bothEmpty_noSegments() {
        val r = AnswerDiff.diff(typed = "", expected = "")
        assertTrue(r.expected.isEmpty())
        assertTrue(r.typed.isEmpty())
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
