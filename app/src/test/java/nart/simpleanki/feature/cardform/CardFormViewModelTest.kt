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
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CardFormViewModelTest {

    private val now = 1_700_000_000_000L

    @get:Rule val tmp = TemporaryFolder()
    private fun media() = MediaManager(LocalMediaStore(tmp.newFolder(), Dispatchers.Unconfined), FakeMediaUploader())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun ids(vararg values: String): () -> String {
        val iter = values.iterator()
        return { iter.next() }
    }

    @Test
    fun canSave_requiresBothSides() {
        val repo = CardRepository(FakeCardDao(), now = { now })
        val vm = CardFormViewModel("d1", repo, media(), now = { now })
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
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("card-1"), now = { now })
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.save(); runCurrent()

        val cards = dao.observeByDeck("d1").first()
        assertEquals(1, cards.size)
        assertEquals("hello", cards.first().front)
        assertEquals(CardState.New.value, cards.first().fsrsState)
        assertNull(cards.first().pairId)
        // New-card save keeps the editor open: tick bumps and inputs reset for the next card.
        assertEquals(1, vm.uiState.value.savedTick)
        assertEquals("", vm.uiState.value.front)
        assertEquals("", vm.uiState.value.back)
        assertFalse(vm.uiState.value.finished)
    }

    @Test
    fun save_newCard_resetsInputs_andBumpsTickEachSave() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        // First save toggles reverse (consumes two ids: original + reverse), second is a plain card.
        val vm = CardFormViewModel(
            "d1", repo, media(),
            idGenerator = ids("c-1", "c-2", "c-3"), now = { now },
        )
        vm.onFrontChange("a"); vm.onBackChange("1"); vm.onToggleReverse(true)
        vm.save(); runCurrent()
        // Reverse toggle and all inputs are cleared after the first save.
        assertEquals(1, vm.uiState.value.savedTick)
        assertFalse(vm.uiState.value.createReverse)
        assertEquals("", vm.uiState.value.front)

        vm.onFrontChange("b"); vm.onBackChange("2")
        vm.save(); runCurrent()
        assertEquals(2, vm.uiState.value.savedTick)
        assertEquals(3, dao.observeByDeck("d1").first().size) // 2 from reverse pair + 1 plain
    }

    @Test
    fun save_withReverse_createsPairedSwappedCards() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("base", "rev"), now = { now })
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
    fun imagePicked_savesLocally_withNoCloudPath() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("c-1"), now = { now })
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.onImagePicked(byteArrayOf(1, 2, 3)); runCurrent()
        assertNotNull(vm.uiState.value.imageName)
        assertNull(vm.uiState.value.imagePath)
        assertFalse(vm.uiState.value.uploadingImage)

        vm.save(); runCurrent()
        val saved = dao.observeByDeck("d1").first().first()
        assertNotNull(saved.image)      // filename persisted on the card
        assertNull(saved.imagePath)     // local-only: no cloud path yet
    }

    @Test
    fun audioRecorded_savesLocally_onOriginalOnly() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("base", "rev"), now = { now })
        vm.onFrontChange("dog"); vm.onBackChange("perro"); vm.onToggleReverse(true)
        vm.onAudioRecorded(byteArrayOf(9, 9)); runCurrent()
        assertNotNull(vm.uiState.value.audioName)
        assertNull(vm.uiState.value.audioPath)

        vm.save(); runCurrent()
        val cards = dao.observeByDeck("d1").first()
        assertNotNull(cards.first { !it.isReverse }.audioName)   // original keeps audio
        assertNull(cards.first { it.isReverse }.audioName)        // reverse is audio-free
    }

    @Test
    fun edit_existingCard_updatesInPlace() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        repo.upsert(
            Card(id = "c1", front = "old", back = "viejo", deckId = "d1", dateCreated = now,
                lastModified = now, fsrsDue = now, fsrsState = CardState.Review.value),
        )
        val vm = CardFormViewModel("d1", repo, media(), editingCardId = "c1", now = { now })
        runCurrent()
        assertEquals("old", vm.uiState.value.front)
        assertTrue(vm.uiState.value.isEdit)

        vm.onFrontChange("new")
        vm.save(); runCurrent()

        val cards = dao.observeByDeck("d1").first()
        assertEquals(1, cards.size)
        assertEquals("new", cards.first().front)
        assertEquals(CardState.Review.value, cards.first().fsrsState) // FSRS state preserved
        // Editing one card signals the screen to close (no reset / no toast loop).
        assertTrue(vm.uiState.value.finished)
        assertEquals(0, vm.uiState.value.savedTick)
    }
}
