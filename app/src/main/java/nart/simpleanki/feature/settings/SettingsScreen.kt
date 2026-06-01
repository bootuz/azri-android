package nart.simpleanki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nart.simpleanki.R
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                        onClick = { viewModel.setPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }
            Stepper("New cards / day", state.settings.newCardsPerDay, step = 5) {
                viewModel.setNewCardsPerDay(it)
            }
            Stepper("Max reviews / day", state.settings.maxReviewsPerDay, step = 25) {
                viewModel.setMaxReviewsPerDay(it)
            }

            HorizontalDivider()
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.isAnonymous) "Guest (${state.uid})" else (state.email ?: state.uid),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = viewModel::signOut) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) { Text("−") }
        Text(
            value.toString(),
            Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onChange(value + step) }) { Text("+") }
    }
}
