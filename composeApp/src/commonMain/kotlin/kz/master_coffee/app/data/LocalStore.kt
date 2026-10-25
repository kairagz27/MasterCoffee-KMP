package kz.master_coffee.app.data

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.encodeToString
import kz.master_coffee.app.domain.*

interface SecureTokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

interface LocalStore {
    var language: String
    var theme: String
    fun catalog(cafeId: Int?): Catalog?
    fun saveCatalog(cafeId: Int?, catalog: Catalog)
    fun cart(userId: String): Cart?
    fun saveCart(userId: String, cart: Cart?)
    fun pending(userId: String): Pair<String, String>?
    fun savePending(userId: String, quoteId: String, key: String)
    fun clearPending(userId: String)
    fun clearAccount(userId: String)
    fun navigation(userId: String): SavedNavigation? = null
    fun saveNavigation(userId: String, navigation: SavedNavigation) {}
}

@kotlinx.serialization.Serializable
data class SavedNavigation(val screen: String = "MENU", val cafeId: Int? = null, val orderId: String? = null)

class SettingsLocalStore(private val settings: Settings = Settings()) : LocalStore {
    init {
        // Never trust or migrate the former local OTP/login/profile flags.
        listOf("isLoggedIn", "userName", "email", "userAvatarResKey", "userAvatarKey", "orders", "cart", "stories_cache").forEach(settings::remove)
    }
    override var language: String
        get() = settings.getString("mvp.language", "ru")
        set(value) { settings.putString("mvp.language", value) }
    override var theme: String
        get() = settings.getString("mvp.theme", "system")
        set(value) { settings.putString("mvp.theme", value) }
    override fun catalog(cafeId: Int?): Catalog? = settings.getStringOrNull("mvp.catalog.${cafeId ?: 0}")?.let { runCatching { coffeeJson.decodeFromString<CatalogDto>(it).domain() }.getOrNull() }
    override fun saveCatalog(cafeId: Int?, catalog: Catalog) {
        val dto = CatalogDto(catalog.cafes.map { CafeDto(it.id, it.name, it.address, it.hours, it.phone, it.imageKey, it.active) }, catalog.products.map { p -> ProductDto(p.id, p.name, p.description, p.category, p.imageKey, p.available, p.sizes.map { SizeDto(it.id, it.label, it.price) }, p.isDrink) }, catalog.addons.map { AddonDto(it.id, it.name, it.group, it.price) }, catalog.stories.map { StoryDto(it.id, it.title, it.imageUrl) })
        settings.putString("mvp.catalog.${cafeId ?: 0}", coffeeJson.encodeToString(dto))
    }
    override fun cart(userId: String) = settings.getStringOrNull("mvp.cart.$userId")?.let { coffeeJson.decodeFromString<CartDto>(it).domain() }
    override fun saveCart(userId: String, cart: Cart?) {
        if (cart == null || cart.items.isEmpty()) settings.remove("mvp.cart.$userId") else settings.putString("mvp.cart.$userId", coffeeJson.encodeToString(cart.dto()))
    }
    override fun pending(userId: String) = settings.getStringOrNull("mvp.pending.$userId")?.let { coffeeJson.decodeFromString<PendingDto>(it).also { record -> check(record.user_id == userId) } }?.let { it.quote_id to it.key }
    override fun savePending(userId: String, quoteId: String, key: String) { settings.putString("mvp.pending.$userId", coffeeJson.encodeToString(PendingDto(userId, quoteId, key))) }
    override fun clearPending(userId: String) { settings.remove("mvp.pending.$userId") }
    override fun navigation(userId: String) = settings.getStringOrNull("mvp.navigation.$userId")?.let { runCatching { coffeeJson.decodeFromString<SavedNavigation>(it) }.getOrNull() }
    override fun saveNavigation(userId: String, navigation: SavedNavigation) { settings.putString("mvp.navigation.$userId", coffeeJson.encodeToString(navigation)) }
    override fun clearAccount(userId: String) { saveCart(userId, null); clearPending(userId); settings.remove("mvp.navigation.$userId") }
}

data class PlatformServices(val engine: HttpClientEngine, val tokens: SecureTokenStore, val apiUrl: String, val allowHttp: Boolean, val local: LocalStore = SettingsLocalStore())
expect fun createPlatformServices(): PlatformServices
expect fun newIdempotencyKey(): String
