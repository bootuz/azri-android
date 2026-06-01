package nart.simpleanki.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.auth.AuthUser
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsUserEmail_andSignsOut() {
        var signedOut = false
        composeRule.setContent {
            HomeScreen(
                user = AuthUser("uid1", "Ada", "ada@example.com", isAnonymous = false),
                onSignOut = { signedOut = true },
            )
        }

        composeRule.onNodeWithText("ada@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed().performClick()
        assertTrue(signedOut)
    }
}
