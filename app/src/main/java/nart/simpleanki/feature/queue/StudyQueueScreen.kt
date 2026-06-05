package nart.simpleanki.feature.queue

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.fsrs.QueueSortOrder
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
    onGoToLibrary: () -> Unit,
    onAddCards: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
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
        onSortChange = viewModel::setSortOrder,
        onGoToLibrary = onGoToLibrary,
        onAddCards = onAddCards,
        onOpenPaywall = onOpenPaywall,
        onDismissNudge = viewModel::dismissPremiumNudge,
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
    onSortChange: (QueueSortOrder) -> Unit = {},
    onGoToLibrary: () -> Unit = {},
    onAddCards: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.currentStreak > 0) {
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("🔥", fontSize = 18.sp)
                            Text(
                                state.currentStreak.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Mode is pure view state; fall back to Decks if folders disappear.
        var mode by rememberSaveable { mutableStateOf(StudyByMode.Decks) }
        val effectiveMode =
            if (mode == StudyByMode.Folders && state.hasFolders) StudyByMode.Folders else StudyByMode.Decks

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.showPremiumNudge) {
                item { PremiumNudgeCard(onClick = onOpenPaywall, onDismiss = onDismissNudge) }
            }
            // Progress when goal tracking is on; a "set up" nudge for brand-new users when it's off.
            // An existing user who deliberately turned it off sees nothing.
            if ((state.dailyGoalEnabled && state.goalTotal > 0) || !state.hasAnyCards) {
                item { DailyGoalCard(state, onClick = onEditGoal) }
            }
            item { HeroCard(state, onStudyAll, onGoToLibrary, onAddCards) }

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
                item { QueueHeader(sortOrder = state.sortOrder, onSortChange = onSortChange) }
                itemsIndexed(state.queueCards, key = { _, c -> c.cardId }) { index, card ->
                    QueueCardRow(index + 1, card)
                }
            }
        }
    }
}

@Composable
private fun PremiumNudgeCard(onClick: () -> Unit, onDismiss: () -> Unit) {
    AzriCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☁️", modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("Back up your cards", style = MaterialTheme.typography.titleSmall)
                Text("Sync across devices with Premium", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueHeader(sortOrder: QueueSortOrder, onSortChange: (QueueSortOrder) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Queue",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort cards")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                QueueSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label()) },
                        leadingIcon = { Icon(order.icon(), contentDescription = null) },
                        trailingIcon = {
                            if (order == sortOrder) Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected"
                            )
                        },
                        onClick = { onSortChange(order); menuOpen = false },
                    )
                }
            }
        }
    }
}

private fun QueueSortOrder.label(): String = when (this) {
    QueueSortOrder.DueDate -> "Due date"
    QueueSortOrder.Difficulty -> "Difficulty"
    QueueSortOrder.Shuffle -> "Shuffle"
}

private fun QueueSortOrder.icon(): ImageVector = when (this) {
    QueueSortOrder.DueDate -> Icons.Default.Schedule
    QueueSortOrder.Difficulty -> Icons.Default.BarChart
    QueueSortOrder.Shuffle -> Icons.Default.Shuffle
}

@Composable
private fun DailyGoalCard(state: StudyQueueUiState, onClick: () -> Unit) {
    AzriCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (!state.dailyGoalEnabled) {
                // Goal tracking off (the default) — invite the user to set one up instead of progress.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Set up your daily goal", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Choose how many cards to study each day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Daily goal",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${state.studiedToday} / ${state.goalTotal}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.goalTotal == 0) 0f else (state.studiedToday.toFloat() / state.goalTotal).coerceIn(
                        0f,
                        1f
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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
private fun HeroCard(
    state: StudyQueueUiState,
    onStudyAll: () -> Unit,
    onGoToLibrary: () -> Unit,
    onAddCards: () -> Unit,
) {
    AzriCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.hasWork && !state.hasAnyCards) {
                // Brand-new user: onboarding nudge toward creating their first cards.
                Icon(
                    Icons.Outlined.CollectionsBookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Let's create your first flashcards",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Add a deck and some cards, then come back here to start studying.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onGoToLibrary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Style, contentDescription = null)
                    Text(
                        "Go to Library",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else if (state.hasWork) {
                Text(
                    state.readyCount.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (state.readyCount == 1) "card ready" else "cards ready",
                    style = MaterialTheme.typography.titleMedium,
                )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
                Spacer(Modifier.height(20.dp))
                // This branch only renders when the user has cards (hasAnyCards), which guarantees
                // at least one deck exists — so the card editor's deck picker is never empty here.
                OutlinedButton(
                    onClick = onAddCards,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        "Add more cards",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
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
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
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
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                StudyByMode.entries.size
                            ),
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
    // Filled with the deck's assigned color (white content), mirroring the iOS deck chip.
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(
            containerColor = deck.color.toColor(),
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                deck.deckName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chipCounts(deck.dueCount, deck.newCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun FolderChip(folder: FolderQueueItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                "${folder.deckCount} ${if (folder.deckCount == 1) "deck" else "decks"} · ${
                    chipCounts(
                        folder.dueCount,
                        folder.newCount
                    )
                }",
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
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        Column(
            Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
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
                    DeckQueueItem(
                        "d1",
                        "Spanish 101",
                        ColorOption.Indigo,
                        dueCount = 12,
                        newCount = 3
                    ),
                    DeckQueueItem("d2", "Biology", ColorOption.Green, dueCount = 3, newCount = 5),
                ),
                folders = listOf(
                    FolderQueueItem(
                        "f1",
                        "Languages",
                        deckCount = 2,
                        dueCount = 12,
                        newCount = 3
                    )
                ),
                queueCards = listOf(
                    QueueCardItem("c1", "hola", "Spanish 101", "Languages"),
                    QueueCardItem("c2", "mitochondria", "Biology", null),
                ),
                goalTotal = 30, studiedToday = 7,
                currentStreak = 7,
            ),
            onStudyAll = {},
        )
    }
}

@Preview(name = "Queue · all caught up", showBackground = true)
@Composable
private fun StudyQueueEmptyPreview() {
    AzriTheme {
        StudyQueueContent(
            state = StudyQueueUiState(loading = false, hasAnyCards = true),
            onStudyAll = {})
    }
}

@Preview(name = "Queue · new user onboarding", showBackground = true)
@Composable
private fun StudyQueueOnboardingPreview() {
    AzriTheme {
        StudyQueueContent(state = StudyQueueUiState(loading = false), onStudyAll = {})
    }
}
