package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypePracticeSessionTest {
    private fun card(id: String, back: String) = Card(
        id = id, front = "f-$id", back = back, deckId = "d",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    private class Recorder {
        data class Entry(val cardId: String, val correct: Boolean, val typed: String)
        val entries = mutableListOf<Entry>()
        val sink: (Card, Boolean, String) -> Unit = { c, ok, t -> entries += Entry(c.id, ok, t) }
    }

    @Test fun correctFirstTry_finalizesCorrect_advances_andCombos() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b")), emptySet(), rec.sink)
        assertEquals("c1", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("a"))
        assertEquals("c2", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("b"))
        assertTrue(s.isFinished)
        assertEquals(listOf(Recorder.Entry("c1", true, "a"), Recorder.Entry("c2", true, "b")), rec.entries)
        assertEquals(2, s.report().bestCombo)
        assertEquals(100, s.report().firstTryAccuracy)
        assertEquals(2, s.report().newlyMastered)
    }

    @Test fun wrongFirstTry_thenContinue_finalizesWrong_andRequeues() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b")), emptySet(), rec.sink)
        assertEquals(SubmitResult.Wrong("a"), s.submit("zzz"))
        assertTrue(s.isRevealing)
        assertTrue(s.canOverride)
        s.continueAfterWrong()
        // c1 finalized wrong (one log) and requeued behind c2.
        assertEquals(Recorder.Entry("c1", false, "zzz"), rec.entries.single())
        assertEquals("c2", s.current!!.id)
        assertFalse(s.isRevealing)
        // Clear c2, then c1 comes back; typing it right now clears it WITHOUT a new log.
        assertEquals(SubmitResult.Correct, s.submit("b"))
        assertEquals("c1", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("a"))
        assertTrue(s.isFinished)
        assertEquals(2, rec.entries.size)                         // exactly one log per card
        assertEquals(50, s.report().firstTryAccuracy)             // c1 wrong, c2 right
        assertEquals(1, s.report().bestCombo)                     // c2 was a clean first-try correct -> longest run is 1
    }

    @Test fun override_marksCorrect_clears_andCountsNewlyMastered() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a")), emptySet(), rec.sink)
        s.submit("close-but-wrong")
        assertTrue(s.canOverride)
        s.override()
        assertTrue(s.isFinished)
        assertEquals(Recorder.Entry("c1", true, "close-but-wrong"), rec.entries.single())
        assertEquals(1, s.report().newlyMastered)
    }

    @Test fun previouslyMastered_notRecountedAsNewly() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a")), previouslyMastered = setOf("c1"), onFinalize = rec.sink)
        s.submit("a")
        assertEquals(0, s.report().newlyMastered)
    }

    @Test fun emptyPool_isFinished_zeroReport() {
        val s = TypePracticeSession(emptyList(), emptySet())
        assertTrue(s.isFinished)
        assertNull(s.current)
        assertEquals(SessionReport(0, 0, 0, 0, 0), s.report())
    }

    @Test fun blankSubmit_onFirstAttempt_isWrong_andLogsOnce() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "answer")), emptySet(), rec.sink)
        assertEquals(SubmitResult.Wrong("answer"), s.submit(""))
        assertTrue(s.canOverride)
        s.continueAfterWrong()
        assertEquals(Recorder.Entry("c1", false, ""), rec.entries.single())
    }

    @Test fun retryAnsweredWrongAgain_doesNotDoubleLog() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b")), emptySet(), rec.sink)
        s.submit("wrong"); s.continueAfterWrong()                 // c1 first-try wrong -> 1 log, requeued behind c2
        assertEquals(SubmitResult.Correct, s.submit("b"))         // clear c2
        assertEquals("c1", s.current!!.id)
        s.submit("wrong-again"); s.continueAfterWrong()           // retry wrong -> no new log, requeued
        assertEquals("c1", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("a"))         // finally clear c1
        assertTrue(s.isFinished)
        assertEquals(1, rec.entries.count { it.cardId == "c1" })  // exactly one log for c1
        assertFalse(rec.entries.single { it.cardId == "c1" }.correct)
    }

    @Test fun typeFrontDirection_comparesAgainstTheFront() {
        val rec = Recorder()
        val s = TypePracticeSession(
            listOf(card("c1", back = "back-ignored")),
            onFinalize = rec.sink,
            direction = TypeDirection.TypeFront,
        )
        // In TypeFront, the answer is the FRONT ("f-c1"), not the back.
        assertEquals(SubmitResult.Wrong("f-c1"), s.submit("nope"))
        s.override()
        assertEquals(Recorder.Entry("c1", true, "nope"), rec.entries.single())
        assertTrue(s.isFinished)
    }

    @Test fun currentCombo_incrementsOnCorrect_resetsOnWrong() {
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b"), card("c3", "c")))
        assertEquals(0, s.currentCombo)
        s.submit("a"); assertEquals(1, s.currentCombo)
        s.submit("b"); assertEquals(2, s.currentCombo)
        s.submit("nope"); assertEquals(0, s.currentCombo)   // wrong first-try resets the combo
    }
}
