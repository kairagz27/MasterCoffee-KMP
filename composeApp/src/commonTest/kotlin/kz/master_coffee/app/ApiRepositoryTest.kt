package kz.master_coffee.app

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kz.master_coffee.app.data.ApiRepository
import kz.master_coffee.app.domain.*
import kotlin.test.*

class ApiRepositoryTest {
    @Test fun catalogMapsUploadedImagesFoodFlagsAndLegacyDrinkDefaults() = runTest {
        val repository = ApiRepository(MockEngine {
            respond("""{"cafes":[],"products":[{"id":20,"name":"Cookie","category":"Food","image_key":"/uploads/cookie.png","is_drink":false,"sizes":[{"id":"20-one","label":"1","price":500}]},{"id":3,"name":"Coffee","category":"Coffee","image_key":"cappuccino","sizes":[{"id":"3-350","label":"350 ml","price":1290}]}],"addons":[],"stories":[{"id":"news","title":"News","image_url":"/uploads/news.png","active":true}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, "http://localhost:8080/api/v1", true)
        try {
            val catalog = repository.catalog(1)
            assertEquals("http://localhost:8080/uploads/cookie.png", catalog.products[0].imageKey)
            assertFalse(catalog.products[0].isDrink)
            assertEquals("cappuccino", catalog.products[1].imageKey)
            assertTrue(catalog.products[1].isDrink)
            assertEquals("http://localhost:8080/uploads/news.png", catalog.stories.single().imageUrl)
        } finally { repository.close() }
    }

    @Test fun quoteSendsIdentifiersAndBearerWithoutTrustedClientAmount() = runTest {
        var requestJson: JsonObject? = null
        val repository = ApiRepository(MockEngine { request ->
            assertEquals("Bearer secure-token", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/orders/quote", request.url.encodedPath)
            requestJson = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            respond("""{"id":"q1","expires_at":"2026-10-24T10:00:00Z","items":[],"subtotal":1350,"discount_percent":3,"discount":40,"total":1310} """, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }, "http://localhost/api/v1", true)
        try {
            val quote = repository.quote("secure-token", Cart(1, listOf(CartLine(3, "3-350", addonIds = listOf("essence-cherry")))))
            assertEquals(1310, quote.total)
            assertEquals(setOf("cafe_id", "items", "comment"), requestJson!!.keys)
            val item = requestJson!!["items"]!!.jsonArray.first().jsonObject
            assertEquals("3-350", item["size_id"]!!.jsonPrimitive.content)
            assertFalse("total" in item)
            assertFalse("price" in item)
            assertFalse("discount" in item)
        } finally { repository.close() }
    }

    @Test fun serverErrorPreservesTypedFailure() = runTest {
        val repository = ApiRepository(MockEngine { respond("""{"error":{"code":"price_changed","message":"Review again"}}""", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json")) }, "http://localhost/api/v1", true)
        try {
            val failure = assertFailsWith<CoffeeFailure> { repository.createOrder("token", "q", "key123456") }
            assertEquals(FailureKind.PRICE_CHANGED, failure.kind)
            assertEquals("price_changed", failure.code)
        } finally { repository.close() }
    }

    @Test fun requestTimeoutIsDistinctFromSuccessfulOrder() = runTest {
        val repository = ApiRepository(MockEngine { request -> throw HttpRequestTimeoutException(request) }, "http://localhost/api/v1", true)
        try { assertEquals(FailureKind.TIMEOUT, assertFailsWith<CoffeeFailure> { repository.createOrder("token", "q", "key123456") }.kind) }
        finally { repository.close() }
    }

    @Test fun productionClientRejectsCleartextUrl() {
        val engine = MockEngine { respond("") }
        try { assertFailsWith<IllegalArgumentException> { ApiRepository(engine, "http://localhost/api/v1", false) } }
        finally { engine.close() }
    }
}
