package kz.master_coffee.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kz.master_coffee.app.domain.*

internal val coffeeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class ApiRepository(engine: HttpClientEngine, baseUrl: String, allowHttp: Boolean) : CoffeeRepository {
    private val base = baseUrl.trimEnd('/').also {
        require(it.startsWith("https://") || (allowHttp && it.startsWith("http://"))) { "Release requires an HTTPS API URL" }
    }
    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(coffeeJson) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
        // Credentials are never logged. Redirects cannot forward them to another origin.
        followRedirects = false
        expectSuccess = false
    }
    private suspend inline fun <reified T> call(path: String, token: String? = null, method: HttpMethod = HttpMethod.Get, body: Any? = null, key: String? = null): T {
        val response = request(path, token, method, body, key)
        return try { response.body() } catch (e: CancellationException) { throw e } catch (_: Exception) { throw CoffeeFailure(FailureKind.SERVER, "invalid_response") }
    }
    private suspend fun request(path: String, token: String?, method: HttpMethod, body: Any?, key: String? = null): HttpResponse {
        val response = try {
            client.request("$base/$path") {
                this.method = method
                if (token != null) bearerAuth(token)
                if (key != null) header("Idempotency-Key", key)
                accept(ContentType.Application.Json)
                if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: HttpRequestTimeoutException) { throw CoffeeFailure(FailureKind.TIMEOUT)
        } catch (_: ConnectTimeoutException) { throw CoffeeFailure(FailureKind.TIMEOUT)
        } catch (_: SocketTimeoutException) { throw CoffeeFailure(FailureKind.TIMEOUT)
        } catch (_: Exception) { throw CoffeeFailure(FailureKind.NETWORK) }
        if (response.status.value !in 200..299) {
            val error = try { response.body<ErrorBodyDto>().error } catch (_: Exception) { null }
            val kind = when {
                response.status.value == 401 -> FailureKind.UNAUTHORIZED
                error?.code == "quote_expired" || error?.code == "price_changed" -> FailureKind.PRICE_CHANGED
                response.status.value in 400..499 -> FailureKind.INVALID
                else -> FailureKind.SERVER
            }
            throw CoffeeFailure(kind, error?.code.orEmpty(), error?.message.orEmpty())
        }
        return response
    }
    override suspend fun login(email: String, password: String): Session = call<SessionDto>("auth/login", method = HttpMethod.Post, body = LoginDto(email, password)).let { Session(it.token, it.user.domain()) }
    override suspend fun register(email: String, password: String, name: String): Session = call<SessionDto>("auth/register", method = HttpMethod.Post, body = RegisterDto(email, password, name)).let { Session(it.token, it.user.domain()) }
    override suspend fun me(token: String) = call<UserDto>("auth/me", token).domain()
    override suspend fun logout(token: String) { request("auth/logout", token, HttpMethod.Post, emptyMap<String, String>()) }
    override suspend fun catalog(cafeId: Int?): Catalog {
        val catalog = call<CatalogDto>("catalog${cafeId?.let { "?cafe_id=$it" }.orEmpty()}").domain()
        val origin = base.removeSuffix("/api/v1")
        return catalog.copy(
            products = catalog.products.map { if (it.imageKey.startsWith("/uploads/")) it.copy(imageKey = origin + it.imageKey) else it },
            stories = catalog.stories.map { if (it.imageUrl.startsWith("/uploads/")) it.copy(imageUrl = origin + it.imageUrl) else it }
        )
    }
    override suspend fun quote(token: String, cart: Cart) = call<QuoteDto>("orders/quote", token, HttpMethod.Post, cart.dto()).domain()
    override suspend fun createOrder(token: String, quoteId: String, idempotencyKey: String) = call<OrderDto>("orders", token, HttpMethod.Post, CreateOrderDto(quoteId), idempotencyKey).domain()
    override suspend fun orders(token: String, before: Int?) = call<OrderPageDto>("orders?limit=20${before?.let { "&before=$it" }.orEmpty()}", token).let { OrderPage(it.orders.map { o -> o.domain() }, it.next_before) }
    override suspend fun order(token: String, id: String) = call<OrderDto>("orders/$id", token).domain()
    override suspend fun cancel(token: String, id: String) = call<OrderDto>("orders/$id/cancel", token, HttpMethod.Post, emptyMap<String, String>()).domain()
    override suspend fun refreshQr(token: String, id: String) = call<OrderDto>("orders/$id/qr", token, HttpMethod.Post, emptyMap<String, String>()).domain()
    override suspend fun loyalty(token: String) = call<LoyaltyDto>("loyalty", token).let { Loyalty(it.qualifying_orders, it.discount_percent, it.next_threshold, it.next_discount_percent) }
    override fun close() = client.close()
}
