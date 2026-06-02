package nart.simpleanki.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import org.junit.Assert.assertEquals
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
                state = SettingsUiState(settings = AppSettings(preset = FsrsPreset.Optimal)),
                onSetPreset = { picked = it },
                onSetNewCardsPerDay = {},
                onSetMaxReviewsPerDay = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Spaced repetition").assertIsDisplayed()
        composeRule.onNodeWithText("Optimal").assertIsDisplayed()
        composeRule.onNodeWithText("Aggressive").performClick()
        assertEquals(FsrsPreset.Aggressive, picked)
    }
}
