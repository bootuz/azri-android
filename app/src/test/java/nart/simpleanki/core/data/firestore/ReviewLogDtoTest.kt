package nart.simpleanki.core.data.firestore

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewLogDtoTest {

    @Test
    fun roundTrip_preservesIdCardIdRatingAndReview() {
        val log = ReviewLog(
            rating = Rating.Good,
            state = CardState.Review,
            due = 5_000L,
            stability = 1.5,
            difficulty = 6.0,
            elapsedDays = 2.0,
            lastElapsedDays = 1.0,
            scheduledDays = 4.0,
            review = 1_700_000_000_000L,
            id = "log-1",
            cardId = "card-7",
        )
        val back = ReviewLogDto.fromDomain(log).toDomain()
        assertEquals("log-1", back.id)
        assertEquals("card-7", back.cardId)
        assertEquals(Rating.Good, back.rating)
        assertEquals(1_700_000_000_000L, back.review)
        assertEquals(CardState.Review, back.state)
    }
}
