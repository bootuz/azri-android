package nart.simpleanki.feature.typepractice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeTypingLogDao
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypePracticeViewModelTest {
    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String, back: String) = Card(
        id = id, front = "f-$id", back = back, deckId = "A",
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun correctAnswer_advances_appendsOneLog_andFinishes() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "answer"))
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        assertFalse(model.uiState.value.loading)
        assertEquals("c1", model.uiState.value.current!!.id)

        model.onInput("answer")
        model.onSubmit()
        runCurrent()

        assertTrue(model.uiState.value.finished)
        assertEquals(1, model.uiState.value.report!!.firstTryCorrect)
        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertTrue(logs.single().correct)
    }

    @Test
    fun wrongAnswer_revealsThenContinue_logsWrong_andRequeues() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "answer"))
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        model.onInput("nope")
        model.onSubmit()
        runCurrent()
        assertTrue(model.uiState.value.revealing)
        assertEquals("answer", model.uiState.value.revealedAnswer)
        assertTrue(model.uiState.value.canOverride)

        model.onContinue()
        runCurrent()
        assertFalse(model.uiState.value.revealing)
        assertEquals("c1", model.uiState.value.current!!.id)        // requeued back to itself (only card)
        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertFalse(logs.single().correct)
    }

    @Test
    fun blankBackCards_excluded_emptyPoolFinishesImmediately() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "   "))                          // blank back -> not typeable
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        assertTrue(model.uiState.value.finished)
        assertEquals(0, model.uiState.value.report!!.completed)
    }

    @Test
    fun override_appendsCorrectLog_andFinishes() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "answer"))
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        model.onInput("nope")
        model.onSubmit()
        runCurrent()
        assertTrue(model.uiState.value.canOverride)

        model.onOverride()
        runCurrent()
        assertTrue(model.uiState.value.finished)
        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertTrue(logs.single().correct)
    }
}
