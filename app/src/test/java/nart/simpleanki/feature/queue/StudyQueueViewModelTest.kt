package nart.simpleanki.feature.queue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.FakeSettingsRepository
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
class StudyQueueViewModelTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun review(id: String, deckId: String) = Card(
        id = id, front = "Q", back = "A", deckId = deckId,
        dateCreated = now, lastModified = now,
        fsrsDue = now - 1000, fsrsState = CardState.Review.value,
    )

    private fun newCard(id: String, deckId: String) = Card(
        id = id, front = "Q", back = "A", deckId = deckId,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun aggregatesAcrossDecks_andBreaksDownPerDeck() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "B", name = "Beta", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A")); cardRepo.upsert(review("a2", "A")); cardRepo.upsert(newCard("a3", "A"))
        cardRepo.upsert(review("b1", "B"))

        val vm = StudyQueueViewModel(cardRepo, deckRepo, FakeSettingsRepository(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertTrue(s.hasWork)
        assertEquals(4, s.readyCount)   // 3 due + 1 new (within generous default limits)
        assertEquals(3, s.dueCount)
        assertEquals(1, s.newCount)
        assertTrue("has an estimate", s.estimatedMinutes >= 1)
        // Per-deck: Alpha (2 due, 1 new) sorts before Beta (1 due) by due count.
        assertEquals(listOf("A", "B"), s.decks.map { it.deckId })
        val alpha = s.decks.first { it.deckId == "A" }
        assertEquals(2, alpha.dueCount)
        assertEquals(1, alpha.newCount)
    }

    @Test
    fun allCaughtUp_whenNothingDueOrNew() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        // A review card whose due date is in the future — not ready today.
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 86_400_000L))

        val vm = StudyQueueViewModel(cardRepo, deckRepo, FakeSettingsRepository(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.hasWork)
        assertEquals(0, s.readyCount)
        assertTrue(s.decks.isEmpty())
    }

    @Test
    fun studiedToday_countsTodaysReviews_andMeetsGoal_independentOfQueue() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        // Two cards reviewed today (count toward progress) but due in the future (not in queue).
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 86_400_000L, fsrsLastReview = now))
        cardRepo.upsert(review("a2", "A").copy(fsrsDue = now + 86_400_000L, fsrsLastReview = now))
        // One reviewed yesterday — must NOT count toward today.
        cardRepo.upsert(review("a3", "A").copy(fsrsDue = now + 86_400_000L, fsrsLastReview = now - 2 * 86_400_000L))

        val settings = FakeSettingsRepository(AppSettings(newCardsTarget = 1, reviewCardsTarget = 1)) // goal total = 2
        val vm = StudyQueueViewModel(cardRepo, deckRepo, settings, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertEquals(2, s.studiedToday)
        assertEquals(2, s.goalTotal)
        assertTrue("goal met even though the queue is empty", s.goalMet)
        assertFalse("nothing ready today", s.hasWork)
        assertEquals(0, s.goalRemaining)
    }

    @Test
    fun goalMet_isFalse_whenTrackingDisabled() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 86_400_000L, fsrsLastReview = now))

        val settings = FakeSettingsRepository(
            AppSettings(dailyGoalEnabled = false, newCardsTarget = 1, reviewCardsTarget = 0),
        )
        val vm = StudyQueueViewModel(cardRepo, deckRepo, settings, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertFalse(vm.uiState.value.goalMet)
    }
}
