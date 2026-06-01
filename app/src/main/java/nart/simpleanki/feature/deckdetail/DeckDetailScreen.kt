package nart.simpleanki.feature.deckdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.ui.components.AzriCard
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: DeckDetailViewModel = koinViewModel { parametersOf(deckId) },
) {
    val state by viewModel.uiState.collectAsState()
    DeckDetailContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onStudy = onStudy,
        onAddCard = onAddCard,
        onEditCard = onEditCard,
        onSettings = onSettings,
    )
}

/** Stateless deck-detail UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailContent(
    state: DeckDetailUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deckName.ifBlank { "Deck" }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Deck settings") }
                    IconButton(onClick = onAddCard) { Icon(Icons.Default.Add, "Add card") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Stats + Study CTA header
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AzriCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat(state.total.toString(), "Total")
                        Stat(state.dueCount.toString(), "Due", highlight = state.dueCount > 0)
                        Stat(state.newCount.toString(), "New")
                    }
                }
                Button(
                    onClick = onStudy,
                    enabled = state.total > 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null)
                    Text(
                        if (state.dueCount > 0) "Study ${state.dueCount} due" else "Study",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search cards") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No cards yet. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visibleCards, key = { it.id }) { card ->
                        AzriCard(onClick = { onEditCard(card.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    card.front,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    card.back,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun previewCard(id: String, front: String, back: String) = Card(
    id = id, front = front, back = back, deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "Deck detail", showBackground = true)
@Composable
private fun DeckDetailPreview() {
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(
                deckId = "d1", deckName = "Spanish 101",
                cards = listOf(
                    previewCard("1", "hola", "hello"),
                    previewCard("2", "gracias", "thank you"),
                    previewCard("3", "por favor", "please"),
                ),
            ),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
        )
    }
}

@Preview(name = "Deck detail · empty", showBackground = true)
@Composable
private fun DeckDetailEmptyPreview() {
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(deckId = "d1", deckName = "New deck"),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
        )
    }
}
