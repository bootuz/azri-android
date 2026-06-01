package nart.simpleanki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.R
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
        onSetNewCardsPerDay = viewModel::setNewCardsPerDay,
        onSetMaxReviewsPerDay = viewModel::setMaxReviewsPerDay,
        onSignOut = viewModel::signOut,
        onDeleteAccount = viewModel::deleteAccount,
        onBack = onBack,
    )
}

/** Stateless settings UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onSetPreset: (FsrsPreset) -> Unit,
    onSetNewCardsPerDay: (Int) -> Unit,
    onSetMaxReviewsPerDay: (Int) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Spaced repetition", style = MaterialTheme.typography.titleMedium)
            Text("Retention preset", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FsrsPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.settings.preset == preset,
                        onClick = { onSetPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }
            Stepper("New cards / day", state.settings.newCardsPerDay, step = 5, onChange = onSetNewCardsPerDay)
            Stepper("Max reviews / day", state.settings.maxReviewsPerDay, step = 25, onChange = onSetMaxReviewsPerDay)

            HorizontalDivider()
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.isAnonymous) "Guest (${state.uid})" else (state.email ?: state.uid),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and synced data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) { Text("−") }
        Text(value.toString(), Modifier.padding(horizontal = 16.dp), textAlign = TextAlign.Center)
        Button(onClick = { onChange(value + step) }) { Text("+") }
    }
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsPreview() {
    AzriTheme {
        SettingsContent(
            state = SettingsUiState(
                settings = AppSettings(preset = FsrsPreset.Optimal, newCardsPerDay = 20, maxReviewsPerDay = 200),
                email = "grace@example.com", uid = "abc123", isAnonymous = false,
            ),
            onSetPreset = {}, onSetNewCardsPerDay = {}, onSetMaxReviewsPerDay = {},
            onSignOut = {}, onDeleteAccount = {}, onBack = {},
        )
    }
}
