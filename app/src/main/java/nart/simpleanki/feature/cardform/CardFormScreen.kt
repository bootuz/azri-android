package nart.simpleanki.feature.cardform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nart.simpleanki.core.media.AudioRecorder
import nart.simpleanki.di.CardFormArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.AzriCard
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
@OptIn(ExperimentalMaterial3Api::class)
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
            CenterAlignedTopAppBar(
                title = { Text(if (state.isEdit) "Edit card" else "New card", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = state.canSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth()) {
            AzriCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    CardField(
                        label = "Front",
                        value = state.front,
                        onValueChange = onFrontChange,
                        placeholder = "Enter the question",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CardField(
                        label = "Back",
                        value = state.back,
                        onValueChange = onBackChange,
                        placeholder = "Enter the answer",
                    )

                    // Media previews
                    if (state.uploadingImage || state.uploadingAudio) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        UploadingRow(if (state.uploadingImage) "Uploading image…" else "Uploading audio…")
                    }
                    if (state.imagePath != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.padding(16.dp)) {
                            MediaImage(state.imagePath, Modifier.fillMaxWidth().height(160.dp))
                        }
                    }
                    if (state.audioPath != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AudioPlayButton(state.audioPath)
                            Text("Audio attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = onRemoveAudio) { Text("Remove") }
                        }
                    }

                    // Bottom toolbar (image · mic · reverse), mirroring the iOS editor
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolbarAction(Icons.Default.Image, "Add image", enabled = state.imagePath == null && !state.uploadingImage, onClick = onAddImage)
                        ToolbarAction(
                            icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            desc = if (isRecording) "Stop recording" else "Record audio",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            enabled = state.audioPath == null && !state.uploadingAudio,
                            onClick = onToggleRecording,
                        )
                        if (!state.isEdit) {
                            ToolbarAction(
                                icon = Icons.Default.SwapHoriz,
                                desc = "Also create reverse card",
                                tint = if (state.createReverse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { onToggleReverse(!state.createReverse) },
                            )
                        }
                    }
                }
            }
            if (!state.isEdit && state.createReverse) {
                Text(
                    "A reverse card (back → front) will also be created.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CardField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 2,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            padd
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun UploadingRow(label: String) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
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
