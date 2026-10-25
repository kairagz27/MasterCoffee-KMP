package kz.master_coffee.app.data

import kotlinx.serialization.Serializable
import kz.master_coffee.app.domain.*

@Serializable internal data class LoginDto(val email: String, val password: String)
@Serializable internal data class RegisterDto(val email: String, val password: String, val name: String)
@Serializable internal data class UserDto(val id: String, val email: String, val name: String, val role: String = "customer", val email_verified: Boolean = false) {
    fun domain() = User(id, email, name, role, email_verified)
}
@Serializable internal data class SessionDto(val token: String, val expires_at: String, val user: UserDto)
@Serializable internal data class CafeDto(val id: Int, val name: String, val address: String, val working_hours: String, val phone: String = "", val image_key: String = "", val active: Boolean = true)
@Serializable internal data class SizeDto(val id: String, val label: String, val price: Int)
@Serializable internal data class ProductDto(val id: Int, val name: String, val description: String = "", val category: String, val image_key: String = "", val available: Boolean = true, val sizes: List<SizeDto>, val is_drink: Boolean = true)
@Serializable internal data class AddonDto(val id: String, val name: String, val group: String, val price: Int)
@Serializable internal data class StoryDto(val id: String, val title: String, val image_url: String, val active: Boolean = true)
@Serializable internal data class CatalogDto(val cafes: List<CafeDto>, val products: List<ProductDto>, val addons: List<AddonDto> = emptyList(), val stories: List<StoryDto> = emptyList()) {
    fun domain() = Catalog(cafes.map { Cafe(it.id, it.name, it.address, it.working_hours, it.phone, it.image_key, it.active) }, products.map { p -> Product(p.id, p.name, p.description, p.category, p.image_key, p.available, p.sizes.map { Size(it.id, it.label, it.price) }, p.is_drink) }, addons.map { Addon(it.id, it.name, it.group, it.price) }, stories.filter { it.active }.map { Story(it.id, it.title, it.image_url) })
}
@Serializable internal data class CartLineDto(val product_id: Int, val size_id: String, val quantity: Int, val addon_ids: List<String>, val sugar: Int, val order_type: String, val temperature: String) {
    fun domain() = CartLine(product_id, size_id, quantity, addon_ids, sugar, order_type, temperature)
}
internal fun CartLine.dto() = CartLineDto(productId, sizeId, quantity, addonIds, sugar, orderType, temperature)
@Serializable internal data class CartDto(val cafe_id: Int, val items: List<CartLineDto>, val comment: String = "") {
    fun domain() = Cart(cafe_id, items.map { it.domain() }, comment)
}
internal fun Cart.dto() = CartDto(cafeId, items.map { it.dto() }, comment)
@Serializable internal data class OrderLineDto(val name: String, val size_label: String, val quantity: Int, val addon_names: List<String> = emptyList(), val sugar: Int = 0, val order_type: String = "takeaway", val temperature: String = "standard", val unit_price: Int, val line_total: Int, val is_drink: Boolean = true) {
    fun domain() = OrderLine(name, size_label, quantity, addon_names, sugar, order_type, temperature, unit_price, line_total, is_drink)
}
@Serializable internal data class QuoteDto(val id: String, val expires_at: String, val items: List<OrderLineDto>, val subtotal: Int, val discount_percent: Int, val discount: Int, val total: Int) {
    fun domain() = Quote(id, expires_at, items.map { it.domain() }, subtotal, discount_percent, discount, total)
}
@Serializable internal data class CreateOrderDto(val quote_id: String)
@Serializable internal data class OrderQrDto(val token: String, val payload: String, val expires_at: String, val used: Boolean = false)
@Serializable internal data class OrderDto(val id: String, val number: String, val cafe_id: Int, val status: String, val payment_status: String, val total: Int, val discount: Int = 0, val discount_percent: Int = 0, val items: List<OrderLineDto>, val comment: String = "", val created_at: String, val qr: OrderQrDto? = null) {
    fun domain() = Order(id, number, cafe_id, status, payment_status, total, discount, discount_percent, items.map { it.domain() }, comment, created_at, qr?.let { OrderQr(it.token, it.payload, it.expires_at, it.used) })
}
@Serializable internal data class OrderPageDto(val orders: List<OrderDto>, val next_before: Int? = null)
@Serializable internal data class LoyaltyDto(val qualifying_orders: Int, val discount_percent: Int, val next_threshold: Int? = null, val next_discount_percent: Int? = null)
@Serializable internal data class ErrorBodyDto(val error: ErrorDto? = null)
@Serializable internal data class ErrorDto(val code: String = "", val message: String = "")
@Serializable internal data class PendingDto(val user_id: String, val quote_id: String, val key: String)
