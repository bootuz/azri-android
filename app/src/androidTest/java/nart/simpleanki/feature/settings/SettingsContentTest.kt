package nart.simpleanki.feature.settings

import androidx.compose.runtime.Composable
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

    @Composable
    private fun content(
        preset: FsrsPreset,
        onSetPreset: (FsrsPreset) -> Unit = {},
        onSetCustomMaxInterval: (Int) -> Unit = {},
        onSetEnableFuzz: (Boolean) -> Unit = {},
        onReset: () -> Unit = {},
    ) {
        SettingsContent(
            state = SettingsUiState(settings = AppSettings(preset = preset)),
            onSetPreset = onSetPreset,
            onSetCustomRetention = {},
            onSetCustomMaxInterval = onSetCustomMaxInterval,
            onSetEnableFuzz = onSetEnableFuzz,
            onSetEnableShortTerm = {},
            onReset = onReset,
            onBack = {},
        )
    }

    @Test
    fun showsPresets_andSelectingInvokesCallback() {
        var picked: FsrsPreset? = null
        composeRule.setContent { content(FsrsPreset.Optimal, onSetPreset = { picked = it }) }
        composeRule.onNodeWithText("Spaced repetition").assertIsDisplayed()
        composeRule.onNodeWithText("Default").assertIsDisplayed() // Optimal.displayName
        composeRule.onNodeWithText("Custom").assertIsDisplayed()
        composeRule.onNodeWithText("Aggressive").performClick()
        assertEquals(FsrsPreset.Aggressive, picked)
    }

    @Test
    fun customParameters_hiddenForBuiltInPreset() {
        composeRule.setContent { content(FsrsPreset.Optimal) }
        composeRule.onNodeWithText("Maximum interval").assertDoesNotExist()
    }

    @Test
    fun customParameters_shownForCustom_andMaxIntervalInvokesCallback() {
        var maxInterval: Int? = null
        composeRule.setContent { content(FsrsPreset.Custom, onSetCustomMaxInterval = { maxInterval = it }) }
        composeRule.onNodeWithText("Maximum interval").assertIsDisplayed()
        composeRule.onNodeWithText("90d").performClick()
        assertEquals(90, maxInterval)
    }

    @Test
    fun resetButton_invokesCallback() {
        var reset = false
        composeRule.setContent { content(FsrsPreset.Custom, onReset = { reset = true }) }
        composeRule.onNodeWithText("Reset").performClick()
        assertTrue(reset)
    }
}
