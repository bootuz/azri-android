package nart.simpleanki.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StickyNote2
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.R
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.ui.components.AzriCard
import nart.simpleanki.ui.components.ColorAccentIcon
import nart.simpleanki.ui.theme.AzriTheme
import nart.simpleanki.ui.theme.toColor
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryScreen(
    onOpenDeck: (String) -> Unit,
    onNewDeck: () -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LibraryContent(state, onOpenDeck, onNewDeck, onNewFolder, onSettings)
}

/** Stateless library UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onOpenDeck: (String) -> Unit,
    onNewDeck: () -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onNewFolder) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = onNewDeck) {
                        Icon(Icons.Default.Add, contentDescription = "New deck")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (state.folders.isEmpty() && state.allDecks.isEmpty()) {
            EmptyLibrary(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.folders.isNotEmpty()) {
                item { SectionHeader("Folders") }
                items(state.folders, key = { "folder-${it.id}" }) { folder -> FolderRow(folder) }
            }
            item { SectionHeader("Decks") }
            items(state.decksWithoutFolder, key = { "deck-${it.id}" }) { deck ->
                DeckRow(deck = deck, cardCount = state.cardCounts[deck.id] ?: 0, onClick = { onOpenDeck(deck.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun FolderRow(folder: Folder) {
    AzriCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorAccentIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant) {
                if (folder.emoji != null) Text(folder.emoji) else Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun DeckRow(deck: Deck, cardCount: Int, onClick: () -> Unit) {
    AzriCard(onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorAccentIcon(tint = deck.color.toColor()) {
                Icon(Icons.Outlined.StickyNote2, null, Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (cardCount == 0) "No cards" else "$cardCount ${if (cardCount == 1) "card" else "cards"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "No decks yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Tap + to create your first deck.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val sampleDecks = listOf(
    Deck(id = "d1", name = "Spanish 101", color = ColorOption.Indigo, dateCreated = 0, lastModified = 0),
    Deck(id = "d2", name = "Biology terms", color = ColorOption.Green, dateCreated = 0, lastModified = 0),
    Deck(id = "d3", name = "Kanji", color = ColorOption.Red, dateCreated = 0, lastModified = 0),
)

@Preview(name = "Library", showBackground = true)
@Composable
private fun LibraryPreview() {
    AzriTheme {
        LibraryContent(
            state = LibraryUiState(
                folders = listOf(Folder(id = "f1", name = "Languages", emoji = "🌍", lastModified = 0)),
                decksWithoutFolder = sampleDecks,
                allDecks = sampleDecks,
                cardCounts = mapOf("d1" to 42, "d2" to 1, "d3" to 0),
            ),
            onOpenDeck = {}, onNewDeck = {}, onNewFolder = {}, onSettings = {},
        )
    }
}

@Preview(name = "Library · empty", showBackground = true)
@Composable
private fun LibraryEmptyPreview() {
    AzriTheme {
        LibraryContent(LibraryUiState(), onOpenDeck = {}, onNewDeck = {}, onNewFolder = {}, onSettings = {})
    }
}

@Preview(name = "Library · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryDarkPreview() {
    AzriTheme(darkTheme = true) {
        LibraryContent(
            state = LibraryUiState(decksWithoutFolder = sampleDecks, allDecks = sampleDecks, cardCounts = mapOf("d1" to 42)),
            onOpenDeck = {}, onNewDeck = {}, onNewFolder = {}, onSettings = {},
        )
    }
}
