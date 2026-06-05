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
    fun newCard_showsReverseToggle_andEditsInvokeCallbacks() {
        var front = ""
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false),
                onFrontChange = { front = it },
                onBackChange = {},
                onSelectDeck = {},
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
        composeRule.onNodeWithContentDescription("Also create reverse card").assertExists() // toggle, new cards only
        composeRule.onNodeWithContentDescription("Add image").assertExists()
        composeRule.onNodeWithText("Front").performTextInput("hola")
        assertEquals("hola", front)
    }

    @Test
    fun editCard_hidesReverseChip() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(front = "a", back = "b", isEdit = true),
                onFrontChange = {},
                onBackChange = {},
                onSelectDeck = {},
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

    @Test
    fun newCard_reverseOn_showsHint() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false, createReverse = true),
                onFrontChange = {},
                onBackChange = {},
                onSelectDeck = {},
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
        composeRule.onNodeWithText("A reverse card (Back → Front) will also be created.")
            .assertExists()
    }

    @Test
    fun newCard_reverseOff_hidesHint() {
        composeRule.setContent {
            CardFormContent(
                state = CardFormUiState(isEdit = false, createReverse = false),
                onFrontChange = {},
                onBackChange = {},
                onSelectDeck = {},
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
        composeRule.onNodeWithText("A reverse card (Back → Front) will also be created.")
            .assertDoesNotExist()
    }
}
