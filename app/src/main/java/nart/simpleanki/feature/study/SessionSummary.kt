package nart.simpleanki.feature.study

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.ui.theme.AzriTheme

// iOS rating palette (shared with the study rating buttons): again=pink, hard=orange, good=indigo, easy=mint.
private val RatingColors = mapOf(
    Rating.Again to Color(0xFFFF2D55),
    Rating.Hard to Color(0xFFFF9500),
    Rating.Good to Color(0xFF5856D6),
    Rating.Easy to Color(0xFF00C7BE),
)

/** Study "session complete" summary — mirrors iOS SessionSummaryView (streak omitted). */
@Composable
fun SessionSummary(state: StudyUiState, onDone: () -> Unit) {
    val accuracy = sessionAccuracy(state.ratingCounts)
    val haptics = LocalHapticFeedback.current
    var appeared by remember { mutableStateOf(false) }
    val emojiScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "emojiScale",
    )
    val emojiAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "emojiAlpha",
    )
    LaunchedEffect(Unit) {
        appeared = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text("🎉", fontSize = 64.sp, modifier = Modifier.scale(emojiScale).alpha(emojiAlpha))
        Spacer(Modifier.height(16.dp))
        Text("Session Complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            motivationalMessage(accuracy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        SessionStatsRow(
            reviewed = state.completed,
            accuracy = accuracy,
            durationLabel = formattedDuration(state.durationMillis),
        )

        if (state.completed > 0) {
            Spacer(Modifier.height(32.dp))
            RatingDistributionBar(state.ratingCounts)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Finish", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun SessionStatsRow(reviewed: Int, accuracy: Int, durationLabel: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(Icons.Outlined.CheckCircle, Color(0xFF34C759), reviewed.toString(), "Reviewed")
        VerticalDivider(Modifier.height(44.dp))
        StatItem(Icons.Outlined.TrackChanges, accuracyColor(accuracy), "$accuracy%", "Accuracy")
        VerticalDivider(Modifier.height(44.dp))
        StatItem(Icons.Outlined.Schedule, Color(0xFFFF9500), durationLabel, "Duration")
    }
}

@Composable
private fun StatItem(icon: ImageVector, iconColor: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RatingDistributionBar(ratingCounts: Map<Rating, Int>) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
            Rating.entries.forEach { rating ->
                val count = ratingCounts[rating] ?: 0
                if (count > 0) {
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(RatingColors.getValue(rating)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Rating.entries.forEach { rating ->
                LegendItem(rating.name, ratingCounts[rating] ?: 0, RatingColors.getValue(rating))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Accuracy -> color, mirroring iOS accuracyColor (>=80 mint, >=60 indigo, >=40 pink, else orange). */
private fun accuracyColor(accuracy: Int): Color = when {
    accuracy >= 80 -> Color(0xFF00C7BE)
    accuracy >= 60 -> Color(0xFF5856D6)
    accuracy >= 40 -> Color(0xFFFF2D55)
    else -> Color(0xFFFF9500)
}

@Preview(name = "Summary · good session", showBackground = true)
@Composable
private fun SessionSummaryGoodPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 25, durationMillis = 312_000,
                ratingCounts = mapOf(Rating.Again to 4, Rating.Hard to 6, Rating.Good to 10, Rating.Easy to 5),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Summary · low accuracy", showBackground = true)
@Composable
private fun SessionSummaryLowPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 10, durationMillis = 180_000,
                ratingCounts = mapOf(Rating.Again to 5, Rating.Hard to 2, Rating.Good to 2, Rating.Easy to 1),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Summary · all easy", showBackground = true)
@Composable
private fun SessionSummaryAllEasyPreview() {
    AzriTheme {
        SessionSummary(
            state = StudyUiState(
                loading = false, finished = true, completed = 10, durationMillis = 180_000,
                ratingCounts = mapOf(Rating.Easy to 10),
            ),
            onDone = {},
        )
    }
}
