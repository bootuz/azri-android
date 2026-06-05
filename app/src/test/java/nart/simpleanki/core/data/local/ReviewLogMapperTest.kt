package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLogMapperTest {

    @Test
    fun roundTrip_entityToDomainToEntity() {
        val log = ReviewLog(
            rating = Rating.Hard, state = CardState.Learning, due = 9_000L,
            stability = 2.0, difficulty = 5.0, elapsedDays = 1.0, lastElapsedDays = 0.5,
            scheduledDays = 3.0, review = 1_700_000_000_000L, id = "l1", cardId = "c1",
        )
        val entity = log.toEntity(dirty = true)
        assertEquals("l1", entity.id)
        assertEquals("c1", entity.cardId)
        assertTrue(entity.dirty)
        val back = entity.toDomain()
        assertEquals(log.copy(), back)   // all fields preserved
    }
}
