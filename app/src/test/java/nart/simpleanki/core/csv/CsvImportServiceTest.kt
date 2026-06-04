package nart.simpleanki.core.csv

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvImportServiceTest {

    private fun service(): Triple<DefaultCsvImportService, FakeDeckDao, FakeCardDao> {
        val deckDao = FakeDeckDao(); val cardDao = FakeCardDao()
        var seq = 0
        val svc = DefaultCsvImportService(
            deckRepository = DeckRepository(deckDao) { 1L },
            cardRepository = CardRepository(cardDao) { 1L },
            appContext = null,
            idGenerator = { "id${seq++}" },
            now = { 1L },
        )
        return Triple(svc, deckDao, cardDao)
    }

    private val parsed = ParsedCsv(
        headers = listOf("front", "back"),
        rows = listOf(
            listOf("hola", "hello"),
            listOf("  adios  ", "  bye  "),  // trimmed
            listOf("", "orphan"),            // empty front -> skipped
            listOf("solo", ""),              // empty back  -> skipped
        ),
    )

    @Test fun previewCards_trims_andSkipsEmptySides() {
        val (svc, _, _) = service()
        val cards = svc.previewCards(parsed, frontCol = 0, backCol = 1)
        assertEquals(2, cards.size)
        assertEquals("hola", cards[0].front); assertEquals("hello", cards[0].back)
        assertEquals("adios", cards[1].front); assertEquals("bye", cards[1].back)
    }

    @Test fun previewCards_respectsColumnIndices() {
        val (svc, _, _) = service()
        val cards = svc.previewCards(parsed, frontCol = 1, backCol = 0)
        assertEquals("hello", cards[0].front); assertEquals("hola", cards[0].back)
    }

    @Test fun validate_throwsOnNoRows() {
        val (svc, _, _) = service()
        assertThrows(CsvImportError.EmptyFile::class.java) {
            svc.validate(ParsedCsv(listOf("a", "b"), emptyList()))
        }
    }

    @Test fun validate_throwsOnSingleColumn() {
        val (svc, _, _) = service()
        assertThrows(CsvImportError.TooFewColumns::class.java) {
            svc.validate(ParsedCsv(listOf("only"), listOf(listOf("x"))))
        }
    }

    @Test fun import_onlySelected_intoOneNewDeck_withCsvSource() = runTest {
        val (svc, deckDao, cardDao) = service()
        val cards = listOf(
            CsvPreviewCard("F1", "B1", selected = true),
            CsvPreviewCard("F2", "B2", selected = false),  // skipped
        )
        svc.import(cards, deckName = "MyDeck")
        val decks = deckDao.observeAll().first()
        assertEquals(listOf("MyDeck"), decks.map { it.name })
        val saved = cardDao.observeAll().first()
        assertEquals(listOf("F1"), saved.map { it.front })
        val card = saved.single()
        assertEquals("csv", card.source)
        assertEquals(0, card.fsrsState)
        assertEquals(decks.single().id, card.deckId)
    }
}
