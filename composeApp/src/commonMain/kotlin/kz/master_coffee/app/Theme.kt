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
    primary = MasterTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MasterCoffeeTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val family = FontFamily(Font(Res.font.montserrat_regular), Font(Res.font.montserrat_medium, FontWeight.Medium), Font(Res.font.montserrat_bold, FontWeight.Bold))
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else lightColorScheme(primary = MasterTeal, background = Color(0xFFF8F9FA), surface = Color.White),
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
