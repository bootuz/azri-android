package nart.simpleanki.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.ui.theme.toColor

/** Standard deck list row: color icon + name + card count + chevron. Shared by Library and folder detail. */
@Composable
fun DeckRow(deck: Deck, cardCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AzriCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorAccentIcon(tint = deck.color.toColor()) {
                Icon(Icons.Outlined.StickyNote2, null, Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (cardCount == 0) "No cards" else "$cardCount ${if (cardCount == 1) "card" else "cards"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
