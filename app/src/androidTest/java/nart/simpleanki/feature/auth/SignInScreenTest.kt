package nart.simpleanki.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsButtons_andInvokesCallbacks() {
        var googleClicked = false
        var guestClicked = false
        composeRule.setContent {
            SignInScreen(
                onGoogleClick = { googleClicked = true },
                onGuestClick = { guestClicked = true },
            )
        }

        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Continue as guest").assertIsDisplayed().performClick()

        assertTrue(googleClicked)
        assertTrue(guestClicked)
    }

    @Test
    fun showsErrorMessage_whenProvided() {
        composeRule.setContent {
            SignInScreen(onGoogleClick = {}, onGuestClick = {}, errorMessage = "Sign-in failed")
        }
        composeRule.onNodeWithText("Sign-in failed").assertIsDisplayed()
    }
}
