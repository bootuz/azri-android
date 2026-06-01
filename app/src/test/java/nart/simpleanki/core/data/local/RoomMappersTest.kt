package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.DeckLayout
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.ReviewCardFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMappersTest {

    @Test
    fun card_roundTrips_throughEntity() {
        val card = Card(
            id = "c1", front = "Q", back = "A", image = null, audioName = null,
            imagePath = null, audioPath = null, deckId = "d1",
            dateCreated = 1000, lastModified = 2000, memorized = false,
            fsrsDue = 3000, fsrsStability = 1.5, fsrsDifficulty = 5.0,
            fsrsElapsedDays = 1.0, fsrsScheduledDays = 2.0, fsrsReps = 1, fsrsLapses = 0,
            fsrsState = CardState.Learning.value, fsrsLastReview = 1500,
            isDeleted = false, source = null, pairId = null, isReverse = false,
        )
        assertEquals(card, card.toEntity().toDomain())
    }

    @Test
    fun deck_roundTrips_throughEntity() {
        val deck = Deck(
            id = "d1", name = "Deck", color = ColorOption.Blue, autoplay = false, shuffled = true,
            layout = DeckLayout.All, reviewFilter = ReviewCardFilter.OriginalsOnly,
            folderId = null, dateCreated = 1, lastModified = 2, isDeleted = false,
        )
        assertEquals(deck, deck.toEntity().toDomain())
    }

    @Test
    fun folder_roundTrips_throughEntity() {
        val folder = Folder(id = "f1", name = "F", emoji = "📁", lastModified = 9)
        assertEquals(folder, folder.toEntity().toDomain())
    }

    @Test
    fun toEntity_setsDirtyFlag_whenRequested() {
        val card = Card(
            id = "c", front = "", back = "", image = null, audioName = null, imagePath = null,
            audioPath = null, deckId = "d", dateCreated = 0, lastModified = 0, memorized = false,
            fsrsDue = 0, fsrsStability = 0.0, fsrsDifficulty = 0.0, fsrsElapsedDays = 0.0,
            fsrsScheduledDays = 0.0, fsrsReps = 0, fsrsLapses = 0, fsrsState = 0,
            fsrsLastReview = null, isDeleted = false, source = null, pairId = null, isReverse = false,
        )
        assertTrue(card.toEntity(dirty = true).dirty)
        assertEquals(false, card.toEntity().dirty)
    }
}
