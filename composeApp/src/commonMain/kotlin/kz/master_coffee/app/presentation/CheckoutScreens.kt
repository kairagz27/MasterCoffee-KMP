package kz.master_coffee.app.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.datetime.*
import kz.master_coffee.app.domain.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import mastercoffee.composeapp.generated.resources.Res
import mastercoffee.composeapp.generated.resources.card_bg
import org.jetbrains.compose.resources.painterResource

@Composable
fun CartScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    val cart = state.cart
    LazyColumn(Modifier.fillMaxSize().testTag("cart_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(text(Copy.Cart, lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (state.pending) item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text(Copy.PendingHint, lang))
                    Button(onClick = model::confirmOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(text(Copy.RecoverOrder, lang)) }
                }
            }
        }
        if (cart == null || cart.items.isEmpty()) {
            item { Text(text(Copy.CartEmpty, lang)); TextButton(onClick = { model.navigate(Screen.MENU) }) { Text(text(Copy.Menu, lang)) } }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Text(state.catalog?.cafes?.firstOrNull { it.id == cart.cafeId }?.address ?: "#${cart.cafeId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            itemsIndexed(cart.items) { index, line ->
                val product = state.catalog?.products?.firstOrNull { it.id == line.productId }
                val size = product?.sizes?.firstOrNull { it.id == line.sizeId }
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(product?.let { catalogText(it.name, lang) } ?: "#${line.productId}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(size?.label ?: line.sizeId, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        line.addonIds.mapNotNull { id -> state.catalog?.addons?.firstOrNull { it.id == id } }.forEach { Text(catalogText(it.name, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (product?.isDrink != false) Text(lineOptions(line.sugar, line.orderType, line.temperature, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text(text(if (line.orderType == "takeaway") Copy.Takeaway else Copy.DineIn, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { model.quantity(index, line.quantity - 1) }, enabled = !state.pending && !state.busy) { Icon(if (line.quantity == 1) Icons.Outlined.Delete else Icons.Outlined.Remove, text(Copy.Remove, lang)) }
                                    Text("${line.quantity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { model.quantity(index, line.quantity + 1) }, enabled = !state.pending && !state.busy && line.quantity < 20) { Icon(Icons.Outlined.Add, text(Copy.Quantity, lang)) }
                                }
                            }
                            if (size != null) {
                                val extra = line.addonIds.sumOf { id -> state.catalog?.addons?.firstOrNull { it.id == id }?.price ?: 0 }
                                Text("${(size.price + extra) * line.quantity} ₸", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
            item { OutlinedTextField(cart.comment, model::comment, label = { Text(text(Copy.Comment, lang)) }, enabled = !state.pending && !state.busy, modifier = Modifier.fillMaxWidth(), maxLines = 4, shape = RoundedCornerShape(16.dp)) }
            item { Text(text(Copy.Estimate, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(text(Copy.PricingUnit, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!state.pending) item { Button(onClick = model::calculate, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("cart_calculate"), shape = RoundedCornerShape(18.dp)) { Text(text(Copy.Calculate, lang)) } }
        }
        state.quote?.let { quote -> item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text(Copy.CartReview, lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    quote.items.forEach { OrderLineSummary(it, lang) }
                    HorizontalDivider()
                    AmountRow(text(Copy.Subtotal, lang), quote.subtotal)
                    AmountRow(text(Copy.Discount, lang) + " ${quote.discountPercent}%", -quote.discount)
                    AmountRow(text(Copy.Total, lang), quote.total, highlighted = true)
                    Text(text(Copy.QuoteHint, lang), style = MaterialTheme.typography.bodyMedium)
                    Text(text(Copy.Expires, lang) + ": " + localTime(quote.expiresAt), style = MaterialTheme.typography.bodySmall)
                    if (!state.pending) Button(onClick = model::confirmOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("cart_confirm"), shape = RoundedCornerShape(18.dp)) { Text(text(Copy.ConfirmOrder, lang)) }
                }
            }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    var confirmCancel by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("orders_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text(text(Copy.Orders, lang), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall); IconButton(onClick = model::refreshAccount, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, text(Copy.Refresh, lang)) } } }
        state.activeOrder?.let { order -> item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text(Copy.Order, lang) + " № ${order.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                            Text(statusText(order.status, lang), modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).testTag("order_status").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                            Text(text(if (order.paymentStatus == "paid") Copy.Paid else Copy.Unpaid, lang), Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (order.status == "ready") Text(text(Copy.ReadyHint, lang))
                    Text(state.catalog?.cafes?.firstOrNull { it.id == order.cafeId }?.address.orEmpty())
                    Text(localTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    order.items.forEach { OrderLineSummary(it, lang) }
                    if (order.comment.isNotEmpty()) Text(order.comment)
                    AmountRow(text(Copy.Total, lang), order.total, highlighted = true)
                    Text(text(Copy.Discount, lang) + ": ${order.discountPercent}% · ${order.discount} ₸", style = MaterialTheme.typography.bodySmall)
                    if (order.active && order.paymentStatus == "unpaid") {
                        Text(text(Copy.AtCounter, lang), style = MaterialTheme.typography.titleMedium)
                        order.qr?.let { qr ->
                            val expired = runCatching { Instant.parse(qr.expiresAt) <= Clock.System.now() }.getOrDefault(true)
                            if (!expired && !qr.used) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Image(rememberQrCodePainter(qr.payload), text(Copy.ShowToStaff, lang), Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).aspectRatio(1f).background(Color.White).padding(16.dp))
                                }
                                SelectionContainer { Text(qr.payload, style = MaterialTheme.typography.bodySmall) }
                            } else Text(text(if (qr.used) Copy.QrUsed else Copy.QrExpired, lang), color = MaterialTheme.colorScheme.error)
                            Text(text(Copy.Expires, lang) + ": " + localTime(qr.expiresAt), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(text(Copy.ShowToStaff, lang), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = model::refreshQr, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.RefreshQr, lang)) }
                    }
                    OutlinedButton(onClick = model::refreshOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("order_refresh")) { Text(text(Copy.Refresh, lang)) }
                    if (order.paymentStatus == "unpaid" && order.status == "created") {
                        Text(text(Copy.OrderCancelledHint, lang), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { confirmCancel = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.CancelOrder, lang)) }
                    }
                }
            }
        } }
        if (state.orders.isEmpty()) item { Text(text(Copy.HistoryEmpty, lang)) }
        items(state.orders, key = { it.id }) { order ->
            Card(onClick = { model.openOrder(order) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("№ ${order.number} · ${order.total} ₸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(statusText(order.status, lang) + " · " + text(if (order.paymentStatus == "paid") Copy.Paid else Copy.Unpaid, lang), color = MaterialTheme.colorScheme.primary)
                    Text(localTime(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.nextBefore != null) item { OutlinedButton(onClick = model::moreOrders, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.More, lang)) } }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text(text(Copy.CancelOrder, lang)) }, text = { Text(text(Copy.OrderCancelledHint, lang)) }, confirmButton = { TextButton(onClick = { confirmCancel = false; model.cancelOrder() }) { Text(text(Copy.Continue, lang)) } }, dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(text(Copy.Close, lang)) } })
}

@Composable
fun ProfileScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    LazyColumn(Modifier.fillMaxSize().testTag("profile_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Icon(Icons.Outlined.Person, null, Modifier.padding(16.dp).size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    if (state.user?.emailVerified != true) Text(text(Copy.EmailUnverified, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF063C42), contentColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 232.dp)) {
                    Image(painterResource(Res.drawable.card_bg), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Color(0xFF063C42).copy(alpha = 0.82f)))
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text(Copy.Loyalty, lang), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            state.loyalty?.let { loyalty -> Text("${loyalty.discountPercent}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                        }
                        state.loyalty?.let { loyalty ->
                            Text(text(Copy.Receipts, lang) + ": ${loyalty.qualifyingOrders}", fontWeight = FontWeight.Medium)
                            loyalty.nextThreshold?.let { threshold ->
                                val progress = (loyalty.qualifyingOrders.toFloat() / threshold.coerceAtLeast(1)).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text(Copy.NextLevel, lang) + ": $threshold → ${loyalty.nextDiscountPercent}%", style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.24f))
                                    Text("${loyalty.qualifyingOrders} / $threshold", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }
                        Text(text(Copy.LoyaltyRules, lang), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                        OutlinedButton(onClick = model::refreshAccount, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.55f)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))) { Text(text(Copy.Refresh, lang)) }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(text(Copy.Language, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); LanguageRow(lang, model::language) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(Copy.Theme, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(Modifier.selectableGroup()) {
                        listOf("system" to Copy.System, "light" to Copy.Light, "dark" to Copy.Dark).forEach { (key, label) ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = state.theme == key, role = Role.RadioButton, onClick = { model.theme(key) }), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = state.theme == key, onClick = null); Text(text(label, lang)) }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = model::logout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("profile_logout"), shape = RoundedCornerShape(16.dp)) { Text(text(Copy.Logout, lang)) } }
        if (state.apiAddress.isNotEmpty()) item { SelectionContainer { Text(text(Copy.ApiAddress, lang) + ": " + state.apiAddress, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun OrderLineSummary(line: OrderLine, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${line.quantity} × " + catalogText(line.name, lang) + " · ${line.sizeLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        line.addonNames.forEach { Text(catalogText(it, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (line.isDrink) Text(lineOptions(line.sugar, line.orderType, line.temperature, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Text(text(if (line.orderType == "takeaway") Copy.Takeaway else Copy.DineIn, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${line.lineTotal} ₸", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AmountRow(label: String, amount: Int, highlighted: Boolean = false) {
    Row(
        modifier = if (highlighted) Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).padding(14.dp) else Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = if (highlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$amount ₸", style = if (highlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun lineOptions(sugar: Int, type: String, temperature: String, lang: String): String = text(Copy.Sugar, lang) + ": $sugar · " + text(if (type == "takeaway") Copy.Takeaway else Copy.DineIn, lang) + " · " + text(when(temperature) { "hot" -> Copy.Hot; "cool" -> Copy.Cool; else -> Copy.Standard }, lang)

@Composable
private fun statusText(status: String, lang: String): String = text(when(status) { "created" -> Copy.Created; "accepted" -> Copy.Accepted; "preparing" -> Copy.Preparing; "ready" -> Copy.Ready; "completed" -> Copy.Completed; else -> Copy.Cancelled }, lang)

private fun localTime(value: String): String = runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.date} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } }.getOrDefault(value)
