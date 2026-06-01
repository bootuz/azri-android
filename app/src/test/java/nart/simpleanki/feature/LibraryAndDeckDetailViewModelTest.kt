package nart.simpleanki.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import nart.simpleanki.core.data.repository.FakeFolderDao
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.feature.deckdetail.DeckDetailViewModel
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
        folderRepo.upsert(Folder(id = "f1", name = "Langs", lastModified = 0))
        deckRepo.upsert(Deck(id = "d1", name = "In", folderId = "f1", dateCreated = 0, lastModified = 0))
        deckRepo.upsert(Deck(id = "d2", name = "Loose", folderId = null, dateCreated = 0, lastModified = 0))

        val vm = LibraryViewModel(folderRepo, deckRepo)
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

    private fun card(id: String, front: String, back: String) = Card(
        id = id, front = front, back = back, deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )
}
