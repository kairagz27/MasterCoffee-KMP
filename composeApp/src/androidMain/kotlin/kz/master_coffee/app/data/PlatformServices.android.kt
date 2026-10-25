package kz.master_coffee.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.ktor.client.engine.okhttp.OkHttp
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kz.master_coffee.app.BuildConfig
import kotlinx.serialization.encodeToString
import kz.master_coffee.app.domain.Cart

object AndroidEnvironment {
    lateinit var context: Context
}

/** Only AES-GCM ciphertext reaches preferences. The non-exportable key lives in Android Keystore. */
private class AndroidTokenStore(context: Context) : SecureTokenStore {
    private val preferences = context.getSharedPreferences("secure_session_v1", Context.MODE_PRIVATE)
    private val alias = "master_coffee_session_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun read(): String? {
        val encoded = preferences.getString("encrypted_token", null) ?: return null
        return try {
            val parts = encoded.split(':')
            check(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { clear(); null }
    }
    override fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString("encrypted_token", payload).commit()) { "Cannot save secure session" }
    }
    override fun clear() { check(preferences.edit().clear().commit()) { "Cannot clear secure session" } }
}

/** Idempotency recovery must be committed before transmitting the create request. */
private class AndroidDurableLocalStore(context: Context, private val delegate: LocalStore = SettingsLocalStore()) : LocalStore by delegate {
    private val preferences = context.getSharedPreferences("durable_orders_v1", Context.MODE_PRIVATE)
    private fun migrate(userId: String) {
        if (preferences.getBoolean("migrated_v1:$userId", false)) return
        val editor = preferences.edit()
        val oldCart = delegate.cart(userId)
        val oldPending = delegate.pending(userId)
        if (!preferences.contains("cart:$userId") && oldCart != null) editor.putString("cart:$userId", coffeeJson.encodeToString(oldCart.dto()))
        if (!preferences.contains(userId) && oldPending != null) editor.putString(userId, coffeeJson.encodeToString(PendingDto(userId, oldPending.first, oldPending.second)))
        check(editor.putBoolean("migrated_v1:$userId", true).commit()) { "Cannot migrate account storage" }
        delegate.saveCart(userId, null); delegate.clearPending(userId)
    }
    override fun cart(userId: String): Cart? { migrate(userId); return preferences.getString("cart:$userId", null)?.let { coffeeJson.decodeFromString<CartDto>(it).domain() } }
    override fun saveCart(userId: String, cart: Cart?) {
        val editor = preferences.edit()
        if (cart == null || cart.items.isEmpty()) editor.remove("cart:$userId")
        else editor.putString("cart:$userId", coffeeJson.encodeToString(cart.dto()))
        check(editor.commit()) { "Cannot persist cart" }
    }
    override fun pending(userId: String): Pair<String, String>? { migrate(userId); return preferences.getString(userId, null)?.let {
        coffeeJson.decodeFromString<PendingDto>(it).also { record -> check(record.user_id == userId) }
    }?.let { it.quote_id to it.key } }
    override fun savePending(userId: String, quoteId: String, key: String) {
        val value = coffeeJson.encodeToString(PendingDto(userId, quoteId, key))
        check(preferences.edit().putString(userId, value).commit()) { "Cannot persist order recovery key" }
    }
    override fun clearPending(userId: String) { check(preferences.edit().remove(userId).commit()) { "Cannot clear order recovery key" } }
    override fun clearAccount(userId: String) { delegate.clearAccount(userId); saveCart(userId, null); clearPending(userId) }
}

actual fun createPlatformServices(): PlatformServices {
    val context = AndroidEnvironment.context
    return PlatformServices(OkHttp.create(), AndroidTokenStore(context), BuildConfig.API_BASE_URL, BuildConfig.DEBUG, AndroidDurableLocalStore(context))
}
actual fun newIdempotencyKey(): String = UUID.randomUUID().toString()
