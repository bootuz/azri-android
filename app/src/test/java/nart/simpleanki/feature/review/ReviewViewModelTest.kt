package nart.simpleanki.feature.review

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
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.ReviewCardFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(
        id: String,
        deckId: String,
        reverse: Boolean = false,
        memorized: Boolean = false,
        deleted: Boolean = false,
    ) = Card(
        id = id, front = "f", back = "b", deckId = deckId,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.Review.value,
        isReverse = reverse, memorized = memorized, isDeleted = deleted,
    )

    @Test
    fun deckReview_appliesDeckFilter_andExcludesMemorizedAndDeleted() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(
            Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now, reviewFilter = ReviewCardFilter.OriginalsOnly),
        )
        cardRepo.upsert(card("orig", "A", reverse = false))
        cardRepo.upsert(card("rev", "A", reverse = true))                  // excluded: OriginalsOnly
        cardRepo.upsert(card("mem", "A", memorized = true))                // excluded: memorized
        cardRepo.upsert(card("gone", "A", deleted = true))                 // excluded: deleted (filtered at the DAO layer)

        val vm = ReviewViewModel("A", null, cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(listOf("orig"), s.cards.map { it.id })
    }

    @Test
    fun folderReview_aggregatesAcrossFoldersDecks_bothDirections() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", folderId = "F", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "B", name = "B", folderId = "F", dateCreated = now, lastModified = now))
        deckRepo.upsert(Deck(id = "C", name = "C", folderId = null, dateCreated = now, lastModified = now))
        cardRepo.upsert(card("a1", "A"))
        cardRepo.upsert(card("b1", "B", reverse = true))
        cardRepo.upsert(card("c1", "C"))                                   // excluded: not in folder F

        val vm = ReviewViewModel(null, "F", cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val s = vm.uiState.value
        assertFalse(s.loading)
        // Folder review uses ReviewCardFilter.All (both directions), across F's decks only.
        // Order is shuffled, so compare as a set.
        assertEquals(setOf("a1", "b1"), s.cards.map { it.id }.toSet())
    }

    @Test
    fun emptyDeck_yieldsEmptyPool() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))

        val vm = ReviewViewModel("A", null, cardRepo, deckRepo, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertFalse(vm.uiState.value.loading)
        assertEquals(emptyList<String>(), vm.uiState.value.cards.map { it.id })
    }
}
