package kz.master_coffee.app.presentation

import mastercoffee.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale

/** Original brand artwork remains readable in both palettes, including image fallbacks. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(Res.drawable.master_logo), description, modifier,
        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(Res.drawable.master_logo2), description, modifier,
        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
}

@Composable
fun ProductImage(key: String, description: String?, modifier: Modifier, scale: ContentScale = ContentScale.Fit) {
    if (key.startsWith("https://") || key.startsWith("http://")) {
        var showingFallback by remember(key) { mutableStateOf(true) }
        coil3.compose.AsyncImage(
            model = key, contentDescription = description, modifier = modifier,
            contentScale = scale,
            error = painterResource(Res.drawable.master_logo2), placeholder = painterResource(Res.drawable.master_logo2),
            onLoading = { showingFallback = true }, onError = { showingFallback = true }, onSuccess = { showingFallback = false },
            colorFilter = if (showingFallback) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null,
        )
    } else {
        val resource = coffeeImage(key)
        val isBrandFallback = resource == Res.drawable.master_logo2
        Image(painterResource(resource), description, modifier,
            contentScale = if (isBrandFallback) ContentScale.Fit else scale,
            colorFilter = if (isBrandFallback) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null)
    }
}

/** API and persisted data contain stable keys only. Resource objects never cross the UI boundary. */
fun coffeeImage(key: String): DrawableResource = when (key) {
    "espresso" -> Res.drawable.espresso
    "americano" -> Res.drawable.americano
    "cappuccino" -> Res.drawable.cappuccino
    "latte" -> Res.drawable.latte
    "flatwhite" -> Res.drawable.flatwhite
    "raf" -> Res.drawable.raf
    "hotcherry" -> Res.drawable.hotcherry
    "batchdaiquiri" -> Res.drawable.batchdaiquiri
    "spicedlatte" -> Res.drawable.spicedlatte
    "rafhogwarts" -> Res.drawable.rafhogwarts
    "glintwine" -> Res.drawable.glintwine
    "batchkunshuaq" -> Res.drawable.batchkunshuaq
    "matchalatte" -> Res.drawable.matchalatte
    "batchbrew" -> Res.drawable.batchbrew
    "kabanbaybatyra6018" -> Res.drawable.kabanbaybatyra6018
    "megasilkway" -> Res.drawable.megasilkway
    "kabanbaybatyra56a" -> Res.drawable.kabanbaybatyra56a
    "ulydala45" -> Res.drawable.ulydala45
    "tyran525" -> Res.drawable.tyran525
    "mangelikel37" -> Res.drawable.mangelikel37
    "turan44b" -> Res.drawable.turan44b
    "alfarabi11" -> Res.drawable.alfarabi11
    "turkestan8" -> Res.drawable.turkestan8
    "syganak522" -> Res.drawable.syganak522
    "tolebi52" -> Res.drawable.tolebi52
    "dinmuhamedkonaev35" -> Res.drawable.dinmuhamedkonaev35
    "story1" -> Res.drawable.story1
    "story2" -> Res.drawable.story2
    "story3" -> Res.drawable.story3
    else -> Res.drawable.master_logo2
}
