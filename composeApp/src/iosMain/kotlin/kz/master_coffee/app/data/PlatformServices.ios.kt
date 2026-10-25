@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package kz.master_coffee.app.data

import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import kotlinx.serialization.encodeToString
import kz.master_coffee.app.domain.Cart

private fun foundationValue(value: CFTypeRef?): Any = CFBridgingRelease(CFRetain(value))!!

/** Device-only Keychain item; no session credentials are stored in NSUserDefaults. */
private class IosTokenStore(private val account: String = "current_session") : SecureTokenStore {
    private fun query(): MutableMap<Any?, Any?> = mutableMapOf(
        foundationValue(kSecClass) to foundationValue(kSecClassGenericPassword),
        foundationValue(kSecAttrService) to "kz.master_coffee.app.session",
        foundationValue(kSecAttrAccount) to account
    )
    private inline fun <T> withQuery(query: Map<Any?, Any?>, block: (CFDictionaryRef) -> T): T {
        val reference = CFBridgingRetain(query) as CFDictionaryRef
        return try { block(reference) } finally { CFRelease(reference) }
    }
    override fun read(): String? = memScoped {
        val query = query().apply {
            put(foundationValue(kSecReturnData), foundationValue(kCFBooleanTrue))
            put(foundationValue(kSecMatchLimit), foundationValue(kSecMatchLimitOne))
        }
        val result = alloc<CFTypeRefVar>()
        result.value = null
        val status = withQuery(query) { SecItemCopyMatching(it, result.ptr) }
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess) { "Cannot read Keychain session: $status" }
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }
    override fun write(token: String) {
        val bytes = token.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val values = mapOf<Any?, Any?>(
            foundationValue(kSecValueData) to data,
            foundationValue(kSecAttrAccessible) to foundationValue(kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        )
        val status = withQuery(query()) { identity -> withQuery(values) { SecItemUpdate(identity, it) } }
        if (status == errSecItemNotFound) {
            check(withQuery(query().apply { putAll(values) }) { SecItemAdd(it, null) } == errSecSuccess) { "Cannot save Keychain session" }
        } else check(status == errSecSuccess) { "Cannot update Keychain session: $status" }
    }
    override fun clear() {
        val status = withQuery(query()) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Cannot delete Keychain session: $status" }
    }
}

/** Checked Security writes also make the pending request durable across process death. */
private class IosDurableLocalStore(private val delegate: LocalStore = SettingsLocalStore()) : LocalStore by delegate {
    private fun recovery(userId: String) = IosTokenStore("pending_order:$userId")
    private fun basket(userId: String) = IosTokenStore("cart:$userId")
    private fun migrate(userId: String) {
        val marker = IosTokenStore("migration_v1:$userId")
        if (marker.read() != null) return
        val oldCart = delegate.cart(userId)
        val oldPending = delegate.pending(userId)
        if (basket(userId).read() == null && oldCart != null) basket(userId).write(coffeeJson.encodeToString(oldCart.dto()))
        if (recovery(userId).read() == null && oldPending != null) recovery(userId).write(coffeeJson.encodeToString(PendingDto(userId, oldPending.first, oldPending.second)))
        marker.write("1")
        delegate.saveCart(userId, null); delegate.clearPending(userId)
    }
    override fun cart(userId: String): Cart? { migrate(userId); return basket(userId).read()?.let { coffeeJson.decodeFromString<CartDto>(it).domain() } }
    override fun saveCart(userId: String, cart: Cart?) {
        if (cart == null || cart.items.isEmpty()) basket(userId).clear()
        else basket(userId).write(coffeeJson.encodeToString(cart.dto()))
    }
    override fun pending(userId: String): Pair<String, String>? { migrate(userId); return recovery(userId).read()?.let {
        coffeeJson.decodeFromString<PendingDto>(it).also { record -> check(record.user_id == userId) }
    }?.let { it.quote_id to it.key } }
    override fun savePending(userId: String, quoteId: String, key: String) { recovery(userId).write(coffeeJson.encodeToString(PendingDto(userId, quoteId, key))) }
    override fun clearPending(userId: String) { recovery(userId).clear() }
    override fun clearAccount(userId: String) { delegate.clearAccount(userId); saveCart(userId, null); clearPending(userId) }
}

actual fun createPlatformServices(): PlatformServices {
    val debug = kotlin.native.Platform.isDebugBinary
    val configured = NSBundle.mainBundle.objectForInfoDictionaryKey("MasterCoffeeAPIURL") as? String
    val url = configured?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
        ?: if (debug) "http://127.0.0.1:8080/api/v1" else error("Set MASTER_COFFEE_API_URL to the real HTTPS API before release")
    return PlatformServices(Darwin.create(), IosTokenStore(), url, debug, IosDurableLocalStore())
}
actual fun newIdempotencyKey(): String = NSUUID().UUIDString
