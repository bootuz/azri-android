package nart.simpleanki.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    SettingsContent(
        state = state,
        onSetPreset = viewModel::setPreset,
        onSetCustomRetention = viewModel::setCustomRetention,
        onSetCustomMaxInterval = viewModel::setCustomMaxInterval,
        onSetEnableFuzz = viewModel::setEnableFuzz,
        onSetEnableShortTerm = viewModel::setEnableShortTerm,
        onReset = viewModel::resetToDefaults,
        onBack = onBack,
    )
}

private val MAX_INTERVAL_OPTIONS = listOf(30 to "30d", 90 to "90d", 180 to "180d", 365 to "1y")

/** Stateless spaced-repetition (FSRS) settings UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onSetPreset: (FsrsPreset) -> Unit,
    onSetCustomRetention: (Double) -> Unit,
    onSetCustomMaxInterval: (Int) -> Unit,
    onSetEnableFuzz: (Boolean) -> Unit,
    onSetEnableShortTerm: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spaced repetition") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onReset) { Text("Reset") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Presets")
            FsrsPreset.entries.forEach { preset ->
                PresetRow(
                    preset = preset,
                    selected = settings.preset == preset,
                    onClick = { onSetPreset(preset) },
                )
            }

            AnimatedVisibility(visible = settings.preset == FsrsPreset.Custom) {
                CustomParameters(
                    settings = settings,
                    onSetCustomRetention = onSetCustomRetention,
                    onSetCustomMaxInterval = onSetCustomMaxInterval,
                    onSetEnableFuzz = onSetEnableFuzz,
                    onSetEnableShortTerm = onSetEnableShortTerm,
                )
            }
        }
    }
}

private fun FsrsPreset.icon(): ImageVector = when (this) {
    FsrsPreset.Optimal -> Icons.Default.Balance
    FsrsPreset.Aggressive -> Icons.Default.Whatshot
    FsrsPreset.Relaxed -> Icons.Default.Spa
    FsrsPreset.Custom -> Icons.Default.Tune
}

@Composable
private fun PresetRow(preset: FsrsPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            preset.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(preset.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomParameters(
    settings: AppSettings,
    onSetCustomRetention: (Double) -> Unit,
    onSetCustomMaxInterval: (Int) -> Unit,
    onSetEnableFuzz: (Boolean) -> Unit,
    onSetEnableShortTerm: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Custom parameters", padded = false)

        // Target retention
        Text(
            "Target retention: ${(settings.customRetention * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.customRetention.toFloat(),
            onValueChange = { onSetCustomRetention(it.toDouble()) },
            valueRange = 0.80f..0.99f,
            steps = 18,
        )
        Caption("Higher = more reviews, better memory")

        // Maximum interval
        Text("Maximum interval", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MAX_INTERVAL_OPTIONS.forEachIndexed { index, (days, label) ->
                SegmentedButton(
                    selected = settings.customMaxInterval == days,
                    onClick = { onSetCustomMaxInterval(days) },
                    shape = SegmentedButtonDefaults.itemShape(index, MAX_INTERVAL_OPTIONS.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(label) }
            }
        }
        Caption("Longest time before a card comes back for review")

        SwitchRow(
            title = "Enable fuzz",
            caption = "Adds slight random variation so cards don't all pile up on one day",
            checked = settings.enableFuzz,
            onCheckedChange = onSetEnableFuzz,
        )
        SwitchRow(
            title = "Short-term optimization",
            caption = "Uses quick relearning steps for cards you're still learning",
            checked = settings.enableShortTerm,
            onCheckedChange = onSetEnableShortTerm,
        )
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Caption(caption)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(text: String, padded: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = if (padded) {
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        } else {
            Modifier.padding(bottom = 4.dp)
        },
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(name = "FSRS settings · custom", showBackground = true)
@Composable
private fun SettingsCustomPreview() {
    AzriTheme {
        SettingsContent(
            state = SettingsUiState(AppSettings(preset = FsrsPreset.Custom, customRetention = 0.92, customMaxInterval = 90)),
            onSetPreset = {}, onSetCustomRetention = {}, onSetCustomMaxInterval = {},
            onSetEnableFuzz = {}, onSetEnableShortTerm = {}, onReset = {}, onBack = {},
        )
    }
}

@Preview(name = "FSRS settings · presets", showBackground = true)
@Composable
private fun SettingsPresetsPreview() {
    AzriTheme {
        SettingsContent(
            state = SettingsUiState(AppSettings(preset = FsrsPreset.Optimal)),
            onSetPreset = {}, onSetCustomRetention = {}, onSetCustomMaxInterval = {},
            onSetEnableFuzz = {}, onSetEnableShortTerm = {}, onReset = {}, onBack = {},
        )
    }
}
