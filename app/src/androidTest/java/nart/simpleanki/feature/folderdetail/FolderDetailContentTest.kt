package nart.simpleanki.feature.folderdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FolderDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun deck(id: String, name: String) =
        Deck(id = id, name = name, color = ColorOption.Indigo, dateCreated = 0, lastModified = 0)

    @Test
    fun emptyFolder_showsHint() {
        composeRule.setContent {
            FolderDetailContent(
                state = FolderDetailUiState(folderId = "f1", folderName = "Spanish"),
                onBack = {}, onOpenDeck = {}, onNewDeck = {}, onEditFolder = {},
            )
        }
        composeRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeRule.onNodeWithText("No decks in this folder").assertIsDisplayed()
    }

    @Test
    fun withDecks_showsThem_andOpensOnClick() {
        var opened: String? = null
        composeRule.setContent {
            FolderDetailContent(
                state = FolderDetailUiState(
                    folderId = "f1", folderName = "Spanish",
                    decks = listOf(deck("d1", "Verbs")),
                    cardCounts = mapOf("d1" to 3),
                ),
                onBack = {}, onOpenDeck = { opened = it }, onNewDeck = {}, onEditFolder = {},
            )
        }
        composeRule.onNodeWithText("Verbs").assertIsDisplayed()
        composeRule.onNodeWithText("3 cards").assertIsDisplayed()
        composeRule.onNodeWithText("Verbs").performClick()
        assertEquals("d1", opened)
    }
}
