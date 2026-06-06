package nart.simpleanki.feature.typepractice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import nart.simpleanki.core.domain.typing.TypeDirection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypePracticeViewModelTest {
    private val now = 1_700_000_000_000L
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String, back: String, front: String = "f-$id") = Card(
        id = id, front = front, back = back, deckId = "A",
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    private fun model(vararg cards: Card): Pair<TypePracticeViewModel, TypingLogRepository> {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        kotlinx.coroutines.runBlocking {
            deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
            cards.forEach { cardRepo.upsert(it) }
        }
        // shuffleSeed = { null } disables shuffling so cards stay in insertion order (c1, c2, ...)
        return TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now }, shuffleSeed = { null }) to logRepo
    }

    @Test
    fun correctAnswer_celebrates_thenAdvances_andAppendsOneLog() = runTest(dispatcher.scheduler) {
        val (vm, logRepo) = model(card("c1", "answer"), card("c2", "two"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.awaitingDirection)

        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()
        assertEquals("c1", vm.uiState.value.current!!.id)
        assertEquals(2, vm.uiState.value.total)

        vm.onInput("answer")
        vm.onSubmit()
        assertTrue(vm.uiState.value.celebrating)
        assertEquals("c1", vm.uiState.value.current!!.id)
        assertEquals(1, vm.uiState.value.combo)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.celebrating)
        assertEquals("c2", vm.uiState.value.current!!.id)

        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertTrue(logs.single().correct)
    }

    @Test
    fun progress_tracksClearedOverTotal() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"), card("c2", "b"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.total)
        assertEquals(2, vm.uiState.value.remaining)

        vm.onInput("a"); vm.onSubmit(); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.total)
        assertEquals(1, vm.uiState.value.remaining)
    }

    @Test
    fun wrongAnswer_resetsComboChip_andReveals() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "answer"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("nope"); vm.onSubmit()
        assertTrue(vm.uiState.value.revealing)
        assertEquals(0, vm.uiState.value.combo)
        assertEquals("answer", vm.uiState.value.revealedAnswer)
        assertFalse(vm.uiState.value.celebrating)
    }

    @Test
    fun inputIgnoredWhileCelebrating() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"), card("c2", "b"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("a"); vm.onSubmit()
        assertTrue(vm.uiState.value.celebrating)
        vm.onInput("ignored")
        assertEquals("a", vm.uiState.value.input)
        vm.onSubmit()
        assertEquals("c1", vm.uiState.value.current!!.id)
    }

    @Test
    fun lastCardCorrect_finishesAfterCelebrate() = runTest(dispatcher.scheduler) {
        val (vm, _) = model(card("c1", "a"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeBack)
        advanceUntilIdle()

        vm.onInput("a"); vm.onSubmit()
        assertTrue(vm.uiState.value.celebrating)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertEquals(1, vm.uiState.value.report!!.completed)
    }

    @Test
    fun typeFront_typesTheFront() = runTest(dispatcher.scheduler) {
        val (vm, logRepo) = model(card("c1", back = "definition"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.chooseDirection(TypeDirection.TypeFront)
        advanceUntilIdle()
        vm.onInput("f-c1"); vm.onSubmit(); advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertTrue(logRepo.observeLogs().first().single().correct)
    }
}
