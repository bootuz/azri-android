package nart.simpleanki.core.data.sync

import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.firestore.ReviewLogDto
import nart.simpleanki.core.data.local.CardEntity
import nart.simpleanki.core.data.local.FolderEntity
import nart.simpleanki.core.data.media.FakeMediaUploader
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.local.toDomain
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeFolderDao
import nart.simpleanki.core.data.repository.FakeReviewLogDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

class SyncManagerTest {

    private class FakeRemote(
        var folders: MutableList<FolderDto> = mutableListOf(),
        var decks: MutableList<DeckDto> = mutableListOf(),
        var cards: MutableList<CardDto> = mutableListOf(),
        var reviewLogs: MutableList<ReviewLogDto> = mutableListOf(),
    ) : RemoteSyncSource {
        val pushedFolders = mutableListOf<FolderDto>()
        val pushedCards = mutableListOf<CardDto>()
        val pushedReviewLogs = mutableListOf<ReviewLogDto>()
        override suspend fun fetchFolders(uid: String) = folders
        override suspend fun pushFolders(uid: String, dtos: List<FolderDto>) { pushedFolders += dtos }
        override suspend fun fetchDecks(uid: String) = decks
        override suspend fun pushDecks(uid: String, dtos: List<DeckDto>) {}
        override suspend fun fetchCards(uid: String) = cards
        override suspend fun pushCards(uid: String, dtos: List<CardDto>) { pushedCards += dtos }
        override suspend fun fetchReviewLogs(uid: String) = reviewLogs
        override suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>) { pushedReviewLogs += dtos }
    }

    private fun ts(millis: Long) = Timestamp(Date(millis))

    @get:Rule val tmp = TemporaryFolder()

    private fun media(uploader: FakeMediaUploader = FakeMediaUploader()) =
        Pair(MediaManager(LocalMediaStore(tmp.newFolder(), Dispatchers.Unconfined), uploader), uploader)

    private fun reviewLogEntity(id: String, dirty: Boolean) = nart.simpleanki.core.data.local.ReviewLogEntity(
        id = id, cardId = "c1", rating = 3, state = 2, due = 0, stability = 1.0, difficulty = 5.0,
        elapsedDays = 0.0, lastElapsedDays = 0.0, scheduledDays = 0.0, review = 1_000, dirty = dirty,
    )

    private fun cardEntity(
        id: String, image: String? = null, imagePath: String? = null,
        lastModified: Long = 1, dirty: Boolean = false,
    ) = CardEntity(
        id = id, front = "f", back = "b", image = image, audioName = null,
        imagePath = imagePath, audioPath = null, deckId = "d1",
        dateCreated = 0, lastModified = lastModified, memorized = false,
        fsrsDue = 0, fsrsStability = 0.0, fsrsDifficulty = 0.0, fsrsElapsedDays = 0.0,
        fsrsScheduledDays = 0.0, fsrsReps = 0, fsrsLapses = 0, fsrsState = 0,
        fsrsLastReview = null, isDeleted = false, source = null, pairId = null,
        isReverse = false, dirty = dirty,
    )

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
        val (m, _) = media()
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), remote, m)

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
        val (m, _) = media()
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), remote, m)

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
        val (m, _) = media()
        val sync = SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), remote, m)

        sync.sync("u1")

        assertTrue(folderDao.getById("f1")!!.isDeleted)
    }

    @Test
    fun push_uploadsLocalOnlyMedia_andPersistsCloudPath() = runTest {
        val cardDao = FakeCardDao()
        val (m, up) = media()
        // Seed a local-only image via the manager, then a dirty card referencing it (no cloud path).
        val name = m.saveImage(byteArrayOf(1, 2, 3))
        cardDao.upsertAll(listOf(cardEntity(id = "c1", image = name, imagePath = null, dirty = true)))
        val remote = FakeRemote()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, FakeReviewLogDao(), remote, m)

        sync.sync("u1")

        assertEquals(1, up.uploadPathCalls)                 // uploaded once
        val saved = cardDao.getById("c1")!!
        assertNotNull(saved.imagePath)                       // cloud path now persisted locally
        assertFalse(saved.dirty)                             // and dirty cleared
    }

    @Test
    fun push_skipsUpload_whenNoLocalMedia() = runTest {
        val cardDao = FakeCardDao()
        val (m, up) = media()
        cardDao.upsertAll(listOf(cardEntity(id = "c1", image = null, imagePath = null, dirty = true)))
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, FakeReviewLogDao(), FakeRemote(), m)

        sync.sync("u1")

        assertEquals(0, up.uploadPathCalls)
        assertFalse(cardDao.getById("c1")!!.dirty)
    }

    @Test
    fun pull_prefetchesRemoteMedia_locally() = runTest {
        val cardDao = FakeCardDao()
        val up = FakeMediaUploader().apply { uploaded["pic.jpg"] = byteArrayOf(8, 8) }
        val store = LocalMediaStore(tmp.newFolder(), Dispatchers.Unconfined)
        val m = MediaManager(store, up)
        val remote = FakeRemote(
            cards = mutableListOf(
                CardDto(id = "c1", image = "pic.jpg", imagePath = "users/u/images/pic.jpg", lastModified = ts(100)),
            ),
        )
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, FakeReviewLogDao(), remote, m)

        sync.sync("u1")

        assertTrue(store.exists("pic.jpg"))                  // prefetched + cached locally
        assertEquals(1, up.downloadCalls)
    }

    @Test
    fun push_uploadFailure_keepsCardDirty_andSkipsPush() = runTest {
        val cardDao = FakeCardDao()
        val (m, up) = media()
        up.uploadPathResult = { Result.failure(IllegalStateException("offline")) }
        val name = m.saveImage(byteArrayOf(1, 2, 3))
        cardDao.upsertAll(listOf(cardEntity(id = "c1", image = name, imagePath = null, dirty = true)))
        val remote = FakeRemote()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, FakeReviewLogDao(), remote, m)

        sync.sync("u1")

        assertTrue(cardDao.getById("c1")!!.dirty)        // stays dirty for retry
        assertNull(cardDao.getById("c1")!!.imagePath)    // no cloud path persisted
        assertTrue(remote.pushedCards.none { it.id == "c1" })  // not pushed this cycle
    }

    @Test
    fun reviewLogs_pushDirty_thenClearDirty() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(reviewLogEntity("l1", dirty = true)))
        val remote = FakeRemote()
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, remote, m)

        sync.sync("u1")

        assertEquals(listOf("l1"), remote.pushedReviewLogs.map { it.id })
        assertTrue(logDao.getDirty().isEmpty())
    }

    @Test
    fun reviewLogs_pullUnionsRemote_andSkipsExisting() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(reviewLogEntity("l1", dirty = false)))
        val remote = FakeRemote(reviewLogs = mutableListOf(
            ReviewLogDto.fromDomain(reviewLogEntity("l1", dirty = false).toDomain()),
            ReviewLogDto.fromDomain(reviewLogEntity("l2", dirty = false).toDomain()),
        ))
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, remote, m)

        sync.sync("u1")

        assertEquals(setOf("l1", "l2"), logDao.getAllIds().toSet())
        // Prove SyncManager's own filter (not just the DAO's IGNORE) skipped the duplicate l1:
        // only the seed l1 and the synced l2 were ever forwarded to insertAll — not l1 twice.
        assertEquals(listOf("l1", "l2"), logDao.inserted.map { it.id })
    }
}
