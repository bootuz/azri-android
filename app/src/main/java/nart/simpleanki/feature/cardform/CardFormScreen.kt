package nart.simpleanki.feature.cardform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nart.simpleanki.di.CardFormArgs
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CardFormScreen(
    deckId: String,
    cardId: String?,
    onDone: () -> Unit,
    viewModel: CardFormViewModel = koinViewModel { parametersOf(CardFormArgs(deckId, cardId)) },
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    CardFormContent(
        state = state,
        onFrontChange = viewModel::onFrontChange,
        onBackChange = viewModel::onBackChange,
        onToggleReverse = viewModel::onToggleReverse,
        onSave = viewModel::save,
        onBack = onDone,
    )
}

/** Stateless card-form UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormContent(
    state: CardFormUiState,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onToggleReverse: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit card" else "New card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = state.canSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Front") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.back,
                onValueChange = onBackChange,
                label = { Text("Back") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.createReverse, onCheckedChange = onToggleReverse)
                    Text("Also create reverse card", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
