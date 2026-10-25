package kz.master_coffee.app.presentation

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kz.master_coffee.app.domain.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDialog(product: Product, state: CoffeeState, finish: (CartLine?) -> Unit) {
    val lang = state.language
    var sizeId by rememberSaveable(product.id) { mutableStateOf(product.sizes.first().id) }
    val size = product.sizes.firstOrNull { it.id == sizeId } ?: product.sizes.first()
    var sugar by rememberSaveable(product.id) { mutableStateOf(0f) }
    var type by rememberSaveable(product.id) { mutableStateOf("takeaway") }
    var temperature by rememberSaveable(product.id) { mutableStateOf("standard") }
    var addons by rememberSaveable(product.id) { mutableStateOf(listOf<String>()) }
    val catalogAddons = state.catalog?.addons.orEmpty()
    val estimate = size.price + catalogAddons.filter { it.id in addons }.sumOf { it.price }
    Dialog(onDismissRequest = { finish(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(catalogText(product.name, lang), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { finish(null) }) { Icon(Icons.Outlined.Close, text(Copy.Close, lang)) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp).testTag("customization_list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                        ProductImage(product.imageKey, null, Modifier.fillMaxWidth().height(220.dp).padding(18.dp))
                    }
                    Text(catalogText(product.description, lang), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CustomizationSection(text(Copy.Size, lang)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            product.sizes.forEach { variant -> FilterChip(modifier = Modifier.testTag("size_${variant.id}"), selected = size.id == variant.id, onClick = { sizeId = variant.id }, label = { Text("${variant.label} · ${variant.price} ₸") }) }
                        }
                    }
                    if (product.isDrink) {
                        val sugarLabel = text(Copy.Sugar, lang)
                        CustomizationSection(text(Copy.Sugar, lang) + ": ${sugar.toInt()}") {
                            Slider(value = sugar, onValueChange = { sugar = it }, valueRange = 0f..5f, steps = 4, modifier = Modifier.testTag("sugar").semantics { contentDescription = sugarLabel })
                        }
                    }
                    CustomizationSection {
                        ChoiceRow(listOf("takeaway" to Copy.Takeaway, "dine_in" to Copy.DineIn), type, lang) { type = it }
                    }
                    if (product.isDrink) Text(text(Copy.AddonHint, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (if (product.isDrink) listOf("syrup" to Copy.Syrup, "essence" to Copy.Essence, "milk" to Copy.Milk) else emptyList()).forEach { (group, label) ->
                        val groupAddons = catalogAddons.filter { it.group == group }
                        if (groupAddons.isNotEmpty()) {
                            CustomizationSection(text(label, lang)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    groupAddons.forEach { addon ->
                                        val selected = addon.id in addons
                                        val maxReached = groupAddons.count { it.id in addons } >= 2
                                        FilterChip(selected = selected, enabled = selected || !maxReached, onClick = { addons = if (selected) addons - addon.id else addons + addon.id }, label = { Text(catalogText(addon.name, lang) + if (addon.price != 0) " +${addon.price} ₸" else "") })
                                    }
                                }
                            }
                        }
                    }
                    if (product.isDrink) CustomizationSection {
                        ChoiceRow(listOf("standard" to Copy.Standard, "hot" to Copy.Hot, "cool" to Copy.Cool), temperature, lang) { temperature = it }
                    }
                    Text(text(Copy.Estimate, lang) + ": $estimate ₸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Button(onClick = { finish(CartLine(product.id, size.id, addonIds = addons.sorted(), sugar = sugar.toInt(), orderType = type, temperature = temperature)) }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp).testTag("product_add"), shape = RoundedCornerShape(18.dp), enabled = state.cafeId != null && !state.pending && !state.busy) { Text(text(Copy.AddToCart, lang)) }
                }
            }
        }
    }
}

@Composable
private fun CustomizationSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(choices: List<Pair<String, Copy>>, selected: String, lang: String, choose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { choices.forEach { (key, label) -> FilterChip(selected = selected == key, onClick = { choose(key) }, label = { Text(text(label, lang)) }) } }
}
