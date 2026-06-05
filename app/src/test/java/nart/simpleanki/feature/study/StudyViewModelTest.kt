package nart.simpleanki.feature.study

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.analytics.FakeLogService
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeReviewLogDao
import nart.simpleanki.core.data.repository.ReviewLogRepository
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.domain.fsrs.QueueSortOrder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
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

    private fun newCard(id: String, deckId: String = "d1") = Card(
        id = id, front = "Q$id", back = "A$id", deckId = deckId,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun nullDeckId_studiesGlobalQueueAcrossDecks() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1", deckId = "d1"))
        repo.upsert(newCard("c2", deckId = "d2"))
        // null deckId = global queue: cards from every deck are included.
        val vm = StudyViewModel(null, null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()
        assertEquals(2, vm.uiState.value.remaining)
    }

    @Test
    fun folderId_studiesOnlyCardsInThatFoldersDecks() = runTest {
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        deckRepo.upsert(Deck(id = "d1", name = "A", folderId = "f1", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "d2", name = "B", folderId = null, dateCreated = now, lastModified = now))
        cardRepo.upsert(newCard("c1", deckId = "d1")) // inside folder f1
        cardRepo.upsert(newCard("c2", deckId = "d2")) // outside f1

        val vm = StudyViewModel(null, "f1", cardRepo, deckRepo, FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()

        assertEquals(1, vm.uiState.value.remaining)
        assertEquals("c1", vm.uiState.value.current?.id)
    }

    @Test
    fun loadsQueue_andStartsWithFirstCard() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals("c1", s.current?.id)
        assertEquals(2, s.remaining)
        assertFalse(s.isRevealed)
        // Answer buttons get a next-due interval preview for all four ratings.
        assertEquals(setOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy), s.ratingIntervals.keys)
        assertTrue(s.ratingIntervals.values.all { it.isNotBlank() })
    }

    @Test
    fun rating_refreshesIntervalsForNextCard() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()

        vm.onRate(Rating.Good)
        runCurrent()
        assertEquals("c2", vm.uiState.value.current?.id)
        assertEquals(4, vm.uiState.value.ratingIntervals.size)
    }

    @Test
    fun reveal_thenRate_advances_andPersistsFsrs() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
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
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()

        vm.onRate(Rating.Easy)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.finished)
        assertNull(s.current)
        assertEquals(1, s.completed)
    }

    @Test
    fun appliesQueueSortOrder_difficultyHardestFirst() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        // Review cards must have stability > 0 (real FSRS invariant) so the interval preview is valid.
        fun reviewCard(id: String, difficulty: Double) = newCard(id).copy(
            fsrsState = CardState.Review.value, fsrsDue = now - 1000,
            fsrsDifficulty = difficulty, fsrsStability = 10.0, fsrsLastReview = now - 86_400_000L,
        )
        repo.upsert(reviewCard("easy", 2.0))
        repo.upsert(reviewCard("hard", 9.0))
        val settings = FakeSettingsRepository(AppSettings(queueSortOrder = QueueSortOrder.Difficulty))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), settings, ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()
        assertEquals("hard", vm.uiState.value.current?.id) // hardest card first
    }

    @Test
    fun load_tracksReviewSessionStart() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        val log = FakeLogService()
        StudyViewModel(null, null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now }, logManager = LogManager(listOf(log)))
        runCurrent()
        assertTrue(log.events.any { it.eventName == "review_session_start" })
    }

    @Test
    fun rating_tracksCardRated_andCompletionOnLastCard() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        val log = FakeLogService()
        val vm = StudyViewModel(null, null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now }, logManager = LogManager(listOf(log)))
        runCurrent()
        vm.onRate(Rating.Good)
        runCurrent()
        // card_rated carries the lowercased rating name; review_session_complete carries the Int count.
        val rated = log.events.first { it.eventName == "card_rated" }
        assertEquals("good", rated.params["rating"])
        val done = log.events.first { it.eventName == "review_session_complete" }
        assertEquals(1, done.params["count"])
    }

    @Test
    fun flipHint_showsUntilFirstReveal_thenStaysHiddenForSession() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        repo.upsert(newCard("c2"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()

        // Hint visible at session start.
        assertTrue(vm.uiState.value.showFlipHint)

        // First flip hides the hint.
        vm.onReveal()
        assertTrue(vm.uiState.value.isRevealed)
        assertFalse(vm.uiState.value.showFlipHint)

        // Advancing to the next card keeps the hint hidden; new card is back on its front.
        vm.onRate(Rating.Good)
        runCurrent()
        assertFalse(vm.uiState.value.showFlipHint)
        assertFalse(vm.uiState.value.isRevealed)
        assertEquals("c2", vm.uiState.value.current?.id)
    }

    @Test
    fun emptyDeck_finishesImmediately() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })
        runCurrent()
        assertTrue(vm.uiState.value.finished)
        assertNull(vm.uiState.value.current)
    }

    @Test
    fun finishingSession_stampsElapsedDuration() = runTest {
        var clock = now
        val repo = CardRepository(FakeCardDao(), now = { clock })
        repo.upsert(newCard("c1"))
        val vm = StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { clock }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { clock })
        runCurrent()
        // Session started at `now`; advance the clock 3s, then rate the only card to finish.
        clock = now + 3_000
        vm.onRate(Rating.Good)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.finished)
        assertEquals(3_000L, s.durationMillis)
    }

    @Test
    fun rating_appendsOneReviewLog_withCardIdAndRating() = runTest {
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(newCard("c1", deckId = "d1"))
        val logRepo = ReviewLogRepository(FakeReviewLogDao(), newId = { "log-1" })

        val vm = StudyViewModel(
            "d1", null, cardRepo, DeckRepository(FakeDeckDao(), now = { now }),
            FakeSettingsRepository(), logRepo, now = { now },
        )
        runCurrent()

        vm.onReveal()
        vm.onRate(Rating.Good)
        runCurrent()

        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("c1", logs[0].cardId)
        assertEquals(Rating.Good, logs[0].rating)
    }

    @Test
    fun finishingSession_setsCurrentStreakAtLeastOne() = runTest {
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(newCard("c1", deckId = "d1"))

        val vm = StudyViewModel(
            "d1", null, cardRepo, DeckRepository(FakeDeckDao(), now = { now }),
            FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.onReveal()
        vm.onRate(Rating.Good)   // last (only) card -> session finishes
        runCurrent()

        assertTrue(vm.uiState.value.finished)
        assertEquals(1, vm.uiState.value.currentStreak)
    }
}
