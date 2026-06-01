package nart.simpleanki.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Standard surface card used for list rows — continuous radius-20 surface on the
 * (slightly gray) page background, with a soft shadow. Mirrors the iOS `.defaultRow()`.
 */
@Composable
fun AzriCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = modifier,
            content = content,
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = modifier,
            content = content,
        )
    }
}

/** Rounded square icon tinted with a deck/folder color (color @ ~16% over surface). */
@Composable
fun ColorAccentIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 36,
    content: @Composable () -> Unit,
) {
    Surface(
        color = tint.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface),
        contentColor = tint,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(size.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
