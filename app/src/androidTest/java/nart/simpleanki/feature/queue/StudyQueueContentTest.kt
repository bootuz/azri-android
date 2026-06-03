package nart.simpleanki.feature.queue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.fsrs.QueueSortOrder
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
        // The queue-list header is identified by its sort control (the "Study" label collides
        // with the hero Study button when there's work).
        composeRule.onNodeWithContentDescription("Sort cards").assertIsDisplayed()
        composeRule.onNodeWithText("hola").assertIsDisplayed()
    }

    @Test
    fun sortMenu_opens_andSelectingInvokesCallback() {
        var picked: QueueSortOrder? = null
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, readyCount = 1,
                    queueCards = listOf(QueueCardItem("c1", "hola", "Spanish", null)),
                    sortOrder = QueueSortOrder.DueDate,
                ),
                onStudyAll = {},
                onSortChange = { picked = it },
            )
        }
        composeRule.onNodeWithContentDescription("Sort cards").performClick()
        composeRule.onNodeWithText("Difficulty").performClick()
        assertEquals(QueueSortOrder.Difficulty, picked)
    }

    @Test
    fun noWork_butHasCards_showsAllCaughtUp() {
        // Returning user who cleared today's queue — has cards, nothing due.
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false, hasAnyCards = true),
                onStudyAll = {},
            )
        }
        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test
    fun goalTrackingOff_showsSetupPrompt_notProgress() {
        composeRule.setContent {
            // Goal tracking off (the default) for a new user → "set up" nudge, no progress bar.
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, hasAnyCards = false,
                    dailyGoalEnabled = false, goalTotal = 30, studiedToday = 0,
                ),
                onStudyAll = {},
            )
        }
        composeRule.onNodeWithText("Set up your daily goal").assertIsDisplayed()
        // No progress readout while tracking is off.
        composeRule.onNodeWithText("0 / 30").assertDoesNotExist()
        composeRule.onNodeWithText("30 to go").assertDoesNotExist()
    }

    @Test
    fun goalTrackingOn_showsProgress_notSetupPrompt() {
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(
                    loading = false, hasAnyCards = true,
                    dailyGoalEnabled = true, goalTotal = 30, studiedToday = 0,
                ),
                onStudyAll = {},
            )
        }
        composeRule.onNodeWithText("0 / 30").assertIsDisplayed()
        composeRule.onNodeWithText("Set up your daily goal").assertDoesNotExist()
    }

    @Test
    fun premiumNudge_visible_andOpensPaywall() {
        var opened = false
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false, hasAnyCards = true, showPremiumNudge = true),
                onStudyAll = {},
                onOpenPaywall = { opened = true },
            )
        }
        composeRule.onNodeWithText("Back up your cards").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun premiumNudge_dismissButton_firesCallback() {
        var dismissed = false
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false, hasAnyCards = true, showPremiumNudge = true),
                onStudyAll = {},
                onDismissNudge = { dismissed = true },
            )
        }
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun newUser_noCards_showsOnboarding_andGoToLibrary() {
        var wentToLibrary = false
        composeRule.setContent {
            // Default state: no cards at all (hasAnyCards = false) → onboarding nudge.
            StudyQueueContent(
                state = StudyQueueUiState(loading = false),
                onStudyAll = {},
                onGoToLibrary = { wentToLibrary = true },
            )
        }
        composeRule.onNodeWithText("Let's create your first flashcards").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Library").performClick()
        assertTrue(wentToLibrary)
    }
}
