package kz.master_coffee.app.presentation

import mastercoffee.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
fun ProductImage(key: String, description: String?, modifier: Modifier, scale: ContentScale = ContentScale.Fit) {
    if (key.startsWith("https://") || key.startsWith("http://")) {
        coil3.compose.AsyncImage(model = key, contentDescription = description, modifier = modifier, contentScale = scale, error = painterResource(Res.drawable.master_logo2), placeholder = painterResource(Res.drawable.master_logo2))
    } else Image(painterResource(coffeeImage(key)), description, modifier, contentScale = scale)
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
