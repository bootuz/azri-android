package nart.simpleanki.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun content(
        state: NotificationsUiState,
        onStudyToggle: (Boolean) -> Unit = {},
        onGoalToggle: (Boolean) -> Unit = {},
    ) = NotificationsContent(
        state = state,
        onStudyToggle = onStudyToggle,
        onStudyTime = { _, _ -> },
        onGoalToggle = onGoalToggle,
        onGoalTime = { _, _ -> },
        onBack = {},
    )

    @Test
    fun showsBothReminderRows() {
        composeRule.setContent { content(NotificationsUiState()) }
        composeRule.onNodeWithText("Daily study reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Goal reminder").assertIsDisplayed()
    }

    @Test
    fun enabledStudy_showsFormattedTime() {
        composeRule.setContent {
            content(NotificationsUiState(studyEnabled = true, studyHour = 9, studyMinute = 0))
        }
        composeRule.onNodeWithText("9:00 AM").assertIsDisplayed()
    }

    @Test
    fun togglingStudy_invokesCallback() {
        var toggled: Boolean? = null
        composeRule.setContent {
            content(NotificationsUiState(studyEnabled = false), onStudyToggle = { toggled = it })
        }
        // First toggleable node is the study switch (top row).
        composeRule.onAllNodes(isToggleable())[0].performClick()
        assertEquals(true, toggled)
    }
}
