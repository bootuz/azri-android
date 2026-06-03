package nart.simpleanki.core.apkg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.media.FakeMediaUploader
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkgImportServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun service(): Triple<DefaultApkgImportService, FakeDeckDao, FakeCardDao> {
        val deckDao = FakeDeckDao(); val cardDao = FakeCardDao()
        val media = MediaManager(LocalMediaStore(tmp.newFolder(), Dispatchers.Unconfined), FakeMediaUploader())
        var seq = 0
        val svc = DefaultApkgImportService(
            unzipper = ApkgUnzipper(), detector = ApkgFormatDetector(), mediaReader = ApkgMediaReader(),
            media = media, deckRepository = DeckRepository(deckDao) { 1L },
            cardRepository = CardRepository(cardDao) { 1L },
            idGenerator = { "id${seq++}" }, now = { 1L },
        )
        return Triple(svc, deckDao, cardDao)
    }

    private val noteType = AnkiNoteType(1, "Basic", listOf("Front", "Back"), 0)
    private val notes = listOf(
        AnkiNote(1, "g1", 1, listOf("F1", "B1"), emptyList()),
        AnkiNote(2, "g2", 1, listOf("", "B2"), emptyList()),     // empty front -> skipped
    )

    @Test fun previewCards_mapsFields_skipsEmpty_noMediaWhenDisabled() = runTest {
        val (svc, _, _) = service()
        val cards = svc.previewCards(notes, noteType, frontIdx = 0, backIdx = 1, media = emptyMap(), importMedia = false)
        assertEquals(1, cards.size)
        assertEquals("F1", cards[0].front); assertEquals("B1", cards[0].back)
        assertNull(cards[0].imageName)
    }

    @Test fun import_onlySelectedCards_intoOneNewDeck_withApkgSource() = runTest {
        val (svc, deckDao, cardDao) = service()
        val cards = listOf(
            ApkgPreviewCard("F1", "B1", imageName = null, audioName = null, selected = true),
            ApkgPreviewCard("F2", "B2", imageName = null, audioName = null, selected = false), // skipped
        )
        svc.import(cards, deckName = "MyDeck")
        val decks = deckDao.observeAll().first()
        assertEquals(listOf("MyDeck"), decks.map { it.name })
        val saved = cardDao.observeAll().first()
        assertEquals(listOf("F1"), saved.map { it.front })   // only the selected card persisted
        val card = saved.single()
        assertEquals("apkg", card.source)
        assertEquals(0, card.fsrsState)
        assertEquals(decks.single().id, card.deckId)         // distinct ids, linkage holds
    }
}
