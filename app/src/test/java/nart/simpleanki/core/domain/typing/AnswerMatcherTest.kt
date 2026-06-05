package nart.simpleanki.core.domain.typing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    @Test fun exactMatch() = assertTrue(AnswerMatcher.matches("hello", "hello"))

    @Test fun caseInsensitive() = assertTrue(AnswerMatcher.matches("Hello", "hELLO"))

    @Test fun trimsAndCollapsesWhitespace() =
        assertTrue(AnswerMatcher.matches("  how   are  you ", "how are you"))

    @Test fun ignoresSurroundingPunctuation() {
        assertTrue(AnswerMatcher.matches("hello!", "hello"))
        assertTrue(AnswerMatcher.matches("¿cómo estás?", "cómo estás"))
        assertTrue(AnswerMatcher.matches("well-known", "well-known"))
    }

    @Test fun accentsAreEnforced() {
        assertFalse(AnswerMatcher.matches("cafe", "café"))
        assertTrue(AnswerMatcher.matches("café", "café"))
    }

    @Test fun blankNeverMatchesNonBlank() {
        assertFalse(AnswerMatcher.matches("", "hello"))
        assertFalse(AnswerMatcher.matches("   ", "hello"))
    }

    @Test fun blankExpectedAfterNormalizationNeverMatches() {
        assertFalse(AnswerMatcher.matches("hello", "?!."))
        assertFalse(AnswerMatcher.matches("", ""))
    }

    @Test fun wrongIsWrong() = assertFalse(AnswerMatcher.matches("hola", "hello"))
}
