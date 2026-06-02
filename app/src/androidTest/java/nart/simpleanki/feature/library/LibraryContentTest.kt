package nart.simpleanki.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsHint() {
        composeRule.setContent {
            LibraryContent(LibraryUiState(), onOpenDeck = {}, onNewDeck = {}, onNewFolder = {})
        }
        composeRule.onNodeWithText("No decks yet").assertIsDisplayed()
    }

    @Test
    fun decksTab_showsDecks_andOpensDeck() {
        var opened: String? = null
        val decks = listOf(Deck(id = "d1", name = "Spanish", dateCreated = 0, lastModified = 0))
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(
                    folders = listOf(Folder(id = "f1", name = "Languages", emoji = "🌍", lastModified = 0)),
                    decksWithoutFolder = decks,
                    allDecks = decks,
                ),
                onOpenDeck = { opened = it },
                onOpenFolder = {},
                onNewDeck = {},
                onNewFolder = {},
            )
        }
        // Decks tab is selected by default: deck visible, folder lives on the other tab.
        composeRule.onNodeWithText("Spanish").assertIsDisplayed().performClick()
        assertEquals("d1", opened)
    }

    @Test
    fun foldersTab_showsFolders_andOpensFolder() {
        var openedFolder: String? = null
        val decks = listOf(Deck(id = "d1", name = "Spanish", dateCreated = 0, lastModified = 0))
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(
                    folders = listOf(Folder(id = "f1", name = "Languages", emoji = "🌍", lastModified = 0)),
                    decksWithoutFolder = decks,
                    allDecks = decks,
                ),
                onOpenDeck = {},
                onOpenFolder = { openedFolder = it },
                onNewDeck = {},
                onNewFolder = {},
            )
        }
        // Switch to the Folders tab, then the folder is shown and tappable.
        composeRule.onNodeWithText("Folders").performClick()
        composeRule.onNodeWithText("Languages").assertIsDisplayed().performClick()
        assertEquals("f1", openedFolder)
    }
}
