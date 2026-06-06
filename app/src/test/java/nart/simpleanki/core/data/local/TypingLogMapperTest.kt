package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingLogMapperTest {
    @Test fun roundTrip_preservesFields() {
        val domain = TypingLog(
            id = "t1", cardId = "c1", deckId = "d1", correct = true, typedText = "café", timestamp = 1_700L,
        )
        val back = domain.toEntity(dirty = false).toDomain()
        assertEquals(domain, back)
    }

    @Test fun toEntity_defaultsDirtyTrue() {
        val e = TypingLog(id = "t1", cardId = "c1", deckId = "d1", correct = false, typedText = "x", timestamp = 1L).toEntity()
        assertTrue(e.dirty)
    }
}
