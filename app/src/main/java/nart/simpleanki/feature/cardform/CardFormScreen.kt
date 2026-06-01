package nart.simpleanki.feature.cardform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
    deckId: String,
    cardId: String?,
    onDone: () -> Unit,
    viewModel: CardFormViewModel = koinViewModel { parametersOf(CardFormArgs(deckId, cardId)) },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

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
        isRecording = isRecording,
        onFrontChange = viewModel::onFrontChange,
        onBackChange = viewModel::onBackChange,
        onToggleReverse = viewModel::onToggleReverse,
        onAddImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = viewModel::onRemoveImage,
        onToggleRecording = ::toggleRecording,
        onRemoveAudio = viewModel::onRemoveAudio,
        onSave = viewModel::save,
        onBack = onDone,
    )
}

/** Stateless card-form UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardFormContent(
    state: CardFormUiState,
    isRecording: Boolean,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onToggleReverse: (Boolean) -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onToggleRecording: () -> Unit,
    onRemoveAudio: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
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
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) { Text("Save") }
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
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Front") },
                placeholder = { Text("Question") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
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
            if (state.imagePath != null) {
                Box(Modifier.fillMaxWidth()) {
                    MediaImage(
                        state.imagePath,
                        Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.large),
                    )
                    FilledTonalIconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) { Icon(Icons.Default.Close, contentDescription = "Remove image") }
                }
            }
            // Audio attached
            if (state.audioPath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioPlayButton(state.audioPath)
                    Text("Audio attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRemoveAudio) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove audio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Attachment actions (Material chips)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.imagePath == null) {
                    AssistChip(
                        onClick = onAddImage,
                        enabled = !state.uploadingImage,
                        leadingIcon = {
                            if (state.uploadingImage) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, Modifier.height(18.dp))
                            }
                        },
                        label = { Text(if (state.uploadingImage) "Uploading…" else "Add image") },
                    )
                }
                if (state.audioPath == null) {
                    AssistChip(
                        onClick = onToggleRecording,
                        enabled = !state.uploadingAudio,
                        leadingIcon = {
                            when {
                                state.uploadingAudio -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                isRecording -> Icon(Icons.Default.Stop, contentDescription = null, Modifier.height(18.dp), tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Default.Mic, contentDescription = null, Modifier.height(18.dp))
                            }
                        },
                        label = {
                            Text(
                                when {
                                    state.uploadingAudio -> "Uploading…"
                                    isRecording -> "Stop recording"
                                    else -> "Record audio"
                                },
                            )
                        },
                    )
                }
            }

            if (!state.isEdit) {
                FilterChip(
                    selected = state.createReverse,
                    onClick = { onToggleReverse(!state.createReverse) },
                    label = { Text("Also create reverse card") },
                    leadingIcon = if (state.createReverse) {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.height(18.dp)) }
                    } else {
                        { Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.height(18.dp)) }
                    },
                )
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
            onFrontChange = {}, onBackChange = {}, onToggleReverse = {},
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
            onFrontChange = {}, onBackChange = {}, onToggleReverse = {},
            onAddImage = {}, onRemoveImage = {}, onToggleRecording = {}, onRemoveAudio = {},
            onSave = {}, onBack = {},
        )
    }
}
