package nart.simpleanki.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import nart.simpleanki.ui.components.DeckRow
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryScreen(
    onOpenDeck: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewDeck: () -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LibraryContent(state, onOpenDeck, onOpenFolder, onNewDeck, onNewFolder, onSettings)
}

/** Stateless library UI, decoupled from the ViewModel for testing. Decks and folders are split
 *  into two tabs (Decks first, Folders second). [initialTab] seeds the selected tab for previews/tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onOpenDeck: (String) -> Unit,
    onOpenFolder: (String) -> Unit = {},
    onNewDeck: () -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
    initialTab: Int = 0,
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            var selectedTab by remember { mutableStateOf(initialTab) }
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Decks") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Folders") },
                )
            }
            when (selectedTab) {
                0 -> DeckList(state.allDecks, state.cardCounts, onOpenDeck)
                else -> FolderList(state.folders, onOpenFolder)
            }
        }
    }
}

@Composable
private fun DeckList(decks: List<Deck>, cardCounts: Map<String, Int>, onOpenDeck: (String) -> Unit) {
    if (decks.isEmpty()) {
        EmptyState(Icons.Outlined.StickyNote2, "No decks yet", "Tap + to create your first deck.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(decks, key = { it.id }) { deck ->
            DeckRow(deck = deck, cardCount = cardCounts[deck.id] ?: 0, onClick = { onOpenDeck(deck.id) })
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onOpenFolder: (String) -> Unit) {
    if (folders.isEmpty()) {
        EmptyState(Icons.Outlined.Folder, "No folders yet", "Tap the folder icon above to create one.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderRow(folder, onClick = { onOpenFolder(folder.id) })
        }
    }
}

@Composable
private fun FolderRow(folder: Folder, onClick: () -> Unit) {
    AzriCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorAccentIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant) {
                if (folder.emoji != null) Text(folder.emoji) else Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                subtitle,
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

@Preview(name = "Library · decks tab", showBackground = true)
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

@Preview(name = "Library · folders tab", showBackground = true)
@Composable
private fun LibraryFoldersTabPreview() {
    AzriTheme {
        LibraryContent(
            state = LibraryUiState(
                folders = listOf(
                    Folder(id = "f1", name = "Languages", emoji = "🌍", lastModified = 0),
                    Folder(id = "f2", name = "Science", lastModified = 0),
                ),
                allDecks = sampleDecks,
            ),
            onOpenDeck = {}, onNewDeck = {}, onNewFolder = {}, onSettings = {},
            initialTab = 1,
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
