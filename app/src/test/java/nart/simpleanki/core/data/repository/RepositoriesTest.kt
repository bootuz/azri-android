package nart.simpleanki.core.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {

    private fun folder(id: String, name: String) = Folder(id = id, name = name, lastModified = 0)

    @Test
    fun folder_upsert_marksDirty_andStampsTime_andObserves() = runTest {
        val dao = FakeFolderDao()
        val repo = FolderRepository(dao, now = { 12345L })
        repo.observeFolders().test {
            assertEquals(emptyList<Folder>(), awaitItem())
            repo.upsert(folder("f1", "Languages"))
            val folders = awaitItem()
            assertEquals(1, folders.size)
            assertEquals(12345L, folders.first().lastModified)
        }
        val dirty = dao.getDirty()
        assertEquals(1, dirty.size)
        assertTrue(dirty.first().dirty)
    }

    @Test
    fun folder_delete_isSoft_andHiddenFromObserve_butDirty() = runTest {
        val dao = FakeFolderDao()
        val repo = FolderRepository(dao, now = { 1L })
        repo.upsert(folder("f1", "A"))
        repo.delete("f1")
        repo.observeFolders().test {
            assertEquals(emptyList<Folder>(), awaitItem())
        }
        val deleted = dao.getById("f1")!!
        assertTrue(deleted.isDeleted)
        assertTrue(deleted.dirty)
    }

    @Test
    fun deck_observeByFolder_filters() = runTest {
        val dao = FakeDeckDao()
        val repo = DeckRepository(dao, now = { 1L })
        repo.upsert(Deck(id = "d1", name = "In", folderId = "f1", dateCreated = 0, lastModified = 0))
        repo.upsert(Deck(id = "d2", name = "Out", folderId = null, dateCreated = 0, lastModified = 0))
        repo.observeDecksInFolder("f1").test {
            val decks = awaitItem()
            assertEquals(listOf("d1"), decks.map { it.id })
        }
    }

    @Test
    fun card_getDue_returnsOnlyDueNonDeleted() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { 100L })
        repo.upsert(baseCard("c1", deck = "d1", due = 50))   // due
        repo.upsert(baseCard("c2", deck = "d1", due = 150))  // not due
        val due = repo.getDue("d1", at = 100L)
        assertEquals(listOf("c1"), due.map { it.id })
    }

    @Test
    fun card_save_doesNotRestampTime() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { 999L })
        repo.save(baseCard("c1", deck = "d1", due = 0).copy(lastModified = 42L))
        assertEquals(42L, dao.getById("c1")!!.lastModified)
        assertTrue(dao.getById("c1")!!.dirty)
    }

    @Test
    fun card_getById_missing_returnsNull() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { 0L })
        assertNull(repo.getById("nope"))
    }

    private fun baseCard(id: String, deck: String, due: Long) = Card(
        id = id, front = "f", back = "b", image = null, audioName = null, imagePath = null,
        audioPath = null, deckId = deck, dateCreated = 0, lastModified = 0, memorized = false,
        fsrsDue = due, fsrsStability = 0.0, fsrsDifficulty = 0.0, fsrsElapsedDays = 0.0,
        fsrsScheduledDays = 0.0, fsrsReps = 0, fsrsLapses = 0, fsrsState = CardState.New.value,
        fsrsLastReview = null, isDeleted = false, source = null, pairId = null, isReverse = false,
    )
}
