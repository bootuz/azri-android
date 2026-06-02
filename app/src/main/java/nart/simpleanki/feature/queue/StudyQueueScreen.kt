package nart.simpleanki.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.ui.components.AzriCard
import nart.simpleanki.ui.components.ColorAccentIcon
import nart.simpleanki.ui.theme.AzriTheme
import nart.simpleanki.ui.theme.toColor
import org.koin.androidx.compose.koinViewModel

private enum class StudyByMode { Decks, Folders }

@Composable
fun StudyQueueScreen(
    onStudyAll: () -> Unit,
    onStudyDeck: (String) -> Unit,
    onStudyFolder: (String) -> Unit,
    viewModel: StudyQueueViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showGoalSheet by remember { mutableStateOf(false) }
    StudyQueueContent(
        state = state,
        onStudyAll = onStudyAll,
        onStudyDeck = onStudyDeck,
        onStudyFolder = onStudyFolder,
        onEditGoal = { showGoalSheet = true },
    )
    if (showGoalSheet) {
        DailyGoalEditorSheet(onDismiss = { showGoalSheet = false })
    }
}

/** Stateless study-queue ("Today") UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyQueueContent(
    state: StudyQueueUiState,
    onStudyAll: () -> Unit,
    onStudyDeck: (String) -> Unit = {},
    onStudyFolder: (String) -> Unit = {},
    onEditGoal: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Mode is pure view state; fall back to Decks if folders disappear.
        var mode by rememberSaveable { mutableStateOf(StudyByMode.Decks) }
        val effectiveMode = if (mode == StudyByMode.Folders && state.hasFolders) StudyByMode.Folders else StudyByMode.Decks

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.dailyGoalEnabled && state.goalTotal > 0) {
                item { DailyGoalCard(state, onClick = onEditGoal) }
            }
            item { HeroCard(state, onStudyAll) }

            if (state.decks.isNotEmpty()) {
                item {
                    StudyByStrip(
                        state = state,
                        mode = effectiveMode,
                        onModeChange = { mode = it },
                        onStudyDeck = onStudyDeck,
                        onStudyFolder = onStudyFolder,
                    )
                }
            }

            if (state.queueCards.isNotEmpty()) {
                item { SectionHeader("Queue") }
                itemsIndexed(state.queueCards, key = { _, c -> c.cardId }) { index, card ->
                    QueueCardRow(index + 1, card)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun DailyGoalCard(state: StudyQueueUiState, onClick: () -> Unit) {
    AzriCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily goal", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${state.studiedToday} / ${state.goalTotal}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (state.goalTotal == 0) 0f else (state.studiedToday.toFloat() / state.goalTotal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.goalMet) "Goal reached 🎉" else "${state.goalRemaining} to go",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroCard(state: StudyQueueUiState, onStudyAll: () -> Unit) {
    AzriCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.hasWork) {
                Text(
                    state.readyCount.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("cards ready", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "~${state.estimatedMinutes} min · ${state.newCount} new · ${state.dueCount} due",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.goalMet) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You've hit today's goal 🎉 — keep going if you like",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStudyAll,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null)
                    Text(
                        if (state.goalMet) "Keep studying" else "Study",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("All caught up", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You're all set for today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "Study by" header (+ Decks/Folders toggle when folders exist) and a horizontal chip strip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyByStrip(
    state: StudyQueueUiState,
    mode: StudyByMode,
    onModeChange: (StudyByMode) -> Unit,
    onStudyDeck: (String) -> Unit,
    onStudyFolder: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.hasFolders) "Study by" else "Study by deck",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.hasFolders) {
                SingleChoiceSegmentedButtonRow {
                    StudyByMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = m == mode,
                            onClick = { onModeChange(m) },
                            shape = SegmentedButtonDefaults.itemShape(index, StudyByMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text(if (m == StudyByMode.Decks) "Decks" else "Folders") }
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (mode == StudyByMode.Decks) {
                items(state.decks, key = { it.deckId }) { deck ->
                    DeckChip(deck, onClick = { onStudyDeck(deck.deckId) })
                }
            } else {
                items(state.folders, key = { it.folderId }) { folder ->
                    FolderChip(folder, onClick = { onStudyFolder(folder.folderId) })
                }
            }
        }
    }
}

@Composable
private fun DeckChip(deck: DeckQueueItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            ColorAccentIcon(tint = deck.color.toColor()) {
                Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                deck.deckName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chipCounts(deck.dueCount, deck.newCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderChip(folder: FolderQueueItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            ColorAccentIcon(tint = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${folder.deckCount} ${if (folder.deckCount == 1) "deck" else "decks"} · ${chipCounts(folder.dueCount, folder.newCount)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chipCounts(due: Int, new: Int): String =
    buildList {
        if (due > 0) add("$due due")
        if (new > 0) add("$new new")
    }.joinToString(" · ").ifEmpty { "0 due" }

/** Read-only preview row in the "Queue" list: number badge + front + deck/folder name. */
@Composable
private fun QueueCardRow(index: Int, card: QueueCardItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                card.front,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(card.deckName, card.folderName).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(name = "Queue · has work", showBackground = true)
@Composable
private fun StudyQueuePreview() {
    AzriTheme {
        StudyQueueContent(
            state = StudyQueueUiState(
                loading = false, readyCount = 23, newCount = 8, dueCount = 15, estimatedMinutes = 4,
                decks = listOf(
                    DeckQueueItem("d1", "Spanish 101", ColorOption.Indigo, dueCount = 12, newCount = 3),
                    DeckQueueItem("d2", "Biology", ColorOption.Green, dueCount = 3, newCount = 5),
                ),
                folders = listOf(FolderQueueItem("f1", "Languages", deckCount = 2, dueCount = 12, newCount = 3)),
                queueCards = listOf(
                    QueueCardItem("c1", "hola", "Spanish 101", "Languages"),
                    QueueCardItem("c2", "mitochondria", "Biology", null),
                ),
                goalTotal = 30, studiedToday = 7,
            ),
            onStudyAll = {},
        )
    }
}

@Preview(name = "Queue · all caught up", showBackground = true)
@Composable
private fun StudyQueueEmptyPreview() {
    AzriTheme {
        StudyQueueContent(state = StudyQueueUiState(loading = false), onStudyAll = {})
    }
}
