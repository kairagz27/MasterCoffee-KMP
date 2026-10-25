package kz.master_coffee.app.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.master_coffee.app.domain.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics

@Composable
fun CoffeeApp(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    var productId by rememberSaveable(state.user?.id) { mutableStateOf<Int?>(null) }
    var changeCafe by rememberSaveable(state.user?.id) { mutableStateOf<Int?>(null) }
    CoffeeShell(state, model, screenBackEnabled = productId == null && changeCafe == null) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it, lang, model::dismissError) }
            when {
                state.restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.user == null -> AuthScreen(state, model)
                else -> key(state.user.id) {
                    when (state.screen) {
                        Screen.MENU -> MenuScreen(state, model) { productId = it.id }
                        Screen.CAFES -> CafeScreen(state) { id -> if (state.cart?.items?.isNotEmpty() == true && state.cafeId != id) changeCafe = id else model.selectCafe(id) }
                        Screen.CART -> CartScreen(state, model)
                        Screen.ORDERS -> OrdersScreen(state, model)
                        Screen.PROFILE -> ProfileScreen(state, model)
                    }
                }
            }
        }
    }
    state.catalog?.products?.firstOrNull { it.id == productId }?.let { selected -> ProductDialog(selected, state) { line -> if (line != null) model.add(line); productId = null } }
    changeCafe?.let { id -> AlertDialog(onDismissRequest = { changeCafe = null }, title = { Text(text(Copy.SelectCafe, lang)) }, text = { Text(text(Copy.CafeChange, lang)) }, confirmButton = { TextButton(onClick = { model.selectCafe(id); changeCafe = null }) { Text(text(Copy.Continue, lang)) } }, dismissButton = { TextButton(onClick = { changeCafe = null }) { Text(text(Copy.Cancel, lang)) } }) }
}

@Composable
private fun ErrorBanner(error: CoffeeFailure, lang: String, dismiss: () -> Unit) {
    val copy = when (error.code) {
        "auth_validation" -> Copy.AuthValidation
        "invalid_credentials" -> Copy.LoginFailed
        "email_exists", "email_taken", "account_exists" -> Copy.EmailExists
        "customer_required" -> Copy.CustomerRequired
        else -> when(error.kind) { FailureKind.NETWORK -> Copy.NetworkError; FailureKind.TIMEOUT -> Copy.TimeoutError; FailureKind.UNAUTHORIZED -> Copy.SessionExpired; FailureKind.PRICE_CHANGED -> Copy.PriceChanged; FailureKind.INVALID -> Copy.InvalidError; FailureKind.SERVER -> Copy.ServerError; FailureKind.STORAGE -> Copy.StorageError }
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.testTag("error").semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text(copy, lang), Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, text(Copy.Close, lang)) }
        }
    }
}

@Composable
private fun AuthScreen(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BrandMark(Modifier.size(96.dp).align(Alignment.CenterHorizontally), "Master Coffee")
        Text(text(Copy.Welcome, lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LanguageRow(lang, model::language)
                if (register) OutlinedTextField(name, { name = it }, label = { Text(text(Copy.Name, lang)) }, singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("auth_name"))
                OutlinedTextField(email, { email = it }, label = { Text(text(Copy.Email, lang)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("auth_email"))
                OutlinedTextField(password, { password = it }, label = { Text(text(Copy.Password, lang)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, text(if (visible) Copy.PasswordHide else Copy.PasswordShow, lang)) } }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("auth_password"))
                Text(text(Copy.AuthHint, lang), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { model.authenticate(email, password, name, register) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth_submit"), shape = RoundedCornerShape(16.dp)) { Text(text(if (register) Copy.Register else Copy.Login, lang)) }
                TextButton(onClick = { register = !register; password = ""; model.dismissError() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("auth_switch")) { Text(text(if (register) Copy.SwitchLogin else Copy.SwitchRegister, lang)) }
                TextButton(onClick = model::restoreSession, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.SessionRestore, lang)) }
            }
        }
    }
}

@Composable
fun LanguageRow(language: String, select: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ru" to "RU", "kk" to "ҚАЗ", "en" to "EN").forEach { (key, label) -> FilterChip(selected = language == key, onClick = { select(key) }, label = { Text(label) }) }
    }
}

@Composable
private fun MenuScreen(state: CoffeeState, model: CoffeeViewModel, select: (Product) -> Unit) {
    val lang = state.language
    var category by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val catalog = state.catalog
    val cafe = catalog?.cafes?.firstOrNull { it.id == state.cafeId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 350.dp || LocalDensity.current.fontScale > 1.25f) 1 else 2
        val products = catalog?.products.orEmpty().filter { (category == null || category == it.category) && (search.isBlank() || it.name.contains(search, true)) }
        LazyColumn(Modifier.fillMaxSize().testTag("menu_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(26.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text(Copy.Welcome, lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        state.loyalty?.let { loyalty ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .2f))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text(Copy.Loyalty, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(text(Copy.Receipts, lang) + ": " + loyalty.qualifyingOrders, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${loyalty.discountPercent}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = { model.navigate(Screen.CAFES) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text(Copy.SelectCafe, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(cafe?.address ?: text(Copy.Cafes, lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (catalog?.stories?.isNotEmpty() == true) {
                item { Text(text(Copy.Stories, lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(catalog.stories, key = { it.id }) { story -> StoryCard(story, lang) }
                    }
                }
            }
            if (state.cached) item { Text(text(Copy.Offline, lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item { Text(text(Copy.AvailabilityHint, lang), style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(search, { search = it }, label = { Text(text(Copy.Search, lang)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), singleLine = true) }
            if (catalog != null) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(text(Copy.Menu, lang)) }) }
                    items(catalog.products.map { it.category }.distinct()) { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(categoryText(value, lang)) }) }
                }
            }
            if (catalog == null) item { if (state.catalogLoading) CircularProgressIndicator() else RetryEmpty(lang, model::refreshCatalog) }
            else if (products.isEmpty()) item { Text(text(Copy.Empty, lang)) }
            items(products.chunked(columns), key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { product -> ProductCard(product, lang, state.cafeId != null && !state.pending && !state.busy, Modifier.weight(1f)) { select(product) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun categoryText(value: String, language: String) = when(value.lowercase()) {
    "coffee", "кофе" -> text(Copy.Coffee, language)
    "signature", "авторские" -> text(Copy.Signature, language)
    "tea", "чаи" -> text(Copy.Tea, language)
    "cold", "холодные" -> text(Copy.Cold, language)
    else -> catalogText(value, language)
}

@Composable
private fun ProductCard(product: Product, lang: String, enabled: Boolean, modifier: Modifier, select: () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductImage(product.imageKey, null, Modifier.fillMaxWidth().height(142.dp))
            Text(catalogText(product.name, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(product.sizes.minOfOrNull { it.price }?.let { "$it ₸" }.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Button(onClick = select, enabled = enabled && product.available && product.sizes.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("product_${product.id}"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), shape = RoundedCornerShape(14.dp)) { Text(text(if (product.available) Copy.AddToCart else Copy.Unavailable, lang), maxLines = 2) }
        }
    }
}

@Composable
private fun CafeScreen(state: CoffeeState, select: (Int) -> Unit) {
    val lang = state.language
    LazyColumn(Modifier.fillMaxSize().testTag("cafe_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(text(Copy.SelectCafe, lang), style = MaterialTheme.typography.headlineSmall) }
        items(state.catalog?.cafes.orEmpty(), key = { it.id }) { cafe ->
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                ProductImage(cafe.imageKey, null, Modifier.fillMaxWidth().height(170.dp), ContentScale.Crop)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(cafe.name, style = MaterialTheme.typography.titleLarge)
                    Text(cafe.address)
                    Text(text(Copy.WorkingHours, lang) + ": " + cafe.hours, style = MaterialTheme.typography.bodySmall)
                    if (cafe.phone.isNotEmpty()) Text(text(Copy.Contact, lang) + ": " + cafe.phone, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { select(cafe.id) }, enabled = cafe.active && !state.busy && !state.pending, modifier = Modifier.fillMaxWidth().testTag("cafe_${cafe.id}")) { Text(text(if (cafe.active) Copy.Continue else Copy.Unavailable, lang)) }
                }
            }
        }
        if (state.catalog?.cafes.isNullOrEmpty()) item { Text(text(Copy.Empty, lang)) }
    }
}

@Composable
private fun RetryEmpty(lang: String, retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(text(Copy.Empty, lang)); Button(onClick = retry) { Text(text(Copy.Retry, lang)) } }
}

@Composable
private fun StoryCard(story: Story, lang: String) {
    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.width(90.dp).height(110.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            ProductImage(story.imageUrl.removePrefix("resource:"), null, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Text(catalogText(story.title, lang), style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
