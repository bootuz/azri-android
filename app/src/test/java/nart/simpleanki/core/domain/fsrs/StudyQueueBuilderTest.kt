package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ReviewCardFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyQueueBuilderTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun card(
        id: String,
        state: Int = CardState.New.value,
        due: Long = now,
        created: Long = now,
        deleted: Boolean = false,
        reverse: Boolean = false,
        difficulty: Double = 0.0,
    ) = Card(
        id = id, front = "f", back = "b", deckId = "d", dateCreated = created, lastModified = created,
        fsrsDue = due, fsrsState = state, isDeleted = deleted, isReverse = reverse, fsrsDifficulty = difficulty,
    )

    @Test
    fun studyQueue_dueBeforeNew_andRespectsLimits() {
        val cards = listOf(
            card("due1", state = CardState.Review.value, due = now - 2 * day),
            card("due2", state = CardState.Review.value, due = now - 1 * day),
            card("notDue", state = CardState.Review.value, due = now + day),
            card("new1", state = CardState.New.value, created = now - 5 * day),
            card("new2", state = CardState.New.value, created = now - 4 * day),
            card("new3", state = CardState.New.value, created = now - 3 * day),
        )
        val queue = StudyQueueBuilder.buildStudyQueue(cards, now, newLimit = 2, reviewLimit = 5)
        assertEquals(listOf("due1", "due2", "new1", "new2"), queue.map { it.id })
    }

    @Test
    fun studyQueue_excludesNotDue_andDeleted() {
        val cards = listOf(
            card("due", state = CardState.Review.value, due = now - day),
            card("future", state = CardState.Review.value, due = now + day),
            card("gone", state = CardState.Review.value, due = now - day, deleted = true),
        )
        val queue = StudyQueueBuilder.buildStudyQueue(cards, now, newLimit = 10, reviewLimit = 10)
        assertEquals(listOf("due"), queue.map { it.id })
    }

    @Test
    fun studyQueue_reviewLimit_capsDueCards() {
        val cards = (1..10).map { card("d$it", state = CardState.Review.value, due = now - it * day) }
        val queue = StudyQueueBuilder.buildStudyQueue(cards, now, newLimit = 0, reviewLimit = 3)
        assertEquals(3, queue.size)
    }

    @Test
    fun reviewQueue_filtersByDirection() {
        val cards = listOf(
            card("orig", reverse = false),
            card("rev", reverse = true),
            card("gone", reverse = true, deleted = true),
        )
        assertEquals(listOf("orig", "rev"), StudyQueueBuilder.buildReviewQueue(cards, ReviewCardFilter.All).map { it.id })
        assertEquals(listOf("orig"), StudyQueueBuilder.buildReviewQueue(cards, ReviewCardFilter.OriginalsOnly).map { it.id })
        assertEquals(listOf("rev"), StudyQueueBuilder.buildReviewQueue(cards, ReviewCardFilter.ReversesOnly).map { it.id })
    }

    @Test
    fun shuffleSeed_isDeterministic() {
        val cards = (1..8).map { card("c$it", state = CardState.Review.value, due = now - it * day) }
        val a = StudyQueueBuilder.buildStudyQueue(cards, now, 0, 10, shuffleSeed = 42)
        val b = StudyQueueBuilder.buildStudyQueue(cards, now, 0, 10, shuffleSeed = 42)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertTrue(a.size == 8)
    }

    // --- sort() ---

    @Test
    fun sort_dueDate_ascending() {
        val cards = listOf(
            card("late", due = now + 3 * day),
            card("early", due = now - 2 * day),
            card("mid", due = now),
        )
        val sorted = StudyQueueBuilder.sort(cards, QueueSortOrder.DueDate, shuffleSeed = 0)
        assertEquals(listOf("early", "mid", "late"), sorted.map { it.id })
    }

    @Test
    fun sort_difficulty_descending_hardestFirst() {
        val cards = listOf(
            card("easy", difficulty = 2.0),
            card("hard", difficulty = 9.0),
            card("medium", difficulty = 5.0),
        )
        val sorted = StudyQueueBuilder.sort(cards, QueueSortOrder.Difficulty, shuffleSeed = 0)
        assertEquals(listOf("hard", "medium", "easy"), sorted.map { it.id })
    }

    @Test
    fun sort_shuffle_isDeterministicPerSeed_andDiffersAcrossSeeds() {
        val cards = (1..12).map { card("c$it") }
        val a = StudyQueueBuilder.sort(cards, QueueSortOrder.Shuffle, shuffleSeed = 7).map { it.id }
        val again = StudyQueueBuilder.sort(cards, QueueSortOrder.Shuffle, shuffleSeed = 7).map { it.id }
        val other = StudyQueueBuilder.sort(cards, QueueSortOrder.Shuffle, shuffleSeed = 99).map { it.id }
        assertEquals(a, again)                      // same seed → same order
        assertEquals(cards.size, a.size)            // keeps every card
        assertTrue("different seed → different order", a != other)
    }
}
