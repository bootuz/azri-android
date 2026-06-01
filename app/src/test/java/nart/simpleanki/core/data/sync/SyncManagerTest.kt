package nart.simpleanki.core.data.sync

import com.google.firebase.Timestamp
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.local.FolderEntity
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeFolderDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SyncManagerTest {

    private class FakeRemote(
        var folders: MutableList<FolderDto> = mutableListOf(),
        var decks: MutableList<DeckDto> = mutableListOf(),
        var cards: MutableList<CardDto> = mutableListOf(),
    ) : RemoteSyncSource {
        val pushedFolders = mutableListOf<FolderDto>()
        override suspend fun fetchFolders(uid: String) = folders
        override suspend fun pushFolders(uid: String, dtos: List<FolderDto>) { pushedFolders += dtos }
        override suspend fun fetchDecks(uid: String) = decks
        override suspend fun pushDecks(uid: String, dtos: List<DeckDto>) {}
        override suspend fun fetchCards(uid: String) = cards
        override suspend fun pushCards(uid: String, dtos: List<CardDto>) {}
    }

    private fun ts(millis: Long) = Timestamp(Date(millis))

    @Test
    fun shouldApplyRemote_lastWriteWins() {
        assertTrue(SyncManager.shouldApplyRemote(null, 100))
        assertTrue(SyncManager.shouldApplyRemote(100, 200))
        assertFalse(SyncManager.shouldApplyRemote(200, 100))
        assertFalse(SyncManager.shouldApplyRemote(100, 100))
    }

    @Test
    fun push_sendsDirty_thenClearsDirty() = runTest {
        val folderDao = FakeFolderDao()
        folderDao.upsertAll(listOf(FolderEntity(id = "f1", name = "A", lastModified = 5, dirty = true)))
        val remote = FakeRemote()
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), remote)

        sync.sync("u1")

        assertEquals(listOf("f1"), remote.pushedFolders.map { it.id })
        assertFalse(folderDao.getById("f1")!!.dirty)
    }

    @Test
    fun pull_appliesNewerRemote_andSkipsOlder() = runTest {
        val folderDao = FakeFolderDao()
        folderDao.upsertAll(
            listOf(
                FolderEntity(id = "newer", name = "local", lastModified = 100),
                FolderEntity(id = "older", name = "local", lastModified = 100),
            )
        )
        val remote = FakeRemote(
            folders = mutableListOf(
                FolderDto(id = "newer", name = "remote-wins", lastModified = ts(200)),
                FolderDto(id = "older", name = "remote-loses", lastModified = ts(50)),
                FolderDto(id = "fresh", name = "remote-new", lastModified = ts(10)),
            )
        )
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), remote)

        sync.sync("u1")

        assertEquals("remote-wins", folderDao.getById("newer")!!.name)
        assertEquals("local", folderDao.getById("older")!!.name)
        assertEquals("remote-new", folderDao.getById("fresh")!!.name)
    }

    @Test
    fun pull_propagatesSoftDelete_fromRemote() = runTest {
        val folderDao = FakeFolderDao()
        folderDao.upsertAll(listOf(FolderEntity(id = "f1", name = "local", lastModified = 1)))
        val remote = FakeRemote(
            folders = mutableListOf(
                FolderDto(id = "f1", name = "local", lastModified = ts(2), isDeleted = true),
            )
        )
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), remote)

        sync.sync("u1")

        assertTrue(folderDao.getById("f1")!!.isDeleted)
    }
}
