package nart.simpleanki.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeFolderDao
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.feature.deckdetail.DeckDetailViewModel
import nart.simpleanki.feature.folderdetail.FolderDetailViewModel
import nart.simpleanki.feature.library.LibraryViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAndDeckDetailViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun library_splitsDecksByFolder() = runTest {
        val folderRepo = FolderRepository(FakeFolderDao(), now = { 1L })
        val deckRepo = DeckRepository(FakeDeckDao(), now = { 1L })
        val cardRepo = CardRepository(FakeCardDao(), now = { 1L })
        folderRepo.upsert(Folder(id = "f1", name = "Langs", lastModified = 0))
        deckRepo.upsert(Deck(id = "d1", name = "In", folderId = "f1", dateCreated = 0, lastModified = 0))
        deckRepo.upsert(Deck(id = "d2", name = "Loose", folderId = null, dateCreated = 0, lastModified = 0))

        val vm = LibraryViewModel(folderRepo, deckRepo, cardRepo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf("f1"), state.folders.map { it.id })
        assertEquals(listOf("d2"), state.decksWithoutFolder.map { it.id })
        assertEquals(2, state.allDecks.size)
    }

    @Test
    fun deckDetail_searchFiltersCards() = runTest {
        val cardDao = FakeCardDao()
        val cardRepo = CardRepository(cardDao, now = { 1L })
        cardRepo.upsert(card("c1", "apple", "fruit"))
        cardRepo.upsert(card("c2", "dog", "animal"))

        val vm = DeckDetailViewModel("d1", cardRepo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(2, vm.uiState.value.visibleCards.size)
        vm.onQueryChange("dog")
        runCurrent()
        assertEquals(listOf("c2"), vm.uiState.value.visibleCards.map { it.id })
    }

    @Test
    fun deckDetail_deleteThenRestore_removesAndReAddsCard() = runTest {
        val cardDao = FakeCardDao()
        val cardRepo = CardRepository(cardDao, now = { 1L })
        cardRepo.upsert(card("c1", "apple", "fruit"))
        cardRepo.upsert(card("c2", "dog", "animal"))

        val vm = DeckDetailViewModel("d1", cardRepo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val toDelete = vm.uiState.value.cards.first { it.id == "c1" }

        vm.deleteCard(toDelete)
        runCurrent()
        assertEquals(listOf("c2"), vm.uiState.value.cards.map { it.id }) // soft-deleted, gone from list

        vm.restoreCard(toDelete)
        runCurrent()
        assertEquals(setOf("c1", "c2"), vm.uiState.value.cards.map { it.id }.toSet()) // undo brings it back
    }

    @Test
    fun deckDetail_dueCountUpdatesLive_whenACardBecomesDue() = runTest {
        // Override the @Before Main dispatcher so the VM shares THIS test's scheduler (so the
        // ticker's delay is advanced by advanceTimeBy below).
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val base = 1_700_000_000_000L
        val clock = { base + testScheduler.currentTime }
        val cardRepo = CardRepository(FakeCardDao(), now = { base })
        // A review card due 60s from now — not due yet.
        cardRepo.upsert(
            Card(
                id = "c1", front = "Q", back = "A", deckId = "d1",
                dateCreated = base, lastModified = base,
                fsrsDue = base + 60_000L, fsrsState = CardState.Review.value,
            ),
        )

        val vm = DeckDetailViewModel("d1", cardRepo, now = clock)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("not due yet", 0, vm.uiState.value.dueCount)

        advanceTimeBy(61_000L)   // cross the due moment
        runCurrent()
        assertEquals("became due", 1, vm.uiState.value.dueCount)
    }

    @Test
    fun folderDetail_listsOnlyDecksInThatFolder() = runTest {
        val folderRepo = FolderRepository(FakeFolderDao(), now = { 1L })
        val deckRepo = DeckRepository(FakeDeckDao(), now = { 1L })
        val cardRepo = CardRepository(FakeCardDao(), now = { 1L })
        folderRepo.upsert(Folder(id = "f1", name = "Languages", lastModified = 0))
        deckRepo.upsert(Deck(id = "d1", name = "In folder", folderId = "f1", dateCreated = 0, lastModified = 0))
        deckRepo.upsert(Deck(id = "d2", name = "Elsewhere", folderId = null, dateCreated = 0, lastModified = 0))

        val vm = FolderDetailViewModel("f1", deckRepo, cardRepo, folderRepo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals("Languages", vm.uiState.value.folderName)
        assertEquals(listOf("d1"), vm.uiState.value.decks.map { it.id })
    }

    private fun card(id: String, front: String, back: String) = Card(
        id = id, front = front, back = back, deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )
}
