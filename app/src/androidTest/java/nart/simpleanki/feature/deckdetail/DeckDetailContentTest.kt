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

    private fun reviewCard(id: String, due: Long) = Card(
        id = id, front = "f$id", back = "b$id", deckId = "d1",
        dateCreated = 0, lastModified = 0, fsrsDue = due, fsrsState = CardState.Review.value,
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
    fun caughtUp_showsMessage_andNoStudyButton() {
        val now = 1_000_000_000_000L
        composeRule.setContent {
            DeckDetailContent(
                state = DeckDetailUiState(
                    deckId = "d1", deckName = "Spanish",
                    cards = listOf(reviewCard("c1", due = now + 86_400_000L)),
                    dueCount = 0, newCount = 0,
                ),
                onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
                now = now,
            )
        }
        composeRule.onNodeWithText("You're all caught up!").assertIsDisplayed()
        composeRule.onNodeWithText("Study").assertDoesNotExist()
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
