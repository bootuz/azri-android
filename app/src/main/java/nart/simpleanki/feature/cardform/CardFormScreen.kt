package nart.simpleanki.feature.cardform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nart.simpleanki.core.media.AudioRecorder
import nart.simpleanki.di.CardFormArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.MediaImage
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CardFormScreen(
    deckId: String?,
    cardId: String?,
    onClose: () -> Unit,
    viewModel: CardFormViewModel = koinViewModel { parametersOf(CardFormArgs(deckId, cardId)) },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // A new-card save keeps the editor open (the inputs reset); show a toast so the
    // user knows it landed and can add another. Keyed on the tick so it re-fires each save.
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) snackbarHostState.showSnackbar("Card saved", withDismissAction = true)
    }
    // Editing an existing card closes the editor when the save completes.
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.onImagePicked(bytes)
        }
    }

    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    fun startRecording() {
        runCatching { recorder.start() }.onSuccess { isRecording = true }
    }
    fun stopRecording() {
        val bytes = recorder.stop()
        isRecording = false
        if (bytes != null) viewModel.onAudioRecorded(bytes)
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startRecording() }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    CardFormContent(
        state = state,
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        onFrontChange = viewModel::onFrontChange,
        onBackChange = viewModel::onBackChange,
        onSelectDeck = viewModel::onSelectDeck,
        onToggleReverse = viewModel::onToggleReverse,
        onAddImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = viewModel::onRemoveImage,
        onToggleRecording = ::toggleRecording,
        onRemoveAudio = viewModel::onRemoveAudio,
        onSave = viewModel::save,
        onBack = onClose,
    )
}

/** Stateless card-form UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormContent(
    state: CardFormUiState,
    isRecording: Boolean,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onSelectDeck: (String) -> Unit,
    onToggleReverse: (Boolean) -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onToggleRecording: () -> Unit,
    onRemoveAudio: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val frontFocus = remember { FocusRequester() }
    // Autofocus Front on open (and re-focus after each save) so the user can type immediately.
    LaunchedEffect(Unit) { runCatching { frontFocus.requestFocus() } }
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) runCatching { frontFocus.requestFocus() }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text(data.visuals.message, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit card" else "New card") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                // imePadding() is on the bar (not the Scaffold/content) so it rises above the keyboard;
                // edge-to-edge is enabled in MainActivity. Scaffold then pads content by the bar's height.
                modifier = Modifier.imePadding(),
                actions = {
                    if (state.imageName == null) {
                        IconButton(onClick = onAddImage, enabled = !state.uploadingImage) {
                            if (state.uploadingImage) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Image, contentDescription = "Add image")
                            }
                        }
                    }
                    if (state.audioName == null) {
                        IconButton(onClick = onToggleRecording, enabled = !state.uploadingAudio) {
                            when {
                                state.uploadingAudio ->
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                isRecording ->
                                    Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = MaterialTheme.colorScheme.error)
                                else ->
                                    Icon(Icons.Default.Mic, contentDescription = "Record audio")
                            }
                        }
                    }
                    if (!state.isEdit) {
                        IconToggleButton(
                            checked = state.createReverse,
                            onCheckedChange = onToggleReverse,
                        ) {
                            Icon(
                                if (state.createReverse) Icons.Default.Check else Icons.Default.SwapHoriz,
                                contentDescription = "Also create reverse card",
                            )
                        }
                    }
                },
                floatingActionButton = {
                    // M3 FABs have no `enabled` flag: show disabled via muted colors + a gated click.
                    val saveEnabled = state.canSave
                    FloatingActionButton(
                        onClick = { if (saveEnabled) onSave() },
                        modifier = if (saveEnabled) Modifier else Modifier.semantics { disabled() },
                        containerColor = if (saveEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (saveEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var deckMenuExpanded by remember { mutableStateOf(false) }
            if (state.pickDeck) {
                val selectedDeckName =
                    state.decks.firstOrNull { it.id == state.selectedDeckId }?.name ?: ""
                ExposedDropdownMenuBox(
                    expanded = deckMenuExpanded,
                    onExpandedChange = { deckMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedDeckName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deck") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deckMenuExpanded) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = deckMenuExpanded,
                        onDismissRequest = { deckMenuExpanded = false },
                    ) {
                        state.decks.forEach { deck ->
                            DropdownMenuItem(
                                text = { Text(deck.name) },
                                onClick = { onSelectDeck(deck.id); deckMenuExpanded = false },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Front") },
                placeholder = { Text("Question") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(frontFocus),
            )
            OutlinedTextField(
                value = state.back,
                onValueChange = onBackChange,
                label = { Text("Back") },
                placeholder = { Text("Answer") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Image preview
            if (state.imageName != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.large),
                ) {
                    MediaImage(
                        state.imageName,
                        state.imagePath,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    FilledIconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.45f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove image", modifier = Modifier.size(18.dp))
                    }
                }
            }
            // Audio attached
            if (state.audioName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioPlayButton(state.audioName, state.audioPath)
                    Text("Audio attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRemoveAudio) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove audio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Reverse-card hint: shown while the bottom-bar reverse toggle is on (new cards only).
            if (state.createReverse && !state.isEdit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "A reverse card (Back → Front) will also be created.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        }
    }
}

@Preview(name = "Card form · new", showBackground = true)
@Composable
private fun CardFormNewPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(front = "Bonjour", back = "Hello", createReverse = true),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}

@Preview(name = "Card form · recording", showBackground = true)
@Composable
private fun CardFormRecordingPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(front = "cat", back = "gato", isEdit = true),
            isRecording = true,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}

@Preview(name = "Card form · pick deck (empty)", showBackground = true)
@Composable
private fun CardFormPickDeckPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                pickDeck = true,
                decks = listOf(DeckOption("d1", "French"), DeckOption("d2", "Spanish")),
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}

@Preview(name = "Card form · pick deck (selected)", showBackground = true)
@Composable
private fun CardFormPickDeckSelectedPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                front = "bonjour", back = "hello",
                pickDeck = true, selectedDeckId = "d2",
                decks = listOf(DeckOption("d1", "French"), DeckOption("d2", "Spanish")),
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}

@Preview(name = "Card form · with attachments", showBackground = true)
@Composable
private fun CardFormWithAttachmentsPreview() {
    AzriTheme {
        CardFormContent(
            state = CardFormUiState(
                front = "dog", back = "perro",
                imageName = "img.jpg", audioName = "audio.m4a",
            ),
            isRecording = false,
            onFrontChange = {}, onBackChange = {}, onSelectDeck = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}
