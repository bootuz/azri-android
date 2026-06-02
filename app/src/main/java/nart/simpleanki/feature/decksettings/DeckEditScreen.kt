package nart.simpleanki.feature.decksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.di.DeckEditArgs
import nart.simpleanki.ui.theme.toColor
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditScreen(
    deckId: String?,
    folderId: String?,
    onDone: () -> Unit,
    onDeleted: () -> Unit = onDone,
    viewModel: DeckEditViewModel = koinViewModel { parametersOf(DeckEditArgs(deckId, folderId)) },
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Deck settings" else "New deck") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.canSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPicker(selected = state.color, onSelect = viewModel::onColorChange)
            if (state.folders.isNotEmpty()) {
                FolderPicker(
                    folders = state.folders,
                    selectedId = state.folderId,
                    onSelect = viewModel::onFolderChange,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.shuffled, onCheckedChange = viewModel::onShuffledChange)
                Text("Shuffle cards", Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.autoplay, onCheckedChange = viewModel::onAutoplayChange)
                Text("Autoplay audio", Modifier.padding(start = 8.dp))
            }
            if (state.isEdit) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete deck", Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete deck?") },
            text = { Text("This permanently deletes \"${state.name}\" and all of its cards.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Deck color selector: a dropdown showing each color as a swatch beside its name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPicker(selected: ColorOption, onSelect: (ColorOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Color") },
            leadingIcon = { ColorSwatch(selected) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ColorOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    leadingIcon = { ColorSwatch(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

/** A small filled circle of a deck [ColorOption], with a hairline so light swatches read. */
@Composable
private fun ColorSwatch(color: ColorOption) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color.toColor())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

private fun ColorOption.displayName(): String = wire.replaceFirstChar { it.uppercase() }

/** Single-choice folder selector backed by a scrollable dropdown (scales to many folders). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPicker(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = folders.firstOrNull { it.id == selectedId }?.name ?: "None"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Folder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = { onSelect(null); expanded = false },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = { onSelect(folder.id); expanded = false },
                )
            }
        }
    }
}
