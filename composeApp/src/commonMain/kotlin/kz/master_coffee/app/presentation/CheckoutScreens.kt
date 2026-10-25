package kz.master_coffee.app.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun CartScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    val cart = state.cart
    LazyColumn(Modifier.fillMaxSize().testTag("cart_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(text(Copy.Cart, lang), style = MaterialTheme.typography.headlineSmall) }
        if (state.pending) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text(Copy.PendingHint, lang))
                    Button(onClick = model::confirmOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.RecoverOrder, lang)) }
                }
            }
        }
        if (cart == null || cart.items.isEmpty()) {
            item { Text(text(Copy.CartEmpty, lang)); TextButton(onClick = { model.navigate(Screen.MENU) }) { Text(text(Copy.Menu, lang)) } }
        } else {
            item { Text(state.catalog?.cafes?.firstOrNull { it.id == cart.cafeId }?.address ?: "#${cart.cafeId}", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(cart.items) { index, line ->
                val product = state.catalog?.products?.firstOrNull { it.id == line.productId }
                val size = product?.sizes?.firstOrNull { it.id == line.sizeId }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(product?.let { catalogText(it.name, lang) } ?: "#${line.productId}", style = MaterialTheme.typography.titleLarge)
                        Text(size?.label ?: line.sizeId)
                        line.addonIds.mapNotNull { id -> state.catalog?.addons?.firstOrNull { it.id == id } }.forEach { Text(catalogText(it.name, lang), style = MaterialTheme.typography.bodySmall) }
                        if (product?.isDrink != false) Text(lineOptions(line.sugar, line.orderType, line.temperature, lang), style = MaterialTheme.typography.bodySmall)
                        else Text(text(if (line.orderType == "takeaway") Copy.Takeaway else Copy.DineIn, lang), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { model.quantity(index, line.quantity - 1) }, enabled = !state.pending && !state.busy) { Icon(if (line.quantity == 1) Icons.Outlined.Delete else Icons.Outlined.Remove, text(Copy.Remove, lang)) }
                            Text("${line.quantity}", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { model.quantity(index, line.quantity + 1) }, enabled = !state.pending && !state.busy && line.quantity < 20) { Icon(Icons.Outlined.Add, text(Copy.Quantity, lang)) }
                            Spacer(Modifier.weight(1f))
                            if (size != null) {
                                val extra = line.addonIds.sumOf { id -> state.catalog?.addons?.firstOrNull { it.id == id }?.price ?: 0 }
                                Text("${(size.price + extra) * line.quantity} ₸", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { OutlinedTextField(cart.comment, model::comment, label = { Text(text(Copy.Comment, lang)) }, enabled = !state.pending && !state.busy, modifier = Modifier.fillMaxWidth(), maxLines = 4) }
            item { Text(text(Copy.Estimate, lang), style = MaterialTheme.typography.bodySmall); Text(text(Copy.PricingUnit, lang), style = MaterialTheme.typography.bodySmall) }
            if (!state.pending) item { Button(onClick = model::calculate, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("cart_calculate")) { Text(text(Copy.Calculate, lang)) } }
        }
        state.quote?.let { quote -> item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text(Copy.CartReview, lang), style = MaterialTheme.typography.titleLarge)
                    quote.items.forEach { OrderLineSummary(it, lang) }
                    HorizontalDivider()
                    AmountRow(text(Copy.Subtotal, lang), quote.subtotal)
                    AmountRow(text(Copy.Discount, lang) + " ${quote.discountPercent}%", -quote.discount)
                    AmountRow(text(Copy.Total, lang), quote.total)
                    Text(text(Copy.QuoteHint, lang), style = MaterialTheme.typography.bodyMedium)
                    Text(text(Copy.Expires, lang) + ": " + localTime(quote.expiresAt), style = MaterialTheme.typography.bodySmall)
                    if (!state.pending) Button(onClick = model::confirmOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("cart_confirm")) { Text(text(Copy.ConfirmOrder, lang)) }
                }
            }
        } }
    }
}

@Composable
fun OrdersScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    var confirmCancel by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("orders_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text(text(Copy.Orders, lang), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall); IconButton(onClick = model::refreshAccount, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, text(Copy.Refresh, lang)) } } }
        state.activeOrder?.let { order -> item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text(Copy.Order, lang) + " № ${order.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(statusText(order.status, lang), modifier = Modifier.testTag("order_status").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(text(if (order.paymentStatus == "paid") Copy.Paid else Copy.Unpaid, lang), style = MaterialTheme.typography.titleMedium)
                    if (order.status == "ready") Text(text(Copy.ReadyHint, lang))
                    Text(state.catalog?.cafes?.firstOrNull { it.id == order.cafeId }?.address.orEmpty())
                    Text(localTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
                    order.items.forEach { OrderLineSummary(it, lang) }
                    if (order.comment.isNotEmpty()) Text(order.comment)
                    AmountRow(text(Copy.Total, lang), order.total)
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
            Card(onClick = { model.openOrder(order) }, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("№ ${order.number} · ${order.total} ₸", style = MaterialTheme.typography.titleMedium)
                    Text(statusText(order.status, lang) + " · " + text(if (order.paymentStatus == "paid") Copy.Paid else Copy.Unpaid, lang))
                    Text(localTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
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
        item { Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(state.user?.email.orEmpty()); if (state.user?.emailVerified != true) Text(text(Copy.EmailUnverified, lang), style = MaterialTheme.typography.bodySmall) }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text(Copy.Loyalty, lang), style = MaterialTheme.typography.titleLarge)
                    state.loyalty?.let { loyalty ->
                        Text("${loyalty.discountPercent}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(text(Copy.Receipts, lang) + ": ${loyalty.qualifyingOrders}")
                        if (loyalty.nextThreshold != null) Text(text(Copy.NextLevel, lang) + ": ${loyalty.nextThreshold} → ${loyalty.nextDiscountPercent}%")
                    }
                    Text(text(Copy.LoyaltyRules, lang), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = model::refreshAccount, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.Refresh, lang)) }
                }
            }
        }
        item { Text(text(Copy.Language, lang), style = MaterialTheme.typography.titleMedium); LanguageRow(lang, model::language) }
        item {
            Text(text(Copy.Theme, lang), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                listOf("system" to Copy.System, "light" to Copy.Light, "dark" to Copy.Dark).forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = state.theme == key, role = Role.RadioButton, onClick = { model.theme(key) }), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = state.theme == key, onClick = null); Text(text(label, lang)) }
                }
            }
        }
        item { OutlinedButton(onClick = model::logout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("profile_logout")) { Text(text(Copy.Logout, lang)) } }
        if (state.apiAddress.isNotEmpty()) item { SelectionContainer { Text(text(Copy.ApiAddress, lang) + ": " + state.apiAddress, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun OrderLineSummary(line: OrderLine, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${line.quantity} × " + catalogText(line.name, lang) + " · ${line.sizeLabel}", style = MaterialTheme.typography.titleMedium)
        line.addonNames.forEach { Text(catalogText(it, lang), style = MaterialTheme.typography.bodySmall) }
        if (line.isDrink) Text(lineOptions(line.sugar, line.orderType, line.temperature, lang), style = MaterialTheme.typography.bodySmall)
        else Text(text(if (line.orderType == "takeaway") Copy.Takeaway else Copy.DineIn, lang), style = MaterialTheme.typography.bodySmall)
        Text("${line.lineTotal} ₸")
    }
}

@Composable
private fun AmountRow(label: String, amount: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Text("$amount ₸", fontWeight = FontWeight.Bold) } }

@Composable
private fun lineOptions(sugar: Int, type: String, temperature: String, lang: String): String = text(Copy.Sugar, lang) + ": $sugar · " + text(if (type == "takeaway") Copy.Takeaway else Copy.DineIn, lang) + " · " + text(when(temperature) { "hot" -> Copy.Hot; "cool" -> Copy.Cool; else -> Copy.Standard }, lang)

@Composable
private fun statusText(status: String, lang: String): String = text(when(status) { "created" -> Copy.Created; "accepted" -> Copy.Accepted; "preparing" -> Copy.Preparing; "ready" -> Copy.Ready; "completed" -> Copy.Completed; else -> Copy.Cancelled }, lang)

private fun localTime(value: String): String = runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.date} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } }.getOrDefault(value)
