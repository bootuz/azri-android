package nart.simpleanki.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.feature.decksettings.DeckEditViewModel
import nart.simpleanki.feature.library.FolderEditViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModelsTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun deck_create_persistsWithSettings_inFolder() = runTest {
        val dao = FakeDeckDao()
        val repo = DeckRepository(dao, now = { now })
        val folderRepo = FolderRepository(FakeFolderDao(), now = { now })
        val vm = DeckEditViewModel(repo, folderRepo, CardRepository(FakeCardDao(), now = { now }), initialFolderId = "f1", idGenerator = { "deck-1" }, now = { now })
        assertFalse(vm.uiState.value.canSave)
        vm.onNameChange("French")
        vm.onColorChange(ColorOption.Purple)
        vm.onShuffledChange(true)
        vm.onReviewFilterChange(ReviewCardFilter.ReversesOnly)
        vm.save(); runCurrent()

        val decks = dao.observeAll().first()
        assertEquals(1, decks.size)
        with(decks.first()) {
            assertEquals("French", name)
            assertEquals(ColorOption.Purple.wire, color)
            assertTrue(shuffled)
            assertEquals(ReviewCardFilter.ReversesOnly.wire, reviewFilter)
            assertEquals("f1", folderId)
        }
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun deck_edit_loadsAndUpdates() = runTest {
        val dao = FakeDeckDao()
        val repo = DeckRepository(dao, now = { now })
        repo.upsert(Deck(id = "d1", name = "Old", color = ColorOption.Red, dateCreated = now, lastModified = now))
        val vm = DeckEditViewModel(repo, FolderRepository(FakeFolderDao(), now = { now }), CardRepository(FakeCardDao(), now = { now }), editingDeckId = "d1", now = { now })
        runCurrent()
        assertEquals("Old", vm.uiState.value.name)
        assertEquals(ColorOption.Red, vm.uiState.value.color)

        vm.onNameChange("New")
        vm.save(); runCurrent()
        assertEquals("New", dao.getById("d1")!!.name)
    }

    @Test
    fun deck_edit_moveIntoFolder_loadsFolderOptions_andPersists() = runTest {
        val deckDao = FakeDeckDao()
        val deckRepo = DeckRepository(deckDao, now = { now })
        val folderRepo = FolderRepository(FakeFolderDao(), now = { now })
        folderRepo.upsert(Folder(id = "f1", name = "Languages", lastModified = now))
        deckRepo.upsert(Deck(id = "d1", name = "French", dateCreated = now, lastModified = now)) // no folder

        val vm = DeckEditViewModel(deckRepo, folderRepo, CardRepository(FakeCardDao(), now = { now }), editingDeckId = "d1", now = { now })
        runCurrent()
        // Folder options are loaded for the picker, deck starts folderless.
        assertEquals(listOf("f1"), vm.uiState.value.folders.map { it.id })
        assertEquals(null, vm.uiState.value.folderId)

        vm.onFolderChange("f1")
        vm.save(); runCurrent()
        assertEquals("f1", deckDao.getById("d1")!!.folderId)
    }

    @Test
    fun deck_delete_cascadesToCards_andSignalsDeleted() = runTest {
        val deckDao = FakeDeckDao()
        val cardDao = FakeCardDao()
        val deckRepo = DeckRepository(deckDao, now = { now })
        val cardRepo = CardRepository(cardDao, now = { now })
        deckRepo.upsert(Deck(id = "d1", name = "Doomed", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "d1"))
        cardRepo.upsert(card("c2", "d1"))
        cardRepo.upsert(card("c3", "other")) // different deck, must survive

        val vm = DeckEditViewModel(
            deckRepo, FolderRepository(FakeFolderDao(), now = { now }), cardRepo,
            editingDeckId = "d1", now = { now },
        )
        runCurrent()

        vm.delete(); runCurrent()
        assertTrue(vm.uiState.value.deleted)
        assertTrue("deck soft-deleted", deckDao.getById("d1")!!.isDeleted)
        assertTrue("c1 soft-deleted", cardDao.getById("c1")!!.isDeleted)
        assertTrue("c2 soft-deleted", cardDao.getById("c2")!!.isDeleted)
        assertFalse("other deck's card untouched", cardDao.getById("c3")!!.isDeleted)
    }

    private fun card(id: String, deckId: String) = Card(
        id = id, front = "Q", back = "A", deckId = deckId,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test
    fun folder_create_andEdit() = runTest {
        val dao = FakeFolderDao()
        val repo = FolderRepository(dao, now = { now })
        val create = FolderEditViewModel(repo, idGenerator = { "fold-1" }, now = { now })
        create.onNameChange("Languages")
        create.onEmojiChange("🌍")
        create.save(); runCurrent()
        assertEquals("Languages", dao.getById("fold-1")!!.name)
        assertEquals("🌍", dao.getById("fold-1")!!.emoji)

        val edit = FolderEditViewModel(repo, editingFolderId = "fold-1", now = { now })
        runCurrent()
        assertEquals("Languages", edit.uiState.value.name)
        edit.onNameChange("Idiomas")
        edit.save(); runCurrent()
        assertEquals("Idiomas", dao.getById("fold-1")!!.name)
    }
}
