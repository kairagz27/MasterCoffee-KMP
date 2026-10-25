package kz.master_coffee.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kz.master_coffee.app.data.*
import kz.master_coffee.app.domain.*

enum class Screen { MENU, CAFES, CART, ORDERS, PROFILE }
data class CoffeeState(
    val restoring: Boolean = true, val busy: Boolean = false, val user: User? = null,
    val catalog: Catalog? = null, val cached: Boolean = false, val catalogLoading: Boolean = false, val cafeId: Int? = null,
    val cart: Cart? = null, val quote: Quote? = null, val pending: Boolean = false,
    val orders: List<Order> = emptyList(), val nextBefore: Int? = null, val activeOrder: Order? = null,
    val loyalty: Loyalty? = null, val error: CoffeeFailure? = null,
    val screen: Screen = Screen.MENU, val language: String = "ru", val theme: String = "system", val apiAddress: String = ""
)
data class CoffeeEnvironment(val repository: CoffeeRepository, val tokens: SecureTokenStore, val local: LocalStore, val keyFactory: () -> String = ::newIdempotencyKey, val apiAddress: String = "")

class CoffeeViewModel(
    private val environment: () -> CoffeeEnvironment,
    storageDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    constructor(repository: CoffeeRepository, tokens: SecureTokenStore, local: LocalStore, keyFactory: () -> String = ::newIdempotencyKey, apiAddress: String = "", storageDispatcher: CoroutineDispatcher = Dispatchers.Default) : this({ CoffeeEnvironment(repository, tokens, local, keyFactory, apiAddress) }, storageDispatcher)
    private val storage = BackgroundStorage(storageDispatcher)
    private lateinit var dependencies: CoffeeEnvironment
    private val repository get() = dependencies.repository
    private val tokens get() = dependencies.tokens
    private val local get() = dependencies.local
    private val mutable = MutableStateFlow(CoffeeState())
    val state: StateFlow<CoffeeState> = mutable.asStateFlow()
    private val generation = MutableStateFlow(0L)
    private var session: Session? = null
    private var accountJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var authJob: Job? = null
    private var catalogJob: Job? = null
    private var pollJob: Job? = null
    private var commentJob: Job? = null
    private var foreground = false
    private var closed = false
    private var initialized = false
    private var catalogRevision = 0L

    private suspend fun initializeEnvironment() {
        var created: CoffeeEnvironment? = null
        try {
            dependencies = storage.run { environment().also { created = it } }
            initialized = true
        } finally { if (!initialized) created?.repository?.close() }
    }

    init {
        authJob = viewModelScope.launch {
            try {
                // Includes engine, Settings, SharedPreferences and Keychain construction.
                initializeEnvironment()
                restore()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(restoring = false, error = e.asFailure()) } }
        }
    }
    private suspend fun checkCurrent(epoch: Long, token: String? = null) {
        currentCoroutineContext().ensureActive()
        if (generation.value != epoch || (token != null && session?.token != token)) throw CancellationException("Superseded account request")
    }
    private suspend fun <T> accountStorage(epoch: Long, block: () -> T): T = storage.run {
        if (generation.value != epoch) throw CancellationException("Superseded account storage")
        block()
    }
    fun restoreSession() {
        if (mutable.value.busy || mutable.value.restoring) return
        authJob?.cancel()
        mutable.update { it.copy(restoring = true, error = null) }
        authJob = viewModelScope.launch {
            try {
                if (!initialized) initializeEnvironment()
                restore()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(restoring = false, busy = false, error = e.asFailure()) } }
        }
    }
    private suspend fun restore() {
        val epoch = generation.value
        try {
            withTimeout(25_000) {
                val preferences = storage.run { Triple(local.language, local.theme, tokens.read()) }
                checkCurrent(epoch)
                mutable.update { it.copy(language = preferences.first, theme = preferences.second, apiAddress = dependencies.apiAddress) }
                val token = preferences.third
                if (token == null) { mutable.update { it.copy(restoring = false) }; refreshCatalog(); return@withTimeout }
                val user = repository.me(token)
                checkCurrent(epoch)
                if (user.role != "customer") {
                    accountStorage(epoch) { tokens.clear(); local.clearAccount(user.id) }
                    revokeUnsupportedSession(token)
                    throw CoffeeFailure(FailureKind.INVALID, "customer_required")
                }
                establish(Session(token, user), epoch)
            }
        } catch (e: TimeoutCancellationException) { mutable.update { it.copy(restoring = false, error = CoffeeFailure(FailureKind.TIMEOUT)) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            checkCurrent(epoch)
            val failure = e.asFailure()
            if (failure.kind == FailureKind.UNAUTHORIZED) storage.run { tokens.clear() }
            mutable.update { it.copy(restoring = false, error = failure) }
            refreshCatalog()
        }
    }
    fun authenticate(email: String, password: String, name: String?, register: Boolean) {
        if (!initialized || mutable.value.busy || mutable.value.restoring) return
        if (email.isBlank() || password.encodeToByteArray().size !in 12..72 || (register && name.isNullOrBlank())) {
            mutable.update { it.copy(error = CoffeeFailure(FailureKind.INVALID, "auth_validation")) }; return
        }
        authJob?.cancel()
        val epoch = generation.value
        // Set before launching, so two taps in one Main-loop turn cannot race.
        mutable.update { it.copy(busy = true, error = null) }
        authJob = viewModelScope.launch {
            try {
                withTimeout(25_000) {
                    val result = if (register) repository.register(email.trim(), password, name!!.trim()) else repository.login(email.trim(), password)
                    checkCurrent(epoch)
                    if (result.user.role != "customer") { revokeUnsupportedSession(result.token); throw CoffeeFailure(FailureKind.INVALID, "customer_required") }
                    accountStorage(epoch) { tokens.write(result.token) }
                    checkCurrent(epoch)
                    establish(result, epoch)
                }
            } catch (e: TimeoutCancellationException) { if (generation.value == epoch) mutable.update { it.copy(busy = false, error = CoffeeFailure(FailureKind.TIMEOUT)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch) mutable.update { it.copy(busy = false, error = e.asFailure()) } }
        }
    }
    private suspend fun revokeUnsupportedSession(token: String) {
        try { withTimeout(5_000) { repository.logout(token) } }
        catch (_: TimeoutCancellationException) { }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The server's expiry still bounds an unreachable session. */ }
    }
    private suspend fun establish(value: Session, epoch: Long) {
        val saved = accountStorage(epoch) { Triple(local.cart(value.user.id), local.pending(value.user.id), local.navigation(value.user.id)) }
        checkCurrent(epoch)
        session = value
        val nav = saved.third
        val screen = if (saved.second != null) Screen.CART else nav?.screen?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.MENU
        mutable.update { it.copy(restoring = false, busy = false, user = value.user, cart = saved.first, cafeId = saved.first?.cafeId ?: nav?.cafeId, pending = saved.second != null, error = null, screen = screen) }
        refreshCatalog()
        refreshAccount(nav?.orderId)
        restartPolling()
    }
    fun logout() {
        if (!initialized) return
        val previous = session
        generation.value += 1
        val epoch = generation.value
        authJob?.cancel(); accountJob.cancel(); catalogJob?.cancel(); pollJob?.cancel(); commentJob?.cancel()
        accountJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        session = null
        mutable.update { CoffeeState(restoring = false, busy = true, catalog = it.catalog, cached = it.cached, language = it.language, theme = it.theme, apiAddress = dependencies.apiAddress) }
        // New sign-in stays disabled until all older storage writes and erasure finish.
        authJob = viewModelScope.launch {
            try {
                storage.run { tokens.clear(); previous?.let { local.clearAccount(it.user.id) } }
                if (generation.value == epoch) mutable.update { it.copy(busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch) mutable.update { it.copy(busy = false, error = e.asFailure()) } }
            if (previous != null) try { withTimeout(15_000) { repository.logout(previous.token) } } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e } catch (_: Exception) { /* Local credential erased; unreachable server session expires. */ }
        }
    }
    fun language(value: String) { mutable.update { it.copy(language = value) }; preference { local.language = value } }
    fun theme(value: String) { mutable.update { it.copy(theme = value) }; preference { local.theme = value } }
    private fun preference(block: () -> Unit) { if (initialized) viewModelScope.launch { try { storage.run(block) } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(error = e.asFailure()) } } } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun navigate(screen: Screen) { mutable.update { it.copy(screen = screen, error = null) }; persistNavigation(); if (screen == Screen.ORDERS || screen == Screen.PROFILE) refreshAccount() }
    fun back(): Boolean {
        if (mutable.value.screen == Screen.MENU) return false
        navigate(Screen.MENU)
        return true
    }
    private fun persistNavigation() {
        val s = session ?: return
        val epoch = generation.value
        val current = mutable.value
        viewModelScope.launch(accountJob) {
            try { accountStorage(epoch) { local.saveNavigation(s.user.id, SavedNavigation(current.screen.name, current.cafeId, current.activeOrder?.id)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch) mutable.update { it.copy(error = e.asFailure()) } }
        }
    }
    fun selectCafe(id: Int) = accountAction { s, epoch ->
        if (mutable.value.pending) return@accountAction
        if (mutable.value.cafeId != id) {
            accountStorage(epoch) { local.saveCart(s.user.id, null) }
            checkCurrent(epoch, s.token)
            mutable.update { it.copy(cafeId = id, cart = null, quote = null, screen = Screen.MENU) }
            persistNavigation()
        }
        refreshCatalog()
    }
    fun refreshCatalog() {
        if (!initialized) return
        catalogJob?.cancel()
        val cafeId = mutable.value.cafeId
        val epoch = generation.value
        val revision = ++catalogRevision
        mutable.update { it.copy(catalogLoading = true) }
        catalogJob = viewModelScope.launch {
            try {
                val cached = storage.run { local.catalog(cafeId) }
                checkCurrent(epoch)
                mutable.update { it.copy(catalog = cached ?: it.catalog, cached = cached != null) }
                val catalog = withTimeout(20_000) { repository.catalog(cafeId) }
                checkCurrent(epoch)
                storage.run { local.saveCatalog(cafeId, catalog) }
                checkCurrent(epoch)
                if (mutable.value.cafeId == cafeId) mutable.update { it.copy(catalog = catalog, cached = false) }
            } catch (e: TimeoutCancellationException) { if (generation.value == epoch) mutable.update { it.copy(error = CoffeeFailure(FailureKind.TIMEOUT)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch && mutable.value.cafeId == cafeId) mutable.update { it.copy(error = e.asFailure()) } }
            finally { if (generation.value == epoch && mutable.value.cafeId == cafeId && catalogRevision == revision) mutable.update { it.copy(catalogLoading = false) } }
        }
    }
    fun add(line: CartLine) = accountAction { s, epoch ->
        val cafe = mutable.value.cafeId ?: return@accountAction
        if (mutable.value.pending) return@accountAction
        val cart = (mutable.value.cart ?: Cart(cafe, emptyList())).let { it.copy(items = it.items + line) }
        saveCart(s, epoch, cart)
        mutable.update { it.copy(screen = Screen.CART) }; persistNavigation()
    }
    fun quantity(index: Int, quantity: Int) = accountAction { s, epoch ->
        if (mutable.value.pending) return@accountAction
        val cart = mutable.value.cart ?: return@accountAction
        saveCart(s, epoch, cart.copy(items = cart.items.mapIndexedNotNull { i, line -> if (i != index) line else if (quantity < 1) null else line.copy(quantity = quantity.coerceAtMost(20)) }))
    }
    fun comment(value: String) {
        if (mutable.value.pending || mutable.value.busy) return
        val s = session ?: return
        val epoch = generation.value
        val cart = mutable.value.cart?.copy(comment = value.take(500)) ?: return
        mutable.update { it.copy(cart = cart, quote = null) }
        commentJob?.cancel()
        commentJob = viewModelScope.launch(accountJob) {
            try { delay(250); accountStorage(epoch) { local.saveCart(s.user.id, cart) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch) mutable.update { it.copy(error = e.asFailure()) } }
        }
    }
    private suspend fun saveCart(s: Session, epoch: Long, cart: Cart) {
        accountStorage(epoch) { local.saveCart(s.user.id, cart) }
        checkCurrent(epoch, s.token)
        mutable.update { it.copy(cart = cart.takeIf { c -> c.items.isNotEmpty() }, quote = null) }
    }
    fun calculate() = accountAction { s, epoch ->
        if (mutable.value.pending) return@accountAction
        commentJob?.join()
        val cart = mutable.value.cart ?: return@accountAction
        accountStorage(epoch) { local.saveCart(s.user.id, cart) }
        val quote = repository.quote(s.token, cart)
        checkCurrent(epoch, s.token)
        mutable.update { it.copy(quote = quote) }
    }
    fun confirmOrder() = accountAction { s, epoch ->
        val quote = mutable.value.quote
        val pending = accountStorage(epoch) {
            local.pending(s.user.id) ?: quote?.let { val key = dependencies.keyFactory(); local.savePending(s.user.id, it.id, key); it.id to key }
        } ?: return@accountAction
        checkCurrent(epoch, s.token)
        mutable.update { it.copy(pending = true) }
        try {
            val order = repository.createOrder(s.token, pending.first, pending.second)
            checkCurrent(epoch, s.token)
            accountStorage(epoch) {
                // Never erase recovery data before the old cart is durably removed.
                local.saveCart(s.user.id, null)
                local.clearPending(s.user.id)
            }
            checkCurrent(epoch, s.token)
            mutable.update { it.copy(cart = null, quote = null, pending = false, activeOrder = order, orders = listOf(order) + it.orders.filterNot { o -> o.id == order.id }, screen = Screen.ORDERS) }
            persistNavigation(); restartPolling()
        } catch (e: CoffeeFailure) {
            if (e.kind in listOf(FailureKind.INVALID, FailureKind.PRICE_CHANGED)) {
                accountStorage(epoch) { local.clearPending(s.user.id) }
                mutable.update { it.copy(pending = false, quote = null) }
            }
            throw e
        }
    }
    fun refreshAccount(selectedId: String? = null) = accountAction { s, epoch ->
        val orders = repository.orders(s.token)
        val loyalty = repository.loyalty(s.token)
        val selected = selectedId ?: mutable.value.activeOrder?.id
        val selectedOrder = orders.orders.firstOrNull { it.id == selected }
            ?: selected?.let { repository.order(s.token, it) }
        checkCurrent(epoch, s.token)
        mutable.update { current ->
            current.copy(orders = orders.orders, nextBefore = orders.nextBefore, loyalty = loyalty, activeOrder = selectedOrder ?: orders.orders.firstOrNull { it.active })
        }
    }
    fun moreOrders() = accountAction { s, epoch ->
        val before = mutable.value.nextBefore ?: return@accountAction
        val page = repository.orders(s.token, before); checkCurrent(epoch, s.token)
        mutable.update { it.copy(orders = (it.orders + page.orders).distinctBy { o -> o.id }, nextBefore = page.nextBefore) }
    }
    fun openOrder(order: Order) { mutable.update { it.copy(activeOrder = order, screen = Screen.ORDERS) }; persistNavigation(); refreshOrder() }
    fun refreshOrder() = accountAction { s, epoch ->
        val id = mutable.value.activeOrder?.id ?: return@accountAction
        val order = repository.order(s.token, id); checkCurrent(epoch, s.token); updateOrder(order)
        val loyalty = repository.loyalty(s.token); checkCurrent(epoch, s.token); mutable.update { it.copy(loyalty = loyalty) }
    }
    fun cancelOrder() = accountAction { s, epoch -> mutable.value.activeOrder?.let { val order = repository.cancel(s.token, it.id); checkCurrent(epoch, s.token); updateOrder(order) } }
    fun refreshQr() = accountAction { s, epoch -> mutable.value.activeOrder?.let { val order = repository.refreshQr(s.token, it.id); checkCurrent(epoch, s.token); updateOrder(order) } }
    private fun updateOrder(order: Order) { mutable.update { it.copy(activeOrder = order, orders = listOf(order) + it.orders.filterNot { o -> o.id == order.id }) } }
    fun setForeground(value: Boolean) { foreground = value; restartPolling() }
    private fun restartPolling() {
        pollJob?.cancel()
        if (!foreground || session == null) return
        val epoch = generation.value
        pollJob = viewModelScope.launch(accountJob) {
            while (isActive) {
                delay(5_000)
                val s = session ?: break
                val selected = mutable.value.activeOrder
                if (selected?.active == true && !mutable.value.busy) try {
                    val order = withTimeout(20_000) { repository.order(s.token, selected.id) }
                    checkCurrent(epoch, s.token)
                    if (mutable.value.activeOrder?.id != selected.id) continue
                    updateOrder(order)
                    if (!order.active) { val loyalty = repository.loyalty(s.token); checkCurrent(epoch, s.token); mutable.update { it.copy(loyalty = loyalty) } }
                } catch (e: TimeoutCancellationException) { if (generation.value == epoch) mutable.update { it.copy(error = CoffeeFailure(FailureKind.TIMEOUT)) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (generation.value == epoch) { val error = e.asFailure(); if (error.kind == FailureKind.UNAUTHORIZED) logout(); mutable.update { it.copy(error = error) } } }
            }
        }
    }
    private fun accountAction(block: suspend (Session, Long) -> Unit) {
        val s = session ?: return
        if (mutable.value.busy) return
        val epoch = generation.value
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(accountJob) {
            try { withTimeout(25_000) { block(s, epoch) } }
            catch (e: TimeoutCancellationException) { if (generation.value == epoch) mutable.update { it.copy(error = CoffeeFailure(FailureKind.TIMEOUT)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.value == epoch) { val error = e.asFailure(); if (error.kind == FailureKind.UNAUTHORIZED) logout(); mutable.update { it.copy(error = error) } } }
            finally { if (generation.value == epoch) mutable.update { it.copy(busy = false) } }
        }
    }
    fun close() { if (!closed) { closed = true; viewModelScope.cancel(); accountJob.cancel(); if (initialized) repository.close() } }
    override fun onCleared() { close() }
}
private fun Exception.asFailure() = this as? CoffeeFailure ?: CoffeeFailure(FailureKind.SERVER, "unexpected")
