package nart.simpleanki.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel

private enum class ReminderKind { Study, Goal }

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var awaiting by remember { mutableStateOf<ReminderKind?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val kind = awaiting
        awaiting = null
        if (granted) when (kind) {
            ReminderKind.Study -> viewModel.setStudy(true, state.studyHour, state.studyMinute)
            ReminderKind.Goal -> viewModel.setGoal(true, state.goalHour, state.goalMinute)
            null -> {}
        }
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    NotificationsContent(
        state = state,
        onStudyToggle = { on ->
            when {
                !on -> viewModel.setStudy(false, state.studyHour, state.studyMinute)
                hasPermission() -> viewModel.setStudy(true, state.studyHour, state.studyMinute)
                else -> { awaiting = ReminderKind.Study; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        },
        onStudyTime = { h, m -> viewModel.setStudy(state.studyEnabled, h, m) },
        onGoalToggle = { on ->
            when {
                !on -> viewModel.setGoal(false, state.goalHour, state.goalMinute)
                hasPermission() -> viewModel.setGoal(true, state.goalHour, state.goalMinute)
                else -> { awaiting = ReminderKind.Goal; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        },
        onGoalTime = { h, m -> viewModel.setGoal(state.goalEnabled, h, m) },
        onBack = onBack,
    )
}

/** Stateless notifications UI, decoupled from the ViewModel + permission flow for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    onStudyToggle: (Boolean) -> Unit,
    onStudyTime: (Int, Int) -> Unit,
    onGoalToggle: (Boolean) -> Unit,
    onGoalTime: (Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ReminderRow(
                title = "Daily study reminder",
                subtitle = "A nudge to study at the same time every day",
                enabled = state.studyEnabled,
                hour = state.studyHour,
                minute = state.studyMinute,
                onToggle = onStudyToggle,
                onTime = onStudyTime,
            )
            ReminderRow(
                title = "Goal reminder",
                subtitle = "Reminds you in the evening if you haven't hit today's goal",
                enabled = state.goalEnabled,
                hour = state.goalHour,
                minute = state.goalMinute,
                onToggle = onGoalToggle,
                onTime = onGoalTime,
            )
        }
    }
}

@Composable
private fun ReminderRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onToggle: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Row(
                Modifier.fillMaxWidth().clickable { showPicker = true }.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatTime(hour, minute),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

    if (showPicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m -> onTime(h, m); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}

/** 24h hour/minute → "9:00 AM". */
internal fun formatTime(hour: Int, minute: Int): String {
    val isAm = hour < 12
    val h12 = (hour % 12).let { if (it == 0) 12 else it }
    return "%d:%02d %s".format(h12, minute, if (isAm) "AM" else "PM")
}

@Preview(name = "Notifications", showBackground = true)
@Composable
private fun NotificationsPreview() {
    AzriTheme {
        NotificationsContent(
            state = NotificationsUiState(studyEnabled = true, studyHour = 9, goalEnabled = false, goalHour = 20),
            onStudyToggle = {}, onStudyTime = { _, _ -> }, onGoalToggle = {}, onGoalTime = { _, _ -> }, onBack = {},
        )
    }
}
