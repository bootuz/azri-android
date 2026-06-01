package nart.simpleanki.feature.study

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun newCard(id: String) = Card(
        id = id, front = "Q$id", back = "A$id", deckId = "d1",
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun loadsQueue_andStartsWithFirstCard() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", repo, FakeSettingsRepository(), now = { now })
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals("c1", s.current?.id)
        assertEquals(2, s.remaining)
        assertFalse(s.isRevealed)
    }

    @Test
    fun reveal_thenRate_advances_andPersistsFsrs() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", repo, FakeSettingsRepository(), now = { now })
        runCurrent()

        vm.onReveal()
        assertTrue(vm.uiState.value.isRevealed)

        vm.onRate(Rating.Good)
        runCurrent()

        val s = vm.uiState.value
        assertEquals("c2", s.current?.id)
        assertEquals(1, s.completed)
        assertEquals(1, s.ratingCounts[Rating.Good])
        assertFalse(s.isRevealed)
        // c1 persisted with advanced FSRS state (no longer New, reps incremented)
        val saved = dao.getById("c1")!!
        assertEquals(1, saved.fsrsReps)
        assertTrue(saved.fsrsState != CardState.New.value)
        assertTrue(saved.fsrsDue > now)
    }

    @Test
    fun ratingAllCards_finishesSession() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        val vm = StudyViewModel("d1", repo, FakeSettingsRepository(), now = { now })
        runCurrent()

        vm.onRate(Rating.Easy)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.finished)
        assertNull(s.current)
        assertEquals(1, s.completed)
    }

    @Test
    fun emptyDeck_finishesImmediately() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        val vm = StudyViewModel("d1", repo, FakeSettingsRepository(), now = { now })
        runCurrent()
        assertTrue(vm.uiState.value.finished)
        assertNull(vm.uiState.value.current)
    }
}
