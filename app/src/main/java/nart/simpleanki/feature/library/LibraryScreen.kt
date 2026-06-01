package nart.simpleanki.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nart.simpleanki.R
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDeck: (String) -> Unit,
    onNewDeck: () -> Unit,
    onNewFolder: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNewFolder) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = onNewDeck) {
                        Icon(Icons.Default.Add, contentDescription = "New deck")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.sign_out))
                    }
                },
            )
        },
    ) { padding ->
        if (state.folders.isEmpty() && state.allDecks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No decks yet. Tap + to create one.")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.folders, key = { "folder-${it.id}" }) { folder ->
                FolderRow(folder)
            }
            if (state.folders.isNotEmpty()) item { HorizontalDivider() }
            items(state.decksWithoutFolder, key = { "deck-${it.id}" }) { deck ->
                DeckRow(deck, onClick = { onOpenDeck(deck.id) })
            }
        }
    }
}

@Composable
private fun FolderRow(folder: Folder) {
    ListItem(
        headlineContent = { Text((folder.emoji?.let { "$it " } ?: "") + folder.name) },
    )
}

@Composable
private fun DeckRow(deck: Deck, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(deck.name) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
