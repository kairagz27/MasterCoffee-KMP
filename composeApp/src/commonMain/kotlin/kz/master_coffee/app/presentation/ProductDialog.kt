package kz.master_coffee.app.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kz.master_coffee.app.domain.*
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDialog(product: Product, state: CoffeeState, finish: (CartLine?) -> Unit) {
    val lang = state.language
    var size by remember(product.id) { mutableStateOf(product.sizes.first()) }
    var sugar by remember { mutableStateOf(0f) }
    var type by remember { mutableStateOf("takeaway") }
    var temperature by remember { mutableStateOf("standard") }
    var addons by remember { mutableStateOf(setOf<String>()) }
    val catalogAddons = state.catalog?.addons.orEmpty()
    val estimate = size.price + catalogAddons.filter { it.id in addons }.sumOf { it.price }
    Dialog(onDismissRequest = { finish(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(catalogText(product.name, lang), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { finish(null) }) { Icon(Icons.Outlined.Close, text(Copy.Close, lang)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(coffeeImage(product.imageKey)), null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
                    Text(catalogText(product.description, lang), style = MaterialTheme.typography.bodyLarge)
                    Text(text(Copy.Size, lang), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        product.sizes.forEach { variant -> FilterChip(selected = size.id == variant.id, onClick = { size = variant }, label = { Text("${variant.label} · ${variant.price} ₸") }) }
                    }
                    Text(text(Copy.Sugar, lang) + ": ${sugar.toInt()}", style = MaterialTheme.typography.titleMedium)
                    Slider(value = sugar, onValueChange = { sugar = it }, valueRange = 0f..5f, steps = 4)
                    ChoiceRow(listOf("takeaway" to Copy.Takeaway, "dine_in" to Copy.DineIn), type, lang) { type = it }
                    Text(text(Copy.AddonHint, lang), style = MaterialTheme.typography.bodySmall)
                    listOf("syrup" to Copy.Syrup, "essence" to Copy.Essence, "milk" to Copy.Milk).forEach { (group, label) ->
                        val groupAddons = catalogAddons.filter { it.group == group }
                        if (groupAddons.isNotEmpty()) {
                            Text(text(label, lang), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupAddons.forEach { addon ->
                                    val selected = addon.id in addons
                                    val maxReached = groupAddons.count { it.id in addons } >= 2
                                    FilterChip(selected = selected, enabled = selected || !maxReached, onClick = { addons = if (selected) addons - addon.id else addons + addon.id }, label = { Text(catalogText(addon.name, lang) + if (addon.price != 0) " +${addon.price} ₸" else "") })
                                }
                            }
                        }
                    }
                    ChoiceRow(listOf("standard" to Copy.Standard, "hot" to Copy.Hot, "cool" to Copy.Cool), temperature, lang) { temperature = it }
                    Text(text(Copy.Estimate, lang) + ": $estimate ₸", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { finish(CartLine(product.id, size.id, addonIds = addons.sorted(), sugar = sugar.toInt(), orderType = type, temperature = temperature)) }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), enabled = state.cafeId != null && !state.pending && !state.busy) { Text(text(Copy.AddToCart, lang)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(choices: List<Pair<String, Copy>>, selected: String, lang: String, choose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { (key, label) -> FilterChip(selected = selected == key, onClick = { choose(key) }, label = { Text(text(label, lang)) }) } }
}
