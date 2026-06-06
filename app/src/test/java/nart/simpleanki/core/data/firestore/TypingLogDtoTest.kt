package nart.simpleanki.core.data.firestore

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingLogDtoTest {
    @Test fun roundTrip_preservesFields() {
        val domain = TypingLog(
            id = "t1", cardId = "c1", deckId = "d1", correct = true, typedText = "café", timestamp = 1_700_000L,
        )
        assertEquals(domain, TypingLogDto.fromDomain(domain).toDomain())
    }
}
