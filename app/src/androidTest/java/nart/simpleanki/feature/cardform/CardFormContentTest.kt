package nart.simpleanki.feature.cardform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardFormContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newCard_showsReverseToggle_andEditsInvokeCallbacks() {
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
        composeRule.onNodeWithText("Also create reverse card").assertIsDisplayed()
        composeRule.onNodeWithText("Front").performTextInput("hola")
        assertEquals("hola", front)
    }

    @Test
    fun editCard_hidesReverseToggle() {
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
        composeRule.onNodeWithText("Also create reverse card").assertIsNotDisplayed()
    }
}
