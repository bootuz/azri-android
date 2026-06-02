package nart.simpleanki.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.data.settings.DAILY_GOAL_MAX_PER_BUCKET
import nart.simpleanki.core.data.settings.DAILY_GOAL_MIN_PER_BUCKET
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel

/** Modal editor for the daily goal, opened from the Queue's goal card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalEditorSheet(
    onDismiss: () -> Unit,
    viewModel: DailyGoalViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DailyGoalEditorContent(
            state = state,
            onSetEnabled = viewModel::setEnabled,
            onSetNewCardsTarget = viewModel::setNewCardsTarget,
            onSetReviewCardsTarget = viewModel::setReviewCardsTarget,
            onReset = viewModel::resetToDefaults,
        )
    }
}

/** Stateless editor body, decoupled from the ViewModel for testing/preview. */
@Composable
fun DailyGoalEditorContent(
    state: DailyGoalUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetNewCardsTarget: (Int) -> Unit,
    onSetReviewCardsTarget: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Daily goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Goal tracking", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Track how many cards you study each day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onSetEnabled)
        }

        if (state.enabled) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.total.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "cards / day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            StepperRow("New cards", "Brand-new cards per day", state.newCardsTarget, onSetNewCardsTarget)
            StepperRow("Reviews", "Due cards to clear per day", state.reviewCardsTarget, onSetReviewCardsTarget)
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to defaults")
            }
        } else {
            Text(
                "Tracking paused. Turn it on whenever you want a gentle daily nudge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StepperRow(title: String, subtitle: String, value: Int, onChange: (Int) -> Unit) {
    val step = 5
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(DAILY_GOAL_MIN_PER_BUCKET)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $title")
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(DAILY_GOAL_MAX_PER_BUCKET)) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $title")
        }
    }
}

@Preview(name = "Daily goal editor", showBackground = true)
@Composable
private fun DailyGoalEditorPreview() {
    AzriTheme {
        DailyGoalEditorContent(
            state = DailyGoalUiState(enabled = true, newCardsTarget = 10, reviewCardsTarget = 20),
            onSetEnabled = {}, onSetNewCardsTarget = {}, onSetReviewCardsTarget = {}, onReset = {},
        )
    }
}
