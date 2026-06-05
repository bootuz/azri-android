package nart.simpleanki.feature.queue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeFolderDao
import nart.simpleanki.core.data.repository.FakeReviewLogDao
import nart.simpleanki.core.data.repository.FakeStreakStateDao
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.data.repository.ReviewLogRepository
import nart.simpleanki.core.data.repository.StreakProvider
import nart.simpleanki.core.data.repository.StreakStateRepository
import java.util.TimeZone
import nart.simpleanki.core.billing.Entitlement
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.fsrs.QueueSortOrder
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
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

    private fun emptyStreak() = StreakProvider(ReviewLogRepository(FakeReviewLogDao()), StreakStateRepository(FakeStreakStateDao()))

    @Test
    fun aggregatesAcrossDecks_andBreaksDownPerDeck() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "B", name = "Beta", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A")); cardRepo.upsert(review("a2", "A")); cardRepo.upsert(newCard("a3", "A"))
        cardRepo.upsert(review("b1", "B"))

        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = { now })
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
    fun dueCount_updatesLive_whenAFutureCardBecomesDue() = runTest {
        // The @Before sets Main to an UnconfinedTestDispatcher with its OWN scheduler; override it
        // here so the VM's coroutines (incl. the ticker's delay) share THIS test's scheduler and
        // are driven by advanceTimeBy below.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // Clock tied to virtual time: advancing the scheduler advances now().
        val clock = { now + testScheduler.currentTime }
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        // A review card due 60s from now — not ready yet.
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 60_000L))

        val vm = StudyQueueViewModel(
            cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }),
            FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = clock,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("not due yet", 0, vm.uiState.value.dueCount)
        assertFalse("no work yet", vm.uiState.value.hasWork)

        advanceTimeBy(61_000L)   // cross the due moment
        runCurrent()
        assertEquals("became due", 1, vm.uiState.value.dueCount)
        assertEquals(1, vm.uiState.value.readyCount)
        assertTrue(vm.uiState.value.hasWork)
    }

    @Test
    fun allCaughtUp_whenNothingDueOrNew() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        // A review card whose due date is in the future — not ready today.
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 86_400_000L))

        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.hasWork)
        assertEquals(0, s.readyCount)
        assertTrue(s.decks.isEmpty())
    }

    @Test
    fun hasAnyCards_isFalseForNewUser_andTrueOnceACardExists() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        // Brand-new user: no cards at all.
        assertFalse(vm.uiState.value.hasAnyCards)

        // A card due in the future: nothing ready today, but the user is no longer "new".
        cardRepo.upsert(review("a1", "A").copy(fsrsDue = now + 86_400_000L))
        runCurrent()
        assertFalse("nothing ready today", vm.uiState.value.hasWork)
        assertTrue("owns a card → not onboarding", vm.uiState.value.hasAnyCards)
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

        val settings = FakeSettingsRepository(AppSettings(dailyGoalEnabled = true, newCardsTarget = 1, reviewCardsTarget = 1)) // goal total = 2
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), settings, FakeEntitlementRepository(), emptyStreak(), now = { now })
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
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), settings, FakeEntitlementRepository(), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertFalse(vm.uiState.value.goalMet)
    }

    @Test
    fun buildsFolderChips_andQueueCardsCarryDeckAndFolderNames() = runTest {
        val folderRepo = FolderRepository(FakeFolderDao(), now = { now })
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        folderRepo.upsert(Folder(id = "f1", name = "Languages", lastModified = now))
        deckRepo.upsert(Deck(id = "A", name = "Spanish", folderId = "f1", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A").copy(front = "hola"))

        val vm = StudyQueueViewModel(cardRepo, deckRepo, folderRepo, FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        // Folder chip built from the folder's decks.
        assertEquals(listOf("f1"), s.folders.map { it.folderId })
        assertEquals(1, s.folders.first().deckCount)
        assertEquals(1, s.folders.first().dueCount)
        // Queue preview card carries front + deck + folder names.
        assertEquals(1, s.queueCards.size)
        val card = s.queueCards.first()
        assertEquals("hola", card.front)
        assertEquals("Spanish", card.deckName)
        assertEquals("Languages", card.folderName)
    }

    @Test
    fun sortOrder_difficulty_reordersQueuePreview_hardestFirst() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("easy", "A").copy(fsrsDifficulty = 2.0))
        cardRepo.upsert(review("hard", "A").copy(fsrsDifficulty = 9.0))

        val settings = FakeSettingsRepository(AppSettings(queueSortOrder = QueueSortOrder.Difficulty))
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), settings, FakeEntitlementRepository(), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(QueueSortOrder.Difficulty, vm.uiState.value.sortOrder)
        assertEquals(listOf("hard", "easy"), vm.uiState.value.queueCards.map { it.cardId })
    }

    @Test
    fun premiumNudge_showsForFreeUserWithCards_hiddenForPremium() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A"))

        val free = FakeEntitlementRepository(Entitlement(PremiumTier.None))
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), free, emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.showPremiumNudge)

        val premium = FakeEntitlementRepository(Entitlement(PremiumTier.Annual))
        val vm2 = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), premium, emptyStreak(), now = { now })
        backgroundScope.launch { vm2.uiState.collect {} }
        runCurrent()
        assertFalse(vm2.uiState.value.showPremiumNudge)
    }

    @Test
    fun dismissPremiumNudge_hidesNudge() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A"))
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), FakeEntitlementRepository(Entitlement(PremiumTier.None)), emptyStreak(), now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.showPremiumNudge)
        vm.dismissPremiumNudge(); runCurrent()
        assertFalse(vm.uiState.value.showPremiumNudge)
    }

    @Test
    fun setSortOrder_persistsThroughRepository() = runTest {
        val settings = FakeSettingsRepository()
        val vm = StudyQueueViewModel(
            CardRepository(FakeCardDao(), now = { now }),
            DeckRepository(FakeDeckDao(), now = { now }),
            FolderRepository(FakeFolderDao(), now = { now }),
            settings, FakeEntitlementRepository(), emptyStreak(), now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setSortOrder(QueueSortOrder.Shuffle); runCurrent()
        assertEquals(QueueSortOrder.Shuffle, vm.uiState.value.sortOrder)
    }

    @Test
    fun currentStreak_reflectsConsecutiveReviewDays() = runTest {
        val day = 86_400_000L
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(
            ReviewLogEntity("a", "c1", 3, 2, 0, 1.0, 5.0, 0.0, 0.0, 0.0, now, false),
            ReviewLogEntity("b", "c1", 3, 2, 0, 1.0, 5.0, 0.0, 0.0, 0.0, now - day, false),
        ))
        val streak = StreakProvider(ReviewLogRepository(logDao), StreakStateRepository(FakeStreakStateDao()), now = { now }, timeZone = TimeZone.getTimeZone("UTC"))

        val vm = StudyQueueViewModel(
            CardRepository(FakeCardDao(), now = { now }),
            DeckRepository(FakeDeckDao(), now = { now }),
            FolderRepository(FakeFolderDao(), now = { now }),
            FakeSettingsRepository(), FakeEntitlementRepository(), streak, now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStreak)
    }
}
