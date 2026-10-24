package kz.master_coffee.app.domain

data class User(val id: String, val email: String, val name: String, val role: String, val emailVerified: Boolean)
data class Cafe(val id: Int, val name: String, val address: String, val hours: String, val phone: String, val imageKey: String, val active: Boolean)
data class Size(val id: String, val label: String, val price: Int)
data class Product(val id: Int, val name: String, val description: String, val category: String, val imageKey: String, val available: Boolean, val sizes: List<Size>)
data class Addon(val id: String, val name: String, val group: String, val price: Int)
data class Story(val id: String, val title: String, val imageUrl: String)
data class Catalog(val cafes: List<Cafe>, val products: List<Product>, val addons: List<Addon>, val stories: List<Story>)
data class CartLine(val productId: Int, val sizeId: String, val quantity: Int = 1, val addonIds: List<String> = emptyList(), val sugar: Int = 0, val orderType: String = "takeaway", val temperature: String = "standard")
data class Cart(val cafeId: Int, val items: List<CartLine>, val comment: String = "")
data class OrderLine(val name: String, val sizeLabel: String, val quantity: Int, val addonNames: List<String>, val sugar: Int, val orderType: String, val temperature: String, val unitPrice: Int, val lineTotal: Int)
data class Quote(val id: String, val expiresAt: String, val items: List<OrderLine>, val subtotal: Int, val discountPercent: Int, val discount: Int, val total: Int)
data class OrderQr(val token: String, val payload: String, val expiresAt: String, val used: Boolean)
data class Order(val id: String, val number: String, val cafeId: Int, val status: String, val paymentStatus: String, val total: Int, val discount: Int, val discountPercent: Int, val items: List<OrderLine>, val comment: String, val createdAt: String, val qr: OrderQr?) {
    val active: Boolean get() = status != "completed" && status != "cancelled"
}
data class OrderPage(val orders: List<Order>, val nextBefore: Int?)
data class Loyalty(val qualifyingOrders: Int, val discountPercent: Int, val nextThreshold: Int?, val nextDiscountPercent: Int?)
data class Session(val token: String, val user: User)

interface CoffeeRepository {
    suspend fun login(email: String, password: String): Session
    suspend fun register(email: String, password: String, name: String): Session
    suspend fun me(token: String): User
    suspend fun logout(token: String)
    suspend fun catalog(cafeId: Int?): Catalog
    suspend fun quote(token: String, cart: Cart): Quote
    suspend fun createOrder(token: String, quoteId: String, idempotencyKey: String): Order
    suspend fun orders(token: String, before: Int? = null): OrderPage
    suspend fun order(token: String, id: String): Order
    suspend fun cancel(token: String, id: String): Order
    suspend fun refreshQr(token: String, id: String): Order
    suspend fun loyalty(token: String): Loyalty
    fun close()
}

enum class FailureKind { NETWORK, TIMEOUT, UNAUTHORIZED, PRICE_CHANGED, INVALID, SERVER }
class CoffeeFailure(val kind: FailureKind, val code: String = "", val detail: String = "") : Exception(detail)
