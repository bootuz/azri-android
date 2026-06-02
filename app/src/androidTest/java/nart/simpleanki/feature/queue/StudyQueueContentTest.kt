package nart.simpleanki.feature.queue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.model.ColorOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudyQueueContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hasWork_showsCount_andStudyAndOpenDeckWork() {
        var studied = false
        var openedDeck: String? = null
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, readyCount = 12, newCount = 4, dueCount = 8, estimatedMinutes = 2,
                    decks = listOf(DeckQueueItem("d1", "Spanish", ColorOption.Indigo, dueCount = 8, newCount = 4)),
                ),
                onStudyAll = { studied = true },
                onOpenDeck = { openedDeck = it },
            )
        }
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("cards ready").assertIsDisplayed()
        composeRule.onNodeWithText("Study").performClick()
        assertTrue(studied)
        composeRule.onNodeWithText("Spanish").assertIsDisplayed().performClick()
        assertEquals("d1", openedDeck)
    }

    @Test
    fun noWork_showsAllCaughtUp() {
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false),
                onStudyAll = {},
                onOpenDeck = {},
            )
        }
        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
    }
}
