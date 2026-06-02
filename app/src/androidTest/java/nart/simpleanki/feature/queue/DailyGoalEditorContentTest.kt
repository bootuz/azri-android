package nart.simpleanki.feature.queue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DailyGoalEditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTargets_andTotal_whenEnabled() {
        composeRule.setContent {
            DailyGoalEditorContent(
                state = DailyGoalUiState(enabled = true, newCardsTarget = 10, reviewCardsTarget = 20),
                onSetEnabled = {}, onSetNewCardsTarget = {}, onSetReviewCardsTarget = {}, onReset = {},
            )
        }
        composeRule.onNodeWithText("New cards").assertIsDisplayed()
        composeRule.onNodeWithText("Reviews").assertIsDisplayed()
        composeRule.onNodeWithText("30").assertIsDisplayed() // derived total
    }

    @Test
    fun stepperButtons_invokeCallbacks() {
        var newVal: Int? = null
        composeRule.setContent {
            DailyGoalEditorContent(
                state = DailyGoalUiState(enabled = true, newCardsTarget = 10, reviewCardsTarget = 20),
                onSetEnabled = {}, onSetNewCardsTarget = { newVal = it }, onSetReviewCardsTarget = {}, onReset = {},
            )
        }
        composeRule.onNodeWithContentDescription("Increase New cards").performClick()
        assertEquals(11, newVal)
    }

    @Test
    fun togglingOff_hidesTargets() {
        composeRule.setContent {
            DailyGoalEditorContent(
                state = DailyGoalUiState(enabled = false, newCardsTarget = 10, reviewCardsTarget = 20),
                onSetEnabled = {}, onSetNewCardsTarget = {}, onSetReviewCardsTarget = {}, onReset = {},
            )
        }
        composeRule.onNodeWithText("New cards").assertDoesNotExist()
        composeRule.onNodeWithText("Goal tracking").assertIsDisplayed()
    }
}
