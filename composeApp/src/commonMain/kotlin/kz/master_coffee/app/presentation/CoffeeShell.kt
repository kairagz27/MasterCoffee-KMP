package kz.master_coffee.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kz.master_coffee.app.PlatformBackHandler
import kotlinx.coroutines.launch

private data class CoffeeDestination(val screen: Screen, val label: Copy, val icon: ImageVector)

private val destinations = listOf(
    CoffeeDestination(Screen.MENU, Copy.Menu, Icons.Outlined.LocalCafe),
    CoffeeDestination(Screen.CAFES, Copy.Cafes, Icons.Outlined.Storefront),
    CoffeeDestination(Screen.CART, Copy.Cart, Icons.Outlined.ShoppingBag),
    CoffeeDestination(Screen.ORDERS, Copy.Orders, Icons.Outlined.ReceiptLong),
    CoffeeDestination(Screen.PROFILE, Copy.Profile, Icons.Outlined.Person)
)

/** The original drawer and floating bar share the same account-scoped navigation state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeShell(
    state: CoffeeState,
    model: CoffeeViewModel,
    screenBackEnabled: Boolean,
    content: @Composable (PaddingValues) -> Unit
) {
    val lang = state.language
    val drawer = key(state.user?.id) { rememberDrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    var settingsSheet by rememberSaveable(state.user?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.user?.id) { drawer.close() }
    PlatformBackHandler(
        state.user != null && settingsSheet == null &&
            (drawer.isOpen || (screenBackEnabled && state.screen != Screen.MENU))
    ) {
        if (drawer.isOpen) scope.launch { drawer.close() } else model.back()
    }
    val scaffold: @Composable () -> Unit = {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.user != null) {
                            if (state.screen != Screen.MENU) {
                                IconButton(onClick = model::back, modifier = Modifier.testTag("back")) {
                                    Icon(Icons.Outlined.ArrowBack, text(Copy.Back, lang))
                                }
                            }
                            IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.testTag("drawer_open")) {
                                Icon(Icons.Outlined.Menu, text(Copy.Menu, lang))
                            }
                        } else Spacer(Modifier.width(48.dp))
                        Box(Modifier.weight(1f).height(64.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            BrandLogo(Modifier.fillMaxWidth().heightIn(max = 56.dp), "Master Coffee")
                        }
                        IconButton(
                            onClick = { if (state.user != null) model.refreshAccount(); model.refreshCatalog() },
                            enabled = !state.busy
                        ) { Icon(Icons.Outlined.Refresh, text(Copy.Refresh, lang)) }
                    }
                }
            },
            bottomBar = { if (state.user != null) FloatingCoffeeNavigation(state, model::navigate) },
            content = content
        )
    }
    if (state.user != null) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                ) {
                    Column(
                        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .1f), CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.user.name.firstOrNull()?.uppercase().orEmpty(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(state.user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(state.user.email, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        destinations.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(text(destination.label, lang)) },
                                icon = { Icon(destination.icon, null) },
                                selected = state.screen == destination.screen,
                                onClick = {
                                    model.navigate(destination.screen)
                                    scope.launch { drawer.close() }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("drawer_${destination.screen.name.lowercase()}")
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        DrawerAction(Icons.Outlined.Language, text(Copy.Language, lang), "drawer_language") {
                            scope.launch { drawer.close(); settingsSheet = "language" }
                        }
                        DrawerAction(Icons.Outlined.Palette, text(Copy.Theme, lang), "drawer_theme") {
                            scope.launch { drawer.close(); settingsSheet = "theme" }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        DrawerAction(Icons.Outlined.Logout, text(Copy.Logout, lang), "drawer_logout") {
                            settingsSheet = null
                            scope.launch { drawer.close() }
                            model.logout()
                        }
                    }
                }
            },
            content = scaffold
        )
    } else scaffold()
    settingsSheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = { settingsSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("${sheet}_sheet")
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text(if (sheet == "language") Copy.Language else Copy.Theme, lang), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { settingsSheet = null }, modifier = Modifier.size(48.dp).testTag("sheet_close")) {
                        Icon(Icons.Outlined.Close, text(Copy.Close, lang))
                    }
                }
                Column(Modifier.selectableGroup()) {
                    if (sheet == "language") {
                        listOf("ru" to "Русский", "kk" to "Қазақша", "en" to "English").forEach { (key, label) ->
                            SettingsChoice(label, state.language == key, "language_$key") { model.language(key) }
                        }
                    } else {
                        listOf("system" to Copy.System, "light" to Copy.Light, "dark" to Copy.Dark).forEach { (key, label) ->
                            SettingsChoice(text(label, lang), state.theme == key, "theme_$key") { model.theme(key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, tag: String, action: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = action,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, tag: String, select: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = select)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FloatingCoffeeNavigation(state: CoffeeState, navigate: (Screen) -> Unit) {
    val cartQuantity = state.cart?.items?.sumOf { it.quantity } ?: 0
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0.dp)) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${destination.screen.name.lowercase()}"),
                        selected = state.screen == destination.screen,
                        onClick = { navigate(destination.screen) },
                        icon = {
                            BadgedBox(badge = {
                                if (destination.screen == Screen.CART && cartQuantity > 0) {
                                    Badge { Text(cartQuantity.toString()) }
                                }
                            }) { Icon(destination.icon, null) }
                        },
                        label = { Text(text(destination.label, state.language), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                        )
                    )
                }
            }
        }
    }
}
