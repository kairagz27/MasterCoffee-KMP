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
    val catalog: Catalog? = null, val cached: Boolean = false, val cafeId: Int? = null,
    val cart: Cart? = null, val quote: Quote? = null, val pending: Boolean = false,
    val orders: List<Order> = emptyList(), val nextBefore: Int? = null, val activeOrder: Order? = null,
    val loyalty: Loyalty? = null, val error: CoffeeFailure? = null,
    val screen: Screen = Screen.MENU, val language: String = "ru", val theme: String = "system", val apiAddress: String = ""
)

class CoffeeViewModel(
    private val repository: CoffeeRepository,
    private val tokens: SecureTokenStore,
    private val local: LocalStore,
    private val keyFactory: () -> String = ::newIdempotencyKey,
    private val apiAddress: String = ""
) : ViewModel() {
    private val mutable = MutableStateFlow(CoffeeState(language = local.language, theme = local.theme, catalog = local.catalog(null), cached = local.catalog(null) != null, apiAddress = apiAddress))
    val state: StateFlow<CoffeeState> = mutable.asStateFlow()
    private var session: Session? = null
    private var accountJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var catalogJob: Job? = null
    private var pollJob: Job? = null
    private var foreground = false
    private var closed = false

    init { restoreSession() }

    fun restoreSession() {
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(restoring = true, error = null) }
            val token = try { tokens.read() } catch (_: Exception) { tokens.clear(); null }
            if (token == null) {
                mutable.update { it.copy(restoring = false) }
                refreshCatalog()
                return@launch
            }
            try { establish(Session(token, repository.me(token))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failure = e.asFailure()
                if (failure.kind == FailureKind.UNAUTHORIZED) tokens.clear()
                mutable.update { it.copy(restoring = false, error = failure) }
                refreshCatalog()
            }
        }
    }

    fun authenticate(email: String, password: String, name: String?, register: Boolean) {
        if (mutable.value.busy) return
        if (email.isBlank() || password.encodeToByteArray().size !in 12..72 || (register && name.isNullOrBlank())) {
            mutable.update { it.copy(error = CoffeeFailure(FailureKind.INVALID, "auth_validation")) }; return
        }
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val result = if (register) repository.register(email.trim(), password, name!!.trim()) else repository.login(email.trim(), password)
                tokens.write(result.token)
                establish(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, error = e.asFailure()) } }
        }
    }

    private fun establish(value: Session) {
        session = value
        val cart = local.cart(value.user.id)
        mutable.update { it.copy(restoring = false, busy = false, user = value.user, cart = cart, cafeId = cart?.cafeId, pending = local.pending(value.user.id) != null, error = null, screen = Screen.MENU) }
        refreshCatalog()
        refreshAccount()
        restartPolling()
    }

    fun logout() {
        val previous = session
        accountJob.cancel()
        accountJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        pollJob?.cancel()
        session = null
        tokens.clear()
        previous?.let { local.clearAccount(it.user.id) }
        mutable.update { CoffeeState(restoring = false, catalog = it.catalog, cached = it.cached, language = it.language, theme = it.theme, apiAddress = apiAddress) }
        if (previous != null) viewModelScope.launch { try { repository.logout(previous.token) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local credentials are already erased; the server session also expires. */ } }
    }

    fun language(value: String) { local.language = value; mutable.update { it.copy(language = value) } }
    fun theme(value: String) { local.theme = value; mutable.update { it.copy(theme = value) } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun navigate(screen: Screen) { mutable.update { it.copy(screen = screen, error = null) }; if (screen == Screen.ORDERS || screen == Screen.PROFILE) refreshAccount() }

    fun selectCafe(id: Int) {
        if (mutable.value.pending || mutable.value.busy) return
        if (mutable.value.cafeId != id) {
            mutable.update { it.copy(cafeId = id, cart = null, quote = null, screen = Screen.MENU) }
            session?.let { local.saveCart(it.user.id, null) }
        }
        refreshCatalog()
    }

    fun refreshCatalog() {
        catalogJob?.cancel()
        val cafeId = mutable.value.cafeId
        val cached = local.catalog(cafeId)
        mutable.update { it.copy(catalog = cached, cached = cached != null) }
        catalogJob = viewModelScope.launch {
            try {
                val catalog = repository.catalog(cafeId)
                local.saveCatalog(cafeId, catalog)
                if (mutable.value.cafeId == cafeId) mutable.update { it.copy(catalog = catalog, cached = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (mutable.value.cafeId == cafeId) mutable.update { it.copy(error = e.asFailure()) } }
        }
    }

    fun add(line: CartLine) {
        val current = mutable.value
        val user = session?.user ?: return
        val cafe = current.cafeId ?: return
        if (current.pending || current.busy) return
        val cart = (current.cart ?: Cart(cafe, emptyList())).let { it.copy(items = it.items + line) }
        local.saveCart(user.id, cart)
        mutable.update { it.copy(cart = cart, quote = null, screen = Screen.CART, error = null) }
    }
    fun quantity(index: Int, quantity: Int) {
        if (mutable.value.pending || mutable.value.busy) return
        val cart = mutable.value.cart ?: return
        val lines = cart.items.mapIndexedNotNull { i, line -> if (i != index) line else if (quantity < 1) null else line.copy(quantity = quantity.coerceAtMost(20)) }
        saveCart(cart.copy(items = lines))
    }
    fun comment(value: String) { if (!mutable.value.pending && !mutable.value.busy) mutable.value.cart?.let { saveCart(it.copy(comment = value.take(500))) } }
    private fun saveCart(cart: Cart) {
        session?.let { local.saveCart(it.user.id, cart) }
        mutable.update { it.copy(cart = cart.takeIf { c -> c.items.isNotEmpty() }, quote = null) }
    }

    fun calculate() = accountAction { s ->
        val cart = mutable.value.cart ?: return@accountAction
        if (mutable.value.pending) return@accountAction
        val quote = repository.quote(s.token, cart)
        currentCoroutineContext().ensureActive()
        mutable.update { it.copy(quote = quote) }
    }

    fun confirmOrder() = accountAction { s ->
        val pending = local.pending(s.user.id) ?: mutable.value.quote?.let { quote ->
            val key = keyFactory()
            // Persist before transmission. Every retry, including after process death, uses this exact pair.
            local.savePending(s.user.id, quote.id, key)
            quote.id to key
        } ?: return@accountAction
        mutable.update { it.copy(pending = true) }
        try {
            val order = repository.createOrder(s.token, pending.first, pending.second)
            currentCoroutineContext().ensureActive()
            local.saveCart(s.user.id, null)
            // Commit cart removal before dropping the recovery key. A crash between the two
            // can still recover the existing order; it cannot turn the old cart into a new one.
            local.clearPending(s.user.id)
            mutable.update { it.copy(cart = null, quote = null, pending = false, activeOrder = order, orders = listOf(order) + it.orders.filterNot { o -> o.id == order.id }, screen = Screen.ORDERS) }
            restartPolling()
        } catch (e: CoffeeFailure) {
            if (e.kind != FailureKind.TIMEOUT && e.kind != FailureKind.NETWORK && e.kind != FailureKind.SERVER) {
                local.clearPending(s.user.id)
                mutable.update { it.copy(pending = false, quote = null) }
            }
            throw e
        }
    }

    fun refreshAccount() = accountAction { s ->
        val orders = repository.orders(s.token)
        val loyalty = repository.loyalty(s.token)
        currentCoroutineContext().ensureActive()
        mutable.update { current ->
            val active = current.activeOrder?.let { selected -> orders.orders.firstOrNull { it.id == selected.id } ?: selected } ?: orders.orders.firstOrNull { it.active }
            current.copy(orders = orders.orders, nextBefore = orders.nextBefore, loyalty = loyalty, activeOrder = active)
        }
    }
    fun moreOrders() = accountAction { s ->
        val before = mutable.value.nextBefore ?: return@accountAction
        val page = repository.orders(s.token, before)
        currentCoroutineContext().ensureActive()
        mutable.update { it.copy(orders = (it.orders + page.orders).distinctBy { o -> o.id }, nextBefore = page.nextBefore) }
    }
    fun openOrder(order: Order) { mutable.update { it.copy(activeOrder = order, screen = Screen.ORDERS) }; refreshOrder() }
    fun refreshOrder() = accountAction { s ->
        val current = mutable.value.activeOrder ?: return@accountAction
        val order = repository.order(s.token, current.id)
        currentCoroutineContext().ensureActive()
        updateOrder(order)
        val loyalty = repository.loyalty(s.token)
        mutable.update { it.copy(loyalty = loyalty) }
    }
    fun cancelOrder() = accountAction { s -> mutable.value.activeOrder?.let { val order = repository.cancel(s.token, it.id); currentCoroutineContext().ensureActive(); updateOrder(order) } }
    fun refreshQr() = accountAction { s -> mutable.value.activeOrder?.let { val order = repository.refreshQr(s.token, it.id); currentCoroutineContext().ensureActive(); updateOrder(order) } }
    private fun updateOrder(order: Order) { mutable.update { it.copy(activeOrder = order, orders = (listOf(order) + it.orders.filterNot { o -> o.id == order.id })) } }

    fun setForeground(value: Boolean) { foreground = value; restartPolling() }
    private fun restartPolling() {
        pollJob?.cancel()
        if (!foreground || session == null) return
        pollJob = viewModelScope.launch(accountJob) {
            while (isActive) {
                delay(5_000)
                val s = session ?: break
                val selected = mutable.value.activeOrder
                if (selected?.active == true && !mutable.value.busy) {
                    // This request is a child of the lifecycle polling job, so STOP cancels it too.
                    try {
                        val order = repository.order(s.token, selected.id)
                        currentCoroutineContext().ensureActive()
                        if (session?.token != s.token) break
                        updateOrder(order)
                        if (!order.active) {
                            val loyalty = repository.loyalty(s.token)
                            currentCoroutineContext().ensureActive()
                            mutable.update { it.copy(loyalty = loyalty) }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val failure = e.asFailure()
                        if (failure.kind == FailureKind.UNAUTHORIZED) logout()
                        mutable.update { it.copy(error = failure) }
                    }
                }
            }
        }
    }
    private fun accountAction(block: suspend (Session) -> Unit) {
        val active = session ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(accountJob) {
            try { block(active) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failure = e.asFailure()
                if (failure.kind == FailureKind.UNAUTHORIZED) { logout(); mutable.update { it.copy(error = failure) } }
                else mutable.update { it.copy(error = failure) }
            } finally { if (session?.token == active.token) mutable.update { it.copy(busy = false) } }
        }
    }
    fun close() { if (!closed) { closed = true; viewModelScope.cancel(); catalogJob?.cancel(); accountJob.cancel(); pollJob?.cancel(); repository.close() } }
    override fun onCleared() { close() }
}

private fun Exception.asFailure() = this as? CoffeeFailure ?: CoffeeFailure(FailureKind.SERVER, "unexpected")
