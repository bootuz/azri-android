package nart.simpleanki.feature.deckdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeckDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun card(id: String, front: String, back: String) = Card(
        id = id, front = front, back = back, deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    @Test
    fun emptyDeck_showsHint_noStudyFab() {
        composeRule.setContent {
            DeckDetailContent(
                state = DeckDetailUiState(deckId = "d1", deckName = "Spanish"),
                onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
            )
        }
        composeRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeRule.onNodeWithText("No cards yet. Tap + to add one.").assertIsDisplayed()
    }

    @Test
    fun withCards_showsThem_andEditOnClick() {
        var edited: String? = null
        composeRule.setContent {
            DeckDetailContent(
                state = DeckDetailUiState(deckId = "d1", deckName = "Spanish", cards = listOf(card("c1", "hola", "hello"))),
                onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = { edited = it }, onSettings = {},
            )
        }
        composeRule.onNodeWithText("hola").assertIsDisplayed()
        composeRule.onNodeWithText("hello").assertIsDisplayed()
        composeRule.onNodeWithText("hola").performClick()
        assertEquals("c1", edited)
    }
}
