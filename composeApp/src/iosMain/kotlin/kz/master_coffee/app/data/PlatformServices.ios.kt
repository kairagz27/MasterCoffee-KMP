@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package kz.master_coffee.app.data

import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

private fun foundationValue(value: CFTypeRef?): Any = CFBridgingRelease(CFRetain(value))!!

/** Device-only Keychain item; no session credentials are stored in NSUserDefaults. */
private class IosTokenStore : SecureTokenStore {
    private fun query(): MutableMap<Any?, Any?> = mutableMapOf(
        foundationValue(kSecClass) to foundationValue(kSecClassGenericPassword),
        foundationValue(kSecAttrService) to "kz.master_coffee.app.session",
        foundationValue(kSecAttrAccount) to "current_session"
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

actual fun createPlatformServices(): PlatformServices {
    val debug = kotlin.native.Platform.isDebugBinary
    val configured = NSBundle.mainBundle.objectForInfoDictionaryKey("MasterCoffeeAPIURL") as? String
    val url = configured?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
        ?: if (debug) "http://127.0.0.1:8080/api/v1" else "https://api.mastercoffee.example/api/v1"
    return PlatformServices(Darwin.create(), IosTokenStore(), url, debug)
}
actual fun newIdempotencyKey(): String = NSUUID().UUIDString
