package nart.simpleanki.feature.apkgimport

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.apkg.AnkiNoteType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkgImportScreen(uri: Uri, deckName: String, onClose: () -> Unit) {
    val vm: ApkgImportViewModel = koinViewModel { parametersOf(deckName) }
    val state by vm.uiState.collectAsState()
    LaunchedEffect(uri) { vm.parse(uri) }

    state.error?.let { msg ->
        AlertDialog(
            // Realistic errors are unrecoverable file/parse errors; any dismissal ejects to Library.
            onDismissRequest = { vm.clearError(); onClose() },
            confirmButton = { TextButton(onClick = { vm.clearError(); onClose() }) { Text("OK") } },
            title = { Text("Import error") },
            text = { Text(msg) },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Import deck") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (state.step) {
                ImportStep.Parsing, ImportStep.Importing -> CircularProgressIndicator()
                ImportStep.NoteTypeSelection -> NoteTypeStep(state, vm::selectNoteType)
                ImportStep.FieldMapping -> FieldMappingStep(state, vm)
                ImportStep.Preview -> PreviewStep(state, vm, onClose)
            }
        }
    }
}

@Composable
private fun NoteTypeStep(state: ApkgImportUiState, onSelect: (AnkiNoteType) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.noteTypes) { nt ->
            ListItem(
                headlineContent = { Text(nt.name) },
                supportingContent = { Text("${state.noteCounts[nt.id] ?: 0} notes · ${nt.fields.size} fields") },
                modifier = Modifier.clickable { onSelect(nt) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingStep(state: ApkgImportUiState, vm: ApkgImportViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FieldDropdown("Front", state.availableFields, state.frontField, vm::setFrontField)
        FieldDropdown("Back", state.availableFields, state.backField, vm::setBackField)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.importMedia, onCheckedChange = vm::setImportMedia)
            Spacer(Modifier.width(8.dp))
            Text("Import media")
        }
        Button(onClick = vm::generatePreview, enabled = state.canGeneratePreview && !state.busy) {
            Text("Generate preview")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDropdown(label: String, options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun PreviewStep(state: ApkgImportUiState, vm: ApkgImportViewModel, onClose: () -> Unit) {
    val selectedCount = state.previewCards.count { it.selected }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("$selectedCount of ${state.previewCards.size} selected", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.previewCards) { i, card ->
                ListItem(
                    leadingContent = { Checkbox(checked = card.selected, onCheckedChange = { vm.toggleCard(i) }) },
                    headlineContent = { Text(card.front, maxLines = 1) },
                    supportingContent = { Text(card.back, maxLines = 1) },
                )
            }
        }
        Button(
            onClick = { vm.import(onComplete = onClose) },
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import $selectedCount cards")
        }
    }
}
