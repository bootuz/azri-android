package nart.simpleanki.feature.cardform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.media.FakeMediaUploader
import nart.simpleanki.core.data.media.MediaRef
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardFormViewModelTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun ids(vararg values: String): () -> String {
        val iter = values.iterator()
        return { iter.next() }
    }

    @Test
    fun canSave_requiresBothSides() {
        val repo = CardRepository(FakeCardDao(), now = { now })
        val vm = CardFormViewModel("d1", repo, FakeMediaUploader(),now = { now })
        assertFalse(vm.uiState.value.canSave)
        vm.onFrontChange("hello")
        assertFalse(vm.uiState.value.canSave)
        vm.onBackChange("hola")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun save_newCard_persistsSingleCard() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, FakeMediaUploader(),idGenerator = ids("card-1"), now = { now })
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.save(); runCurrent()

        val cards = dao.observeByDeck("d1").first()
        assertEquals(1, cards.size)
        assertEquals("hello", cards.first().front)
        assertEquals(CardState.New.value, cards.first().fsrsState)
        assertNull(cards.first().pairId)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun save_withReverse_createsPairedSwappedCards() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, FakeMediaUploader(),idGenerator = ids("base", "rev"), now = { now })
        vm.onFrontChange("dog"); vm.onBackChange("perro"); vm.onToggleReverse(true)
        vm.save(); runCurrent()

        val cards = dao.observeByDeck("d1").first().sortedBy { it.isReverse }
        assertEquals(2, cards.size)
        val original = cards[0]
        val reverse = cards[1]
        assertFalse(original.isReverse)
        assertEquals("dog", original.front)
        assertEquals("base", original.pairId)
        assertTrue(reverse.isReverse)
        assertEquals("perro", reverse.front)   // swapped
        assertEquals("dog", reverse.back)
        assertEquals("base", reverse.pairId)    // shared pair id
    }

    @Test
    fun imagePicked_uploads_andSavesImageRefOnCard() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val media = FakeMediaUploader(Result.success(MediaRef("pic.jpg", "users/u/images/pic.jpg")))
        val vm = CardFormViewModel("d1", repo, media, idGenerator = ids("c-1"), now = { now })
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.onImagePicked(byteArrayOf(1, 2, 3)); runCurrent()
        assertEquals(1, media.uploadCalls)
        assertEquals("pic.jpg", vm.uiState.value.imageName)
        assertFalse(vm.uiState.value.uploadingImage)

        vm.save(); runCurrent()
        val saved = dao.observeByDeck("d1").first().first()
        assertEquals("pic.jpg", saved.image)
        assertEquals("users/u/images/pic.jpg", saved.imagePath)
    }

    @Test
    fun audioRecorded_uploads_andSavesAudioRefOnOriginalOnly() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val media = FakeMediaUploader()
        media.audioUploadResult = Result.success(MediaRef("clip.m4a", "users/u/audio/clip.m4a"))
        val vm = CardFormViewModel("d1", repo, media, idGenerator = ids("base", "rev"), now = { now })
        vm.onFrontChange("dog"); vm.onBackChange("perro"); vm.onToggleReverse(true)
        vm.onAudioRecorded(byteArrayOf(9, 9)); runCurrent()
        assertEquals(1, media.audioUploadCalls)
        assertEquals("clip.m4a", vm.uiState.value.audioName)

        vm.save(); runCurrent()
        val cards = dao.observeByDeck("d1").first().sortedBy { it.isReverse }
        assertEquals("users/u/audio/clip.m4a", cards[0].audioPath) // original has audio
        assertNull(cards[1].audioPath)                              // reverse is audio-free
    }

    @Test
    fun edit_existingCard_updatesInPlace() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        repo.upsert(
            Card(id = "c1", front = "old", back = "viejo", deckId = "d1", dateCreated = now,
                lastModified = now, fsrsDue = now, fsrsState = CardState.Review.value),
        )
        val vm = CardFormViewModel("d1", repo, FakeMediaUploader(),editingCardId = "c1", now = { now })
        runCurrent()
        assertEquals("old", vm.uiState.value.front)
        assertTrue(vm.uiState.value.isEdit)

        vm.onFrontChange("new")
        vm.save(); runCurrent()

        val cards = dao.observeByDeck("d1").first()
        assertEquals(1, cards.size)
        assertEquals("new", cards.first().front)
        assertEquals(CardState.Review.value, cards.first().fsrsState) // FSRS state preserved
    }
}
