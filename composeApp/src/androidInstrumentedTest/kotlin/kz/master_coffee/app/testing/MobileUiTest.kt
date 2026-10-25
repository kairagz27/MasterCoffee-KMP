package kz.master_coffee.app.testing

import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kz.master_coffee.app.MasterCoffeeTheme
import kz.master_coffee.app.data.*
import kz.master_coffee.app.domain.*
import kz.master_coffee.app.presentation.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileUiTest {
    @get:Rule val rule = createAndroidComposeRule<MobileTestActivity>()
    private lateinit var model: CoffeeViewModel
    private lateinit var repository: ManagedRepository
    private lateinit var tokens: ManagedTokens
    private lateinit var local: ManagedLocal

    @Before fun prepare() {
        repository = ManagedRepository(); tokens = ManagedTokens(); local = ManagedLocal()
        rule.runOnUiThread {
            model = CoffeeViewModel(repository, tokens, local)
            MobileTestContent.content = { val state by model.state.collectAsState(); MasterCoffeeTheme(false) { CoffeeApp(state, model) } }
        }
        ready()
    }
    @After fun close() { InstrumentationRegistry.getInstrumentation().runOnMainSync { MobileTestContent.content = null; if (::model.isInitialized) model.close() } }
    private fun ready() = rule.waitUntil(15_000) { !model.state.value.restoring && !model.state.value.busy }
    private fun login(email: String = "a@coffee.test") {
        rule.onNodeWithTag("auth_email").performTextInput(email)
        rule.onNodeWithTag("auth_password").performTextInput("managed-password-123")
        rule.onNodeWithTag("auth_submit").performScrollTo().performClick()
        rule.waitUntil(15_000) { model.state.value.user != null && !model.state.value.busy }
    }
    private fun cafeAndDrink() {
        rule.onNodeWithTag("tab_cafes").performClick()
        rule.onNodeWithTag("cafe_list").performScrollToNode(hasTestTag("cafe_1"))
        rule.onNodeWithTag("cafe_1").performClick()
        rule.waitUntil(15_000) { model.state.value.cafeId == 1 && !model.state.value.busy && !model.state.value.catalogLoading }
        rule.onNodeWithTag("menu_list").performScrollToNode(hasTestTag("product_3"))
        rule.onNodeWithTag("product_3").performClick()
    }
    private fun cartAction(tag: String) { rule.onNodeWithTag("cart_list").performScrollToNode(hasTestTag(tag)); rule.onNodeWithTag(tag).performClick() }

    @Test fun customerJourneyCustomizationRotationBackStatusAndAccountIsolation() {
        login(); cafeAndDrink()
        rule.onNodeWithTag("size_3-450").performScrollTo().performClick()
        rule.onNodeWithTag("sugar").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        rule.onNodeWithText("Cherry").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("size_3-450").assertIsSelected()
        rule.onNodeWithTag("product_add").performClick()
        rule.waitUntil(15_000) { model.state.value.cart != null && !model.state.value.busy }
        assertEquals("3-450", model.state.value.cart!!.items.single().sizeId)
        assertEquals(2, model.state.value.cart!!.items.single().sugar)
        assertEquals(listOf("cherry"), model.state.value.cart!!.items.single().addonIds)
        cartAction("cart_calculate")
        rule.waitUntil(15_000) { model.state.value.quote != null && !model.state.value.busy }
        assertEquals(1455, model.state.value.quote!!.total)
        cartAction("cart_confirm")
        rule.waitUntil(15_000) { model.state.value.activeOrder != null && !model.state.value.busy }
        assertNull(model.state.value.cart)
        assertEquals(1, repository.createCalls)
        rule.onNodeWithTag("order_status").assertTextEquals("Created")
        rule.runOnUiThread { repository.current = repository.current.copy(status = "ready", paymentStatus = "paid"); model.refreshOrder() }
        rule.waitUntil(15_000) { model.state.value.activeOrder?.status == "ready" && !model.state.value.busy }
        rule.onNodeWithTag("order_status").assertTextEquals("Ready for pickup")
        rule.runOnUiThread { repository.current = repository.current.copy(status = "completed"); repository.receipts = 1; model.refreshOrder() }
        rule.waitUntil(15_000) { model.state.value.loyalty?.qualifyingOrders == 1 && !model.state.value.busy }
        rule.onNodeWithTag("tab_profile").performClick(); ready()
        rule.onNodeWithText("Completed drink orders: 1").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil { model.state.value.screen == Screen.MENU }
        rule.onNodeWithTag("tab_profile").performClick(); ready()
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("profile_logout"))
        rule.onNodeWithTag("profile_logout").performClick()
        rule.waitUntil(15_000) { model.state.value.user == null && !model.state.value.busy }
        assertNull(tokens.value)
        login("b@coffee.test")
        assertNull(model.state.value.cart)
        assertTrue(model.state.value.orders.isEmpty())
    }

    @Test fun networkErrorKeepsCartAndRetryUsesActualUi() {
        login(); cafeAndDrink(); rule.onNodeWithTag("product_add").performClick(); ready()
        rule.runOnUiThread { repository.quoteFailure = true }
        cartAction("cart_calculate"); ready()
        rule.onNodeWithTag("error").assertExists()
        assertNotNull(model.state.value.cart)
        assertNull(model.state.value.activeOrder)
        rule.runOnUiThread { repository.quoteFailure = false }
        cartAction("cart_calculate")
        rule.waitUntil(15_000) { model.state.value.quote != null && !model.state.value.busy }
        rule.onNodeWithTag("cart_list").performScrollToNode(hasTestTag("cart_confirm"))
        rule.onNodeWithTag("cart_confirm").assertIsEnabled()
        assertEquals(0, repository.createCalls)
    }
}

internal fun backgroundOnly() { check(Looper.myLooper() != Looper.getMainLooper()) { "Storage accessed on the Android main thread" } }
internal class ManagedTokens : SecureTokenStore {
    var value: String? = null
    override fun read(): String? { backgroundOnly(); return value }
    override fun write(token: String) { backgroundOnly(); value = token }
    override fun clear() { backgroundOnly(); value = null }
}
internal class ManagedLocal : LocalStore {
    private var lang = "en"
    private var appearance = "light"
    override var language: String
        get() { backgroundOnly(); return lang }
        set(value) { backgroundOnly(); lang = value }
    override var theme: String
        get() { backgroundOnly(); return appearance }
        set(value) { backgroundOnly(); appearance = value }
    private val catalogs = mutableMapOf<Int?, Catalog>()
    private val carts = mutableMapOf<String, Cart>()
    private val recoveries = mutableMapOf<String, Pair<String, String>>()
    private val navigation = mutableMapOf<String, SavedNavigation>()
    override fun catalog(cafeId: Int?): Catalog? { backgroundOnly(); return catalogs[cafeId] }
    override fun saveCatalog(cafeId: Int?, catalog: Catalog) { backgroundOnly(); catalogs[cafeId] = catalog }
    override fun cart(userId: String): Cart? { backgroundOnly(); return carts[userId] }
    override fun saveCart(userId: String, cart: Cart?) { backgroundOnly(); if (cart == null) carts.remove(userId) else carts[userId] = cart }
    override fun pending(userId: String): Pair<String, String>? { backgroundOnly(); return recoveries[userId] }
    override fun savePending(userId: String, quoteId: String, key: String) { backgroundOnly(); recoveries[userId] = quoteId to key }
    override fun clearPending(userId: String) { backgroundOnly(); recoveries.remove(userId) }
    override fun clearAccount(userId: String) { backgroundOnly(); carts.remove(userId); recoveries.remove(userId); navigation.remove(userId) }
    override fun navigation(userId: String): SavedNavigation? { backgroundOnly(); return navigation[userId] }
    override fun saveNavigation(userId: String, navigation: SavedNavigation) { backgroundOnly(); this.navigation[userId] = navigation }
}
internal class ManagedRepository : CoffeeRepository {
    var quoteFailure = false
    var createCalls = 0
    var receipts = 0
    private var user = User("customer-a", "a@coffee.test", "Android Test", "customer", false)
    private val line = OrderLine("Cappuccino", "450 ml", 1, listOf("Cherry"), 2, "takeaway", "standard", 1500, 1500)
    var current = Order("managed-order", "MC-UI-001", 1, "created", "unpaid", 1455, 45, 3, listOf(line), "", "2026-10-25T10:00:00Z", OrderQr("managed-qr", "mastercoffee:order:managed-qr", "2099-10-25T10:30:00Z", false))
    private val catalog = Catalog(listOf(Cafe(1, "Master Coffee", "Astana test cafe", "08–22", "", "megasilkway", true)), listOf(Product(3, "Cappuccino", "Managed catalogue", "Coffee", "cappuccino", true, listOf(Size("3-350", "350 ml", 1290), Size("3-450", "450 ml", 1440)))), listOf(Addon("cherry", "Cherry", "essence", 60)), emptyList())
    override suspend fun login(email: String, password: String): Session { user = user.copy(id = if (email.startsWith("b")) "customer-b" else "customer-a", email = email); return Session("managed-${user.id}", user) }
    override suspend fun register(email: String, password: String, name: String) = login(email, password)
    override suspend fun me(token: String) = user
    override suspend fun logout(token: String) {}
    override suspend fun catalog(cafeId: Int?) = catalog
    override suspend fun quote(token: String, cart: Cart): Quote { if (quoteFailure) throw CoffeeFailure(FailureKind.NETWORK); return Quote("managed-quote", "2099-10-25T10:05:00Z", listOf(line), 1500, 3, 45, 1455) }
    override suspend fun createOrder(token: String, quoteId: String, idempotencyKey: String): Order { createCalls++; return current }
    override suspend fun orders(token: String, before: Int?) = OrderPage(if (createCalls > 0 && user.id == "customer-a") listOf(current) else emptyList(), null)
    override suspend fun order(token: String, id: String) = current
    override suspend fun cancel(token: String, id: String) = current.copy(status = "cancelled").also { current = it }
    override suspend fun refreshQr(token: String, id: String) = current
    override suspend fun loyalty(token: String) = Loyalty(if (user.id == "customer-a") receipts else 0, 3, 50, 5)
    override fun close() {}
}
