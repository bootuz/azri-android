package nart.simpleanki.feature.cardform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardFormContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newCard_showsReverseAction_andEditsInvokeCallbacks() {
        var front = ""
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false),
                onFrontChange = { front = it },
                onBackChange = {},
                isRecording = false,
                onToggleReverse = {},
                onAddImage = {},
                onRemoveImage = {},
                onToggleRecording = {},
                onRemoveAudio = {},
                onSave = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("New card").assertIsDisplayed()
        // Reverse is now a toolbar action (icon) for new cards.
        composeRule.onNodeWithContentDescription("Also create reverse card").assertExists()
        // Type into the Front field via its placeholder.
        composeRule.onNodeWithText("Enter the question").performTextInput("hola")
        assertEquals("hola", front)
    }

    @Test
    fun editCard_hidesReverseAction() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(front = "a", back = "b", isEdit = true),
                onFrontChange = {},
                onBackChange = {},
                isRecording = false,
                onToggleReverse = {},
                onAddImage = {},
                onRemoveImage = {},
                onToggleRecording = {},
                onRemoveAudio = {},
                onSave = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Edit card").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Also create reverse card").assertDoesNotExist()
    }
}
