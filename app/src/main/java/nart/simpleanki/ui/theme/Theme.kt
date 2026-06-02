package nart.simpleanki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = SAPrimary,
    onPrimary = SAOnPrimary,
    primaryContainer = SAPrimaryContainerLight,
    onPrimaryContainer = SAPrimaryTextLight,
    secondary = SAPrimary,
    onSecondary = SAOnPrimary,
    // Periwinkle tonal container (selected nav pill, chips) — overrides M3's default purple.
    secondaryContainer = SAPrimaryContainerLight,
    onSecondaryContainer = SAPrimaryTextLight,
    background = SAScreenLight,
    onBackground = SAPrimaryTextLight,
    surface = SASurfaceLight,
    onSurface = SAPrimaryTextLight,
    surfaceVariant = SASurfaceVariantLight,
    onSurfaceVariant = SASecondaryTextLight,
    outlineVariant = SAOutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = SAPrimary,
    onPrimary = SAOnPrimary,
    primaryContainer = SAPrimaryContainerDark,
    onPrimaryContainer = SAPrimaryTextDark,
    secondary = SAPrimary,
    onSecondary = SAOnPrimary,
    secondaryContainer = SAPrimaryContainerDark,
    onSecondaryContainer = SAPrimaryTextDark,
    background = SAScreenDark,
    onBackground = SAPrimaryTextDark,
    surface = SASurfaceDark,
    onSurface = SAPrimaryTextDark,
    surfaceVariant = SASurfaceVariantDark,
    onSurfaceVariant = SASecondaryTextDark,
    outlineVariant = SAOutlineDark,
)

// iOS radius scale: sm 8, md 12, lg 20 (default card/row), xl 30 (pill).
private val AzriShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AzriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AzriTypography,
        shapes = AzriShapes,
        content = content,
    )
}
