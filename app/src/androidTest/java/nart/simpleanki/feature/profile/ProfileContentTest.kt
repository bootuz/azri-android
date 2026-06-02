package nart.simpleanki.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.data.settings.ThemeMode
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(isAnonymous: Boolean = false) = ProfileUiState(
        email = if (isAnonymous) null else "grace@example.com",
        isAnonymous = isAnonymous,
        preset = FsrsPreset.Optimal,
        themeMode = ThemeMode.System,
    )

    @Test
    fun spacedRepetitionRow_opensFsrsSettings() {
        var opened = false
        composeRule.setContent {
            ProfileContent(
                state = state(),
                onOpenFsrsSettings = { opened = true },
                onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
            )
        }
        composeRule.onNodeWithText("Spaced repetition").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun themeSegmentedControl_selectsMode() {
        var picked: ThemeMode? = null
        composeRule.setContent {
            ProfileContent(
                state = state(),
                onOpenFsrsSettings = {}, onThemeChange = { picked = it }, onSignOut = {}, onDeleteAccount = {},
            )
        }
        // The tri-state segmented control shows all options at once — tap "Dark" directly.
        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.Dark, picked)
    }

    @Test
    fun overflowMenu_deleteAccount_confirms() {
        var deleted = false
        composeRule.setContent {
            ProfileContent(
                state = state(),
                onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = { deleted = true },
            )
        }
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Delete account").performClick()
        composeRule.onNodeWithText("Delete account?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
    }
}
