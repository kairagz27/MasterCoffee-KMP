package kz.master_coffee.app.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.master_coffee.app.domain.*
import mastercoffee.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun CoffeeApp(state: CoffeeState, model: CoffeeViewModel) {
    val lang = state.language
    var product by remember { mutableStateOf<Product?>(null) }
    var changeCafe by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.user?.id) { product = null; changeCafe = null }
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(Res.drawable.master_logo2), null, Modifier.size(44.dp))
                    Text("MASTER COFFEE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    IconButton(onClick = { if (state.user != null) model.refreshAccount(); model.refreshCatalog() }, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, text(Copy.Refresh, lang)) }
                }
            }
        },
        bottomBar = {
            if (state.user != null) NavigationBar {
                val tabs = listOf(Screen.MENU to Copy.Menu, Screen.CAFES to Copy.Cafes, Screen.CART to Copy.Cart, Screen.ORDERS to Copy.Orders, Screen.PROFILE to Copy.Profile)
                tabs.forEach { (screen, label) ->
                    NavigationBarItem(selected = state.screen == screen, onClick = { model.navigate(screen) }, icon = {
                        val icon = when(screen) { Screen.MENU -> Icons.Outlined.LocalCafe; Screen.CAFES -> Icons.Outlined.Storefront; Screen.CART -> Icons.Outlined.ShoppingBag; Screen.ORDERS -> Icons.Outlined.ReceiptLong; Screen.PROFILE -> Icons.Outlined.Person }
                        BadgedBox(badge = { if (screen == Screen.CART && state.cart != null) Badge { Text(state.cart.items.sumOf { it.quantity }.toString()) } }) { Icon(icon, null) }
                    }, label = { Text(text(label, lang), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it, lang, model::dismissError) }
            when {
                state.restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.user == null -> AuthScreen(state, model)
                else -> key(state.user.id) {
                    when (state.screen) {
                        Screen.MENU -> MenuScreen(state, model) { product = it }
                        Screen.CAFES -> CafeScreen(state) { id -> if (state.cart?.items?.isNotEmpty() == true && state.cafeId != id) changeCafe = id else model.selectCafe(id) }
                        Screen.CART -> CartScreen(state, model)
                        Screen.ORDERS -> OrdersScreen(state, model)
                        Screen.PROFILE -> ProfileScreen(state, model)
                    }
                }
            }
        }
    }
    product?.let { selected -> ProductDialog(selected, state) { line -> if (line != null) model.add(line); product = null } }
    changeCafe?.let { id -> AlertDialog(onDismissRequest = { changeCafe = null }, title = { Text(text(Copy.SelectCafe, lang)) }, text = { Text(text(Copy.CafeChange, lang)) }, confirmButton = { TextButton(onClick = { model.selectCafe(id); changeCafe = null }) { Text(text(Copy.Continue, lang)) } }, dismissButton = { TextButton(onClick = { changeCafe = null }) { Text(text(Copy.Cancel, lang)) } }) }
}

@Composable
private fun ErrorBanner(error: CoffeeFailure, lang: String, dismiss: () -> Unit) {
    val copy = when (error.code) {
        "auth_validation" -> Copy.AuthValidation
        "invalid_credentials" -> Copy.LoginFailed
        "email_exists", "email_taken" -> Copy.EmailExists
        else -> when(error.kind) { FailureKind.NETWORK -> Copy.NetworkError; FailureKind.TIMEOUT -> Copy.TimeoutError; FailureKind.UNAUTHORIZED -> Copy.SessionExpired; FailureKind.PRICE_CHANGED -> Copy.PriceChanged; FailureKind.INVALID -> Copy.InvalidError; FailureKind.SERVER -> Copy.ServerError }
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(Res.drawable.master_logo2), "Master Coffee", Modifier.size(110.dp).align(Alignment.CenterHorizontally))
        Text(text(Copy.Welcome, lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LanguageRow(lang, model::language)
        if (register) OutlinedTextField(name, { name = it }, label = { Text(text(Copy.Name, lang)) }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text(text(Copy.Email, lang)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text(text(Copy.Password, lang)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, text(if (visible) Copy.PasswordHide else Copy.PasswordShow, lang)) } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        Text(text(Copy.AuthHint, lang), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { model.authenticate(email, password, name, register) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(text(if (register) Copy.Register else Copy.Login, lang)) }
        TextButton(onClick = { register = !register; password = ""; model.dismissError() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(if (register) Copy.SwitchLogin else Copy.SwitchRegister, lang)) }
        TextButton(onClick = model::restoreSession, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(text(Copy.SessionRestore, lang)) }
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text(Copy.Welcome, lang), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(state.user?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { model.navigate(Screen.CAFES) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) { Text(cafe?.address ?: text(Copy.SelectCafe, lang)) }
                    }
                }
            }
            if (state.cached) item { Text(text(Copy.Offline, lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item { Text(text(Copy.AvailabilityHint, lang), style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(search, { search = it }, label = { Text(text(Copy.Search, lang)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            if (catalog != null) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(text(Copy.Menu, lang)) }) }
                    items(catalog.products.map { it.category }.distinct()) { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(categoryText(value, lang)) }) }
                }
            }
            if (catalog == null) item { RetryEmpty(lang, model::refreshCatalog) }
            else if (products.isEmpty()) item { Text(text(Copy.Empty, lang)) }
            items(products.chunked(columns), key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { product -> ProductCard(product, lang, state.cafeId != null && !state.pending && !state.busy, Modifier.weight(1f)) { select(product) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
            if (catalog?.stories?.isNotEmpty() == true) {
                item { Text(text(Copy.Stories, lang), style = MaterialTheme.typography.titleLarge) }
                items(catalog.stories, key = { it.id }) { story -> StoryCard(story, lang) }
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
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(coffeeImage(product.imageKey)), null, Modifier.fillMaxWidth().height(142.dp), contentScale = ContentScale.Fit)
            Text(catalogText(product.name, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(product.sizes.minOfOrNull { it.price }?.let { "$it ₸" }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = select, enabled = enabled && product.available && product.sizes.isNotEmpty(), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), shape = RoundedCornerShape(14.dp)) { Text(text(if (product.available) Copy.AddToCart else Copy.Unavailable, lang), maxLines = 2) }
        }
    }
}

@Composable
private fun CafeScreen(state: CoffeeState, select: (Int) -> Unit) {
    val lang = state.language
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(text(Copy.SelectCafe, lang), style = MaterialTheme.typography.headlineSmall) }
        items(state.catalog?.cafes.orEmpty(), key = { it.id }) { cafe ->
            Card(shape = RoundedCornerShape(22.dp)) {
                Image(painterResource(coffeeImage(cafe.imageKey)), null, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(cafe.name, style = MaterialTheme.typography.titleLarge)
                    Text(cafe.address)
                    Text(text(Copy.WorkingHours, lang) + ": " + cafe.hours, style = MaterialTheme.typography.bodySmall)
                    if (cafe.phone.isNotEmpty()) Text(text(Copy.Contact, lang) + ": " + cafe.phone, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { select(cafe.id) }, enabled = cafe.active && !state.busy && !state.pending, modifier = Modifier.fillMaxWidth()) { Text(text(if (cafe.active) Copy.Continue else Copy.Unavailable, lang)) }
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
    Card(shape = RoundedCornerShape(18.dp)) {
        if (story.imageUrl.startsWith("resource:")) Image(painterResource(coffeeImage(story.imageUrl.removePrefix("resource:"))), null, Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
        else coil3.compose.AsyncImage(model = story.imageUrl, contentDescription = story.title, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
        Text(catalogText(story.title, lang), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}
