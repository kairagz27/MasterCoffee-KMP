package kz.master_coffee.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import mastercoffee.composeapp.generated.resources.*

private val DarkColorScheme = darkColorScheme(
    primary = MasterTealLight,
    primaryContainer = Color(0xFF164B50), onPrimaryContainer = Color(0xFFBDEDF0),
    secondary = Color(0xFFA1DCE0), onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF164B50), onSecondaryContainer = Color(0xFFBDEDF0),
    tertiary = Color(0xFFA1DCE0), onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF164B50), onTertiaryContainer = Color(0xFFBDEDF0),
    surfaceVariant = Color(0xFF263638), onSurfaceVariant = Color(0xFFB7C9CB),
    surfaceContainer = Color(0xFF1B2324), surfaceContainerLow = Color(0xFF151D1E),
    surfaceContainerHigh = Color(0xFF253031), surfaceContainerHighest = Color(0xFF303C3D),
    outline = Color(0xFF879C9E), outlineVariant = Color(0xFF3E5052),
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color(0xFF00363B),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MasterCoffeeTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val family = FontFamily(Font(Res.font.montserrat_regular), Font(Res.font.montserrat_medium, FontWeight.Medium), Font(Res.font.montserrat_bold, FontWeight.Bold))
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else lightColorScheme(
            primary = MasterTeal, onPrimary = Color.White,
            primaryContainer = Color(0xFFD0ECEE), onPrimaryContainer = Color(0xFF103F44),
            secondary = Color(0xFF25656C), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD0ECEE), onSecondaryContainer = Color(0xFF103F44),
            tertiary = Color(0xFF25656C), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD0ECEE), onTertiaryContainer = Color(0xFF103F44),
            background = Color(0xFFF8F9FA), surface = Color.White,
            surfaceVariant = Color(0xFFE1ECEC), onSurfaceVariant = Color(0xFF3F4D4F),
            surfaceContainer = Color(0xFFEDF3F3), surfaceContainerLow = Color(0xFFF2F7F7),
            surfaceContainerHigh = Color(0xFFE5EEEE), surfaceContainerHighest = Color(0xFFDDE8E8),
            outline = Color(0xFF6F7F80), outlineVariant = Color(0xFFBFCFD0)
        ),
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family), displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family), headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family)
        ),
        content = content
    )
}
