package nart.simpleanki.core.data.firestore

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.DeckLayout
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreDtoMappersTest {

    @Test
    fun card_roundTrips_throughDto() {
        val card = Card(
            id = "card1",
            front = "Q",
            back = "A",
            image = "img.png",
            audioName = "a.mp3",
            imagePath = "users/u/images/img.png",
            audioPath = "users/u/audio/a.mp3",
            deckId = "deck1",
            dateCreated = 1_700_000_000_000,
            lastModified = 1_700_000_500_000,
            memorized = true,
            fsrsDue = 1_700_100_000_000,
            fsrsStability = 12.5,
            fsrsDifficulty = 5.25,
            fsrsElapsedDays = 3.0,
            fsrsScheduledDays = 7.0,
            fsrsReps = 4,
            fsrsLapses = 1,
            fsrsState = CardState.Review.value,
            fsrsLastReview = 1_700_050_000_000,
            isDeleted = false,
            source = "csv",
            pairId = "pair9",
            isReverse = true,
        )
        assertEquals(card, CardDto.fromDomain(card).toDomain())
    }

    @Test
    fun card_missingIsReverse_defaultsToFalse() {
        val dto = CardDto(id = "c", deckId = "d", isReverse = null)
        assertEquals(false, dto.toDomain().isReverse)
    }

    @Test
    fun deck_roundTrips_withEnumWireValues() {
        val deck = Deck(
            id = "deck1",
            name = "Spanish",
            color = ColorOption.Indigo,
            autoplay = true,
            shuffled = true,
            layout = DeckLayout.BackToFront,
            reviewFilter = ReviewCardFilter.ReversesOnly,
            folderId = "folder1",
            dateCreated = 1_700_000_000_000,
            lastModified = 1_700_000_500_000,
            isDeleted = false,
        )
        val dto = DeckDto.fromDomain(deck)
        assertEquals("indigo", dto.color)
        assertEquals("backToFront", dto.layout)
        assertEquals("reversesOnly", dto.reviewFilter)
        assertEquals(deck, dto.toDomain())
    }

    @Test
    fun deck_missingReviewFilter_defaultsToAll() {
        val dto = DeckDto(id = "d", name = "x", reviewFilter = null)
        assertEquals(ReviewCardFilter.All, dto.toDomain().reviewFilter)
    }

    @Test
    fun folder_roundTrips() {
        val folder = Folder(id = "f1", name = "Languages", emoji = "🌍", lastModified = 1_700_000_000_000)
        assertEquals(folder, FolderDto.fromDomain(folder).toDomain())
    }

    @Test
    fun reviewLog_roundTrips() {
        val log = ReviewLog(
            rating = Rating.Good,
            state = CardState.Review,
            due = 1_700_100_000_000,
            stability = 9.9,
            difficulty = 4.1,
            elapsedDays = 2.0,
            lastElapsedDays = 1.0,
            scheduledDays = 5.0,
            review = 1_700_050_000_000,
        )
        assertEquals(log, ReviewLogDto.fromDomain(log).toDomain())
    }
}
