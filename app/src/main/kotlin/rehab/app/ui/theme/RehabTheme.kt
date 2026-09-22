package rehab.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

private val RehabColorScheme = darkColorScheme(
    primary = RehabColors.Accent,
    onPrimary = RehabColors.Bg,
    secondary = RehabColors.Accent,
    onSecondary = RehabColors.Bg,
    background = RehabColors.Bg,
    onBackground = RehabColors.Text,
    surface = RehabColors.Panel,
    onSurface = RehabColors.Text,
    surfaceVariant = RehabColors.Panel,
    onSurfaceVariant = RehabColors.Muted,
    surfaceContainer = RehabColors.Panel,
    surfaceContainerHigh = RehabColors.Panel,
    surfaceContainerHighest = RehabColors.Line,
    outline = RehabColors.Muted,
    outlineVariant = RehabColors.Line,
    error = RehabColors.Danger,
    onError = RehabColors.Bg,
)

// Typography() par défaut, chaque style recopié avec la police Chivo (aucun style de
// MaterialTheme.typography ne doit garder la police système par défaut).
private val defaultTypography = Typography()
private val RehabTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = Chivo),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = Chivo),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = Chivo),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = Chivo),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = Chivo),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = Chivo),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = Chivo),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = Chivo),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = Chivo),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = Chivo),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = Chivo),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = Chivo),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = Chivo),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = Chivo),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = Chivo),
)

@Composable
fun RehabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RehabColorScheme, typography = RehabTypography) {
        CompositionLocalProvider(
            LocalContentColor provides RehabColors.Text,
            LocalTextStyle provides TextStyle(fontFamily = Chivo, color = RehabColors.Text),
        ) {
            content()
        }
    }
}
