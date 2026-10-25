@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package kz.master_coffee.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kz.master_coffee.app.data.*
import kz.master_coffee.app.domain.*
import kz.master_coffee.app.presentation.CoffeeViewModel
import kotlin.test.*

class CoffeeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<CoffeeViewModel>()
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { models.forEach { it.close() }; Dispatchers.resetMain() }
    private fun model(repo: FakeRepository = FakeRepository(), tokens: FakeTokens = FakeTokens(), local: MemoryLocal = MemoryLocal()) = CoffeeViewModel(repo, tokens, local, { "durable-key-123" }, storageDispatcher = dispatcher).also { models += it }

    @Test fun initializationIsDeferredAndStorageFailureDoesNotCrashCart() = runTest(dispatcher) {
        val local = MemoryLocal()
        var loaded = false
        val vm = CoffeeViewModel(environment = { loaded = true; kz.master_coffee.app.presentation.CoffeeEnvironment(FakeRepository(), FakeTokens("token-a"), local) }, storageDispatcher = dispatcher).also { models += it }
        assertFalse(loaded)
        advanceUntilIdle()
        assertTrue(loaded)
        vm.selectCafe(1); advanceUntilIdle()
        local.failCartSaves = true
        vm.add(CartLine(3, "3-350")); advanceUntilIdle()
        assertEquals(FailureKind.STORAGE, vm.state.value.error?.kind)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.cart)
        local.failCartSaves = false
        vm.add(CartLine(3, "3-350")); advanceUntilIdle()
        assertNotNull(vm.state.value.cart)
    }

    @Test fun staffSignInNeverPersistsTokenAndRestoredStaffSessionIsErased() = runTest(dispatcher) {
        val repo = FakeRepository().apply { user = userA.copy(role = "staff") }
        val tokens = FakeTokens()
        val vm = model(repo, tokens)
        advanceUntilIdle()
        vm.authenticate("staff@coffee.test", "long-password-123", null, false); advanceUntilIdle()
        assertNull(vm.state.value.user)
        assertNull(tokens.value)
        assertEquals(0, tokens.writeCalls)
        assertEquals("customer_required", vm.state.value.error?.code)
        assertEquals(1, repo.logoutCalls)
        val stored = FakeTokens("old-staff-token")
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val restored = model(repo, stored, local); advanceUntilIdle()
        assertNull(restored.state.value.user)
        assertNull(stored.value)
        assertNull(local.cart("user-a"))
        assertEquals("customer_required", restored.state.value.error?.code)
    }

    @Test fun doubleLoginTapAndLateAuthenticationAfterLogoutCannotRestoreAccount() = runTest(dispatcher) {
        val repo = FakeRepository().apply { loginDelay = 5_000; ignoreLoginCancellation = true }
        val tokens = FakeTokens()
        val vm = model(repo, tokens)
        advanceUntilIdle()
        vm.authenticate("a@coffee.test", "long-password-123", null, false)
        vm.authenticate("a@coffee.test", "long-password-123", null, false)
        runCurrent()
        assertEquals(1, repo.loginCalls)
        vm.logout()
        advanceUntilIdle()
        assertNull(vm.state.value.user)
        assertNull(tokens.value)
        assertFalse(vm.state.value.busy)
    }

    @Test fun navigationAndCartSurviveNewViewModelAndBackReturnsToMenu() = runTest(dispatcher) {
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val tokens = FakeTokens("token-a")
        val first = model(tokens = tokens, local = local)
        advanceUntilIdle()
        first.navigate(kz.master_coffee.app.presentation.Screen.PROFILE); advanceUntilIdle()
        first.close()
        val restored = model(tokens = tokens, local = local)
        advanceUntilIdle()
        assertEquals(kz.master_coffee.app.presentation.Screen.PROFILE, restored.state.value.screen)
        assertEquals(1, restored.state.value.cafeId)
        assertNotNull(restored.state.value.cart)
        assertTrue(restored.back())
        assertEquals(kz.master_coffee.app.presentation.Screen.MENU, restored.state.value.screen)
        assertFalse(restored.back())
    }

    @Test fun processRestorationFetchesSelectedOrderBeyondTheFirstHistoryPage() = runTest(dispatcher) {
        val older = order.copy(id = "older-order", status = "completed")
        val repo = FakeRepository().apply { history = listOf(order); fetchedOrder = older }
        val local = MemoryLocal().apply { saveNavigation("user-a", SavedNavigation("ORDERS", 1, older.id)) }
        val restored = model(repo, FakeTokens("token-a"), local)
        advanceUntilIdle()
        assertEquals(older.id, restored.state.value.activeOrder?.id)
        assertEquals(kz.master_coffee.app.presentation.Screen.ORDERS, restored.state.value.screen)
        assertEquals(1, repo.orderReads)
    }

    @Test fun serverQuoteThenConfirmedCreationClearsCart() = runTest(dispatcher) {
        val repo = FakeRepository()
        val local = MemoryLocal()
        val model = model(repo, local = local)
        advanceUntilIdle()
        model.authenticate("a@coffee.test", "long-password-123", null, false)
        advanceUntilIdle()
        model.selectCafe(1)
        advanceUntilIdle()
        model.add(CartLine(3, "3-350"))
        advanceUntilIdle()
        assertNotNull(local.cart("user-a"))
        model.calculate()
        advanceUntilIdle()
        assertEquals(1251, model.state.value.quote?.total)
        assertTrue(repo.creates.isEmpty())
        model.confirmOrder()
        advanceUntilIdle()
        assertEquals("order-1", model.state.value.activeOrder?.id)
        assertNull(model.state.value.cart)
        assertNull(local.cart("user-a"))
        assertFalse(model.state.value.pending)
        assertEquals("quote-1" to "durable-key-123", repo.creates.single())
    }

    @Test fun timeoutNeverShowsSuccessAndRestoredRetryUsesSameKey() = runTest(dispatcher) {
        val repo = FakeRepository().apply { createFailure = CoffeeFailure(FailureKind.TIMEOUT) }
        val tokens = FakeTokens("token-a")
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val first = model(repo, tokens, local)
        advanceUntilIdle()
        first.calculate()
        advanceUntilIdle()
        first.confirmOrder()
        advanceUntilIdle()
        assertEquals(FailureKind.TIMEOUT, first.state.value.error?.kind)
        assertNotNull(first.state.value.cart)
        assertNull(first.state.value.activeOrder)
        assertTrue(first.state.value.pending)
        assertNotNull(local.pending("user-a"))
        first.close()
        repo.createFailure = null
        val restored = model(repo, tokens, local)
        advanceUntilIdle()
        assertTrue(restored.state.value.pending)
        restored.confirmOrder()
        advanceUntilIdle()
        assertEquals(2, repo.creates.size)
        assertEquals(repo.creates[0], repo.creates[1])
        assertNull(restored.state.value.cart)
        assertEquals("order-1", restored.state.value.activeOrder?.id)
    }

    @Test fun stalePriceRequiresExplicitFreshQuoteAndKeepsCart() = runTest(dispatcher) {
        val repo = FakeRepository().apply { createFailure = CoffeeFailure(FailureKind.PRICE_CHANGED, "price_changed") }
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val model = model(repo, FakeTokens("token-a"), local)
        advanceUntilIdle()
        model.calculate(); advanceUntilIdle()
        model.confirmOrder(); advanceUntilIdle()
        assertNotNull(model.state.value.cart)
        assertNull(model.state.value.quote)
        assertNull(model.state.value.activeOrder)
        assertFalse(model.state.value.pending)
        assertNull(local.pending("user-a"))
        model.confirmOrder(); advanceUntilIdle()
        assertEquals(1, repo.creates.size)
    }

    @Test fun recoveryKeySurvivesLocalCleanupFailureAfterServerSuccess() = runTest(dispatcher) {
        val repo = FakeRepository()
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val model = model(repo, FakeTokens("token-a"), local)
        advanceUntilIdle()
        model.calculate(); advanceUntilIdle()
        local.failCartRemoval = true
        model.confirmOrder(); advanceUntilIdle()
        assertNotNull(local.pending("user-a"))
        assertNotNull(model.state.value.cart)
        assertNull(model.state.value.activeOrder)
        local.failCartRemoval = false
        model.confirmOrder(); advanceUntilIdle()
        assertEquals(repo.creates[0], repo.creates[1])
        assertNull(local.pending("user-a"))
        assertNull(local.cart("user-a"))
        assertEquals("order-1", model.state.value.activeOrder?.id)
    }

    @Test fun logoutClearsAccountStateAndCancelsDelayedResponses() = runTest(dispatcher) {
        val repo = FakeRepository()
        val tokens = FakeTokens("token-a")
        val local = MemoryLocal().apply { saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val model = model(repo, tokens, local)
        advanceUntilIdle()
        repo.orderDelay = 10_000
        model.refreshAccount()
        runCurrent()
        model.logout()
        advanceUntilIdle()
        assertNull(tokens.value)
        assertNull(local.cart("user-a"))
        assertNull(model.state.value.user)
        assertTrue(model.state.value.orders.isEmpty())
        assertNull(model.state.value.loyalty)
        repo.user = userB
        repo.orderDelay = 0
        model.authenticate("b@coffee.test", "long-password-123", null, false)
        advanceUntilIdle()
        assertEquals("user-b", model.state.value.user?.id)
        assertNull(model.state.value.cart)
        assertTrue(model.state.value.orders.isEmpty())
    }

    @Test fun expiredStoredSessionDoesNotAuthenticateLocally() = runTest(dispatcher) {
        val tokens = FakeTokens("expired-token")
        val repo = FakeRepository().apply { meFailure = CoffeeFailure(FailureKind.UNAUTHORIZED) }
        val model = model(repo, tokens)
        advanceUntilIdle()
        assertNull(model.state.value.user)
        assertNull(tokens.value)
        assertFalse(model.state.value.restoring)
        assertEquals(FailureKind.UNAUTHORIZED, model.state.value.error?.kind)
    }

    @Test fun offlineUsesPublicCacheAndDoesNotFabricateQuote() = runTest(dispatcher) {
        val repo = FakeRepository().apply { catalogFailure = CoffeeFailure(FailureKind.NETWORK); quoteFailure = CoffeeFailure(FailureKind.NETWORK) }
        val local = MemoryLocal().apply { saveCatalog(1, catalog); saveCart("user-a", Cart(1, listOf(CartLine(3, "3-350")))) }
        val model = model(repo, FakeTokens("token-a"), local)
        advanceUntilIdle()
        assertTrue(model.state.value.cached)
        assertEquals(catalog, model.state.value.catalog)
        model.calculate(); advanceUntilIdle()
        assertNull(model.state.value.quote)
        assertNotNull(model.state.value.cart)
        assertEquals(FailureKind.NETWORK, model.state.value.error?.kind)
        model.confirmOrder(); advanceUntilIdle()
        assertTrue(repo.creates.isEmpty())
    }

    @Test fun pollingStopsWhenBackgrounded() = runTest(dispatcher) {
        val repo = FakeRepository().apply { history = listOf(order) }
        val model = model(repo, FakeTokens("token-a"))
        advanceUntilIdle()
        model.setForeground(true)
        advanceTimeBy(5_001); runCurrent()
        assertEquals(1, repo.orderReads)
        model.setForeground(false)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, repo.orderReads)
    }

    @Test fun backgroundingCancelsAnInFlightPoll() = runTest(dispatcher) {
        val repo = FakeRepository().apply { history = listOf(order); orderReadDelay = 10_000; fetchedOrder = order.copy(status = "ready") }
        val model = model(repo, FakeTokens("token-a"))
        advanceUntilIdle()
        model.setForeground(true)
        advanceTimeBy(5_001); runCurrent()
        assertEquals(1, repo.orderReads)
        model.setForeground(false)
        advanceTimeBy(20_000); runCurrent()
        assertEquals("created", model.state.value.activeOrder?.status)
        assertNull(model.state.value.error)
    }
}

private val userA = User("user-a", "a@coffee.test", "A", "customer", false)
private val userB = User("user-b", "b@coffee.test", "B", "customer", false)
private val catalog = Catalog(listOf(Cafe(1, "Master", "Astana", "08–22", "", "", true)), listOf(Product(3, "Капучино", "", "Кофе", "cappuccino", true, listOf(Size("3-350", "350 мл", 1290)))), emptyList(), emptyList())
private val line = OrderLine("Капучино", "350 мл", 1, emptyList(), 0, "takeaway", "standard", 1290, 1290)
private val order = Order("order-1", "MC-001001", 1, "created", "unpaid", 1251, 39, 3, listOf(line), "", "2026-10-24T10:00:00Z", OrderQr("server-token", "mastercoffee:order:server-token", "2026-10-24T10:30:00Z", false))

private class FakeTokens(var value: String? = null) : SecureTokenStore {
    var writeCalls = 0
    override fun read() = value
    override fun write(token: String) { writeCalls++; value = token }
    override fun clear() { value = null }
}
private class MemoryLocal : LocalStore {
    var failCartRemoval = false
    var failCartSaves = false
    override var language = "ru"
    override var theme = "system"
    private val catalogs = mutableMapOf<Int?, Catalog>()
    private val carts = mutableMapOf<String, Cart>()
    private val pending = mutableMapOf<String, Pair<String, String>>()
    private val navigation = mutableMapOf<String, SavedNavigation>()
    override fun catalog(cafeId: Int?) = catalogs[cafeId]
    override fun saveCatalog(cafeId: Int?, catalog: Catalog) { catalogs[cafeId] = catalog }
    override fun cart(userId: String) = carts[userId]
    override fun saveCart(userId: String, cart: Cart?) { if (failCartSaves || (cart == null && failCartRemoval)) error("disk write failed"); if (cart == null) carts.remove(userId) else carts[userId] = cart }
    override fun pending(userId: String) = pending[userId]
    override fun savePending(userId: String, quoteId: String, key: String) { pending[userId] = quoteId to key }
    override fun clearPending(userId: String) { pending.remove(userId) }
    override fun clearAccount(userId: String) { carts.remove(userId); pending.remove(userId); navigation.remove(userId) }
    override fun navigation(userId: String) = navigation[userId]
    override fun saveNavigation(userId: String, navigation: SavedNavigation) { this.navigation[userId] = navigation }
}
private class FakeRepository : CoffeeRepository {
    var user = userA
    var logoutCalls = 0
    var createFailure: CoffeeFailure? = null
    var meFailure: CoffeeFailure? = null
    var catalogFailure: CoffeeFailure? = null
    var quoteFailure: CoffeeFailure? = null
    var history = emptyList<Order>()
    var orderDelay = 0L
    var orderReads = 0
    var orderReadDelay = 0L
    var fetchedOrder = order
    val creates = mutableListOf<Pair<String, String>>()
    var loginCalls = 0
    var loginDelay = 0L
    var ignoreLoginCancellation = false
    override suspend fun login(email: String, password: String): Session {
        loginCalls++
        if (ignoreLoginCancellation) withContext(NonCancellable) { delay(loginDelay) } else delay(loginDelay)
        return Session("token-${user.id}", user)
    }
    override suspend fun register(email: String, password: String, name: String) = login(email, password)
    override suspend fun me(token: String): User { meFailure?.let { throw it }; return user }
    override suspend fun logout(token: String) { logoutCalls++ }
    override suspend fun catalog(cafeId: Int?): Catalog { catalogFailure?.let { throw it }; return kz.master_coffee.app.catalog }
    override suspend fun quote(token: String, cart: Cart): Quote { quoteFailure?.let { throw it }; return Quote("quote-1", "2026-10-24T10:05:00Z", listOf(line), 1290, 3, 39, 1251) }
    override suspend fun createOrder(token: String, quoteId: String, idempotencyKey: String): Order { creates += quoteId to idempotencyKey; createFailure?.let { throw it }; return order }
    override suspend fun orders(token: String, before: Int?): OrderPage { if (orderDelay > 0) delay(orderDelay); return OrderPage(history, null) }
    override suspend fun order(token: String, id: String): Order { orderReads++; if (orderReadDelay > 0) delay(orderReadDelay); return fetchedOrder }
    override suspend fun cancel(token: String, id: String) = order.copy(status = "cancelled")
    override suspend fun refreshQr(token: String, id: String) = order
    override suspend fun loyalty(token: String) = Loyalty(0, 3, 50, 5)
    override fun close() {}
}
