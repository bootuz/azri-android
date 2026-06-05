package nart.simpleanki.feature.folderdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.ui.components.DeckRow
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FolderDetailScreen(
    folderId: String,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onReview: () -> Unit,
    onNewDeck: () -> Unit,
    onEditFolder: () -> Unit,
    viewModel: FolderDetailViewModel = koinViewModel { parametersOf(folderId) },
) {
    val state by viewModel.uiState.collectAsState()
    FolderDetailContent(
        state = state,
        onBack = onBack,
        onOpenDeck = onOpenDeck,
        onReview = onReview,
        onNewDeck = onNewDeck,
        onEditFolder = onEditFolder,
    )
}

/** Stateless folder-detail UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailContent(
    state: FolderDetailUiState,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onReview: () -> Unit = {},
    onNewDeck: () -> Unit,
    onEditFolder: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.folderName.ifBlank { "Folder" }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReview) { Icon(Icons.Filled.Style, "Review folder") }
                    IconButton(onClick = onEditFolder) { Icon(Icons.Default.Edit, "Edit folder") }
                    IconButton(onClick = onNewDeck) { Icon(Icons.Default.Add, "New deck in folder") }
                },
            )
        },
    ) { padding ->
        if (state.decks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "No decks in this folder",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Tap + to add one, or move a deck here from its settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.decks, key = { it.id }) { deck ->
                DeckRow(deck = deck, cardCount = state.cardCounts[deck.id] ?: 0, onClick = { onOpenDeck(deck.id) })
            }
        }
    }
}

private val sampleDecks = listOf(
    Deck(id = "d1", name = "Verbs", color = ColorOption.Indigo, dateCreated = 0, lastModified = 0),
    Deck(id = "d2", name = "Nouns", color = ColorOption.Green, dateCreated = 0, lastModified = 0),
)

@Preview(name = "Folder detail", showBackground = true)
@Composable
private fun FolderDetailPreview() {
    AzriTheme {
        FolderDetailContent(
            state = FolderDetailUiState(
                folderId = "f1", folderName = "Spanish", decks = sampleDecks,
                cardCounts = mapOf("d1" to 12, "d2" to 1),
            ),
            onBack = {}, onOpenDeck = {}, onNewDeck = {}, onEditFolder = {},
        )
    }
}

@Preview(name = "Folder detail · empty", showBackground = true)
@Composable
private fun FolderDetailEmptyPreview() {
    AzriTheme {
        FolderDetailContent(
            state = FolderDetailUiState(folderId = "f1", folderName = "Empty"),
            onBack = {}, onOpenDeck = {}, onNewDeck = {}, onEditFolder = {},
        )
    }
}
