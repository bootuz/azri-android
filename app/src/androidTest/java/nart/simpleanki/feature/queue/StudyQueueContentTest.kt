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

    private val spanish = DeckQueueItem("d1", "Spanish", ColorOption.Indigo, dueCount = 8, newCount = 4)
    private val languages = FolderQueueItem("f1", "Languages", deckCount = 1, dueCount = 8, newCount = 4)

    @Test
    fun hasWork_showsCount_andStudyButton() {
        var studied = false
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, readyCount = 12, newCount = 4, dueCount = 8, estimatedMinutes = 2,
                    decks = listOf(spanish),
                ),
                onStudyAll = { studied = true },
            )
        }
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("cards ready").assertIsDisplayed()
        composeRule.onNodeWithText("Study").performClick()
        assertTrue(studied)
    }

    @Test
    fun deckChip_startsDeckStudy() {
        var studiedDeck: String? = null
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false, readyCount = 12, decks = listOf(spanish)),
                onStudyAll = {},
                onStudyDeck = { studiedDeck = it },
            )
        }
        composeRule.onNodeWithText("Study by deck").assertIsDisplayed()
        composeRule.onNodeWithText("Spanish").assertIsDisplayed().performClick()
        assertEquals("d1", studiedDeck)
    }

    @Test
    fun foldersToggle_showsFolderChips_andStartsFolderStudy() {
        var studiedFolder: String? = null
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, readyCount = 12, decks = listOf(spanish), folders = listOf(languages),
                ),
                onStudyAll = {},
                onStudyFolder = { studiedFolder = it },
            )
        }
        // Toggle is present because folders exist; switch to Folders mode.
        composeRule.onNodeWithText("Folders").performClick()
        composeRule.onNodeWithText("Languages").assertIsDisplayed().performClick()
        assertEquals("f1", studiedFolder)
    }

    @Test
    fun queueList_showsCardFronts() {
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, readyCount = 1,
                    queueCards = listOf(QueueCardItem("c1", "hola", "Spanish", "Languages")),
                ),
                onStudyAll = {},
            )
        }
        composeRule.onNodeWithText("Queue").assertIsDisplayed()
        composeRule.onNodeWithText("hola").assertIsDisplayed()
    }

    @Test
    fun noWork_showsAllCaughtUp() {
        composeRule.setContent {
            StudyQueueContent(state = StudyQueueUiState(loading = false), onStudyAll = {})
        }
        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
    }
}
