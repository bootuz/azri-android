package nart.simpleanki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SAPrimary,
    onPrimary = SAOnPrimary,
    background = SABackgroundLight,
    onBackground = SAPrimaryText,
    surface = SABackgroundLight,
    onSurface = SAPrimaryText,
    onSurfaceVariant = SASecondaryText,
)

private val DarkColors = darkColorScheme(
    primary = SAPrimaryDark,
    onPrimary = SAPrimaryText,
    background = SABackgroundDark,
    onBackground = SAOnPrimary,
    surface = SASurfaceDark,
    onSurface = SAOnPrimary,
    onSurfaceVariant = SASecondaryText,
)

@Composable
fun AzriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AzriTypography,
        content = content,
    )
}
