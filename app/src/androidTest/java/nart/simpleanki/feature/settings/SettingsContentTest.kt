package nart.simpleanki.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPresets_andSelectingInvokesCallback() {
        var picked: FsrsPreset? = null
        composeRule.setContent {
            SettingsContent(
                state = SettingsUiState(settings = AppSettings(preset = FsrsPreset.Optimal), isAnonymous = true, uid = "u1"),
                onSetPreset = { picked = it },
                onSetNewCardsPerDay = {},
                onSetMaxReviewsPerDay = {},
                onSignOut = {},
                onDeleteAccount = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Optimal").assertIsDisplayed()
        composeRule.onNodeWithText("Aggressive").performClick()
        assertEquals(FsrsPreset.Aggressive, picked)
    }

    @Test
    fun deleteAccount_showsConfirmation_thenConfirms() {
        var deleted = false
        composeRule.setContent {
            SettingsContent(
                state = SettingsUiState(settings = AppSettings(), email = "a@b.com", isAnonymous = false, uid = "u1"),
                onSetPreset = {},
                onSetNewCardsPerDay = {},
                onSetMaxReviewsPerDay = {},
                onSignOut = {},
                onDeleteAccount = { deleted = true },
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Delete account").performClick()
        composeRule.onNodeWithText("Delete account?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
    }
}
