package nart.simpleanki.feature.study

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudyContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val card = Card(
        id = "c1", front = "hola", back = "hello", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    @Test
    fun front_showsQuestion_andFlipsOnTap() {
        var revealed = false
        composeRule.setContent {
            StudyContent(
                state = StudyUiState(loading = false, current = card, isRevealed = false, remaining = 1),
                onReveal = { revealed = true },
                onRate = {},
                onDone = {},
            )
        }
        // The card flips on tap (no "Show answer" button) — tapping the question reveals the answer.
        composeRule.onNodeWithText("hola").assertIsDisplayed().performClick()
        assertTrue(revealed)
    }

    @Test
    fun revealed_showsAnswer_andRatingButtons() {
        var rated: Rating? = null
        composeRule.setContent {
            StudyContent(
                state = StudyUiState(loading = false, current = card, isRevealed = true, remaining = 1),
                onReveal = {},
                onRate = { rated = it },
                onDone = {},
            )
        }
        composeRule.onNodeWithText("hello").assertIsDisplayed()
        composeRule.onNodeWithText("Again").assertIsDisplayed()
        composeRule.onNodeWithText("Good").assertIsDisplayed().performClick()
        assertEquals(Rating.Good, rated)
    }

    @Test
    fun finished_showsSummary() {
        composeRule.setContent {
            StudyContent(
                state = StudyUiState(loading = false, finished = true, completed = 3, ratingCounts = mapOf(Rating.Good to 3)),
                onReveal = {},
                onRate = {},
                onDone = {},
            )
        }
        composeRule.onNodeWithText("Session Complete").assertIsDisplayed()
        composeRule.onNodeWithText("Finish").assertIsDisplayed()
    }
}
