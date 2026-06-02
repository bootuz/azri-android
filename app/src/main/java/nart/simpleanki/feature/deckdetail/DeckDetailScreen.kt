package nart.simpleanki.feature.deckdetail

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nart.simpleanki.core.domain.fsrs.IntervalFormatter
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DeckDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onStudy = onStudy,
        onAddCard = onAddCard,
        onEditCard = onEditCard,
        onSettings = onSettings,
        onDeleteCard = { card ->
            viewModel.deleteCard(card)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Card deleted",
                    actionLabel = "Undo",
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreCard(card)
            }
        },
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onDeleteCard: (Card) -> Unit = {},
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search cards") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        SwipeToDeleteCard(
                            onClick = { onEditCard(card.id) },
                            onDelete = { onDeleteCard(card) },
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
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
                                Column(
                                    modifier = Modifier.padding(start = 12.dp),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    StateBadge(CardState.fromValue(card.fsrsState) ?: CardState.New)
                                    dueLabel(card, now)?.let { due ->
                                        Text(
                                            due,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A card row that reveals a red "delete" affordance when swiped right-to-left and removes
 * itself once swiped past threshold. Tapping still opens the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCard(
    onClick: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete card",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        AzriCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Compact colored pill showing a card's FSRS state (New/Learning/Review/Relearning). */
@Composable
private fun StateBadge(state: CardState) {
    val (label, color) = when (state) {
        CardState.New -> "New" to MaterialTheme.colorScheme.primary
        CardState.Learning -> "Learning" to Color(0xFFC25E1D)
        CardState.Review -> "Review" to Color(0xFF1F8A47)
        CardState.Relearning -> "Relearning" to Color(0xFFA02A2A)
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Relative due text for a card: null for New cards, else "Due now" / "Due in 3d". */
private fun dueLabel(card: Card, now: Long): String? {
    val state = CardState.fromValue(card.fsrsState) ?: CardState.New
    if (state == CardState.New) return null
    return if (card.fsrsDue <= now) "Due now" else "Due in ${IntervalFormatter.format(card.fsrsDue - now)}"
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

private fun previewCard(
    id: String, front: String, back: String,
    state: CardState = CardState.New, fsrsDue: Long = 0,
) = Card(
    id = id, front = front, back = back, deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = fsrsDue, fsrsState = state.value,
)

@Preview(name = "Deck detail", showBackground = true)
@Composable
private fun DeckDetailPreview() {
    val now = 1_000_000_000_000L
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(
                deckId = "d1", deckName = "Spanish 101",
                cards = listOf(
                    previewCard("1", "hola", "hello", CardState.New),
                    previewCard("2", "gracias", "thank you", CardState.Review, fsrsDue = now + 4 * 86_400_000L),
                    previewCard("3", "por favor", "please", CardState.Learning, fsrsDue = now - 60_000L),
                ),
            ),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
            now = now,
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
