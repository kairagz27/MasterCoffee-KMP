package kz.master_coffee.app.testing

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kz.master_coffee.app.MasterCoffeeTheme
import kz.master_coffee.app.data.ApiRepository
import kz.master_coffee.app.presentation.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Opt-in real API + PostgreSQL scenario. Real app account/Keystore files are never opened. */
@RunWith(AndroidJUnit4::class)
class MobileBackendJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MobileTestActivity>()
    private var model: CoffeeViewModel? = null
    @After fun close() { InstrumentationRegistry.getInstrumentation().runOnMainSync { MobileTestContent.content = null; model?.close() } }

    @Test fun realCustomerUiCashierPaymentReadyHandoffAndLoyalty() {
        val args = InstrumentationRegistry.getArguments()
        val api = args.getString("masterCoffeeLiveApi")?.trimEnd('/')
        assumeTrue("Supply masterCoffeeLiveApi to opt in to the real backend journey", !api.isNullOrBlank())
        val staffEmail = requireNotNull(args.getString("masterCoffeeStaffEmail")) { "Supply a development staff email" }
        val staffPassword = requireNotNull(args.getString("masterCoffeeStaffPassword")) { "Supply the development staff password" }
        val repository = ApiRepository(OkHttp.create(), api!!, true)
        val vm = CoffeeViewModel(repository, ManagedTokens(), ManagedLocal())
        model = vm
        rule.runOnUiThread { MobileTestContent.content = { val state by vm.state.collectAsState(); MasterCoffeeTheme(false) { CoffeeApp(state, vm) } } }
        rule.waitUntil(20_000) { !vm.state.value.restoring }
        rule.onNodeWithTag("auth_switch").performScrollTo().performClick()
        val email = "android-${UUID.randomUUID()}@verification.invalid"
        rule.onNodeWithTag("auth_name").performScrollTo().performTextInput("Android verification")
        rule.onNodeWithTag("auth_email").performScrollTo().performTextInput(email)
        rule.onNodeWithTag("auth_password").performScrollTo().performTextInput("Android-verification-2026!")
        rule.onNodeWithTag("auth_submit").performScrollTo().performClick()
        waitReady(vm) { it.user != null && it.catalog != null && !it.catalogLoading }
        capture("01-catalog")
        rule.onNodeWithTag("tab_cafes").performClick()
        rule.onNodeWithTag("cafe_list").performScrollToNode(hasTestTag("cafe_1"))
        rule.onNodeWithTag("cafe_1").performClick()
        waitReady(vm) { it.cafeId == 1 && !it.catalogLoading }
        rule.onNodeWithTag("menu_list").performScrollToNode(hasTestTag("product_3"))
        rule.onNodeWithTag("product_3").performClick()
        rule.onNodeWithTag("size_3-450").performScrollTo().performClick()
        capture("02-customization")
        rule.onNodeWithTag("product_add").performClick()
        waitReady(vm) { it.cart != null }
        rule.onNodeWithTag("cart_list").performScrollToNode(hasTestTag("cart_calculate"))
        rule.onNodeWithTag("cart_calculate").performClick()
        waitReady(vm) { it.quote != null }
        val expectedTotal = vm.state.value.quote!!.total
        rule.onNodeWithTag("cart_list").performScrollToNode(hasTestTag("cart_confirm"))
        rule.onNodeWithTag("cart_confirm").performClick()
        waitReady(vm) { it.activeOrder != null }
        val created = vm.state.value.activeOrder!!
        assertEquals(expectedTotal, created.total)
        assertNotNull(created.qr)
        assertNull(vm.state.value.cart)
        capture("03-server-order-qr")

        val staffClient = HttpClient(OkHttp) { install(ContentNegotiation) { json() } }
        try {
            runBlocking {
                val response = staffClient.post("$api/auth/login") { contentType(ContentType.Application.Json); setBody(buildJsonObject { put("email", staffEmail); put("password", staffPassword) }) }
                check(response.status.value == 200) { "Staff sign-in failed: HTTP ${response.status.value}" }
                val token = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("token").jsonPrimitive.content
                suspend fun staff(path: String, body: JsonObject, method: HttpMethod = HttpMethod.Post) {
                    val result = staffClient.request("$api/$path") { this.method = method; bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
                    check(result.status.value in 200..299) { "Staff operation failed: HTTP ${result.status.value}" }
                }
                staff("staff/orders/lookup", buildJsonObject { put("code", created.qr!!.payload) })
                staff("staff/orders/${created.id}/payment", buildJsonObject { put("qr_token", created.qr!!.token) })
                for (status in listOf("accepted", "preparing", "ready")) staff("staff/orders/${created.id}/status", buildJsonObject { put("status", status) }, HttpMethod.Patch)
                rule.runOnUiThread { vm.setForeground(true) }
                waitReady(vm) { it.activeOrder?.status == "ready" && it.activeOrder.paymentStatus == "paid" }
                rule.onNodeWithTag("order_status").assertTextEquals("Ready for pickup")
                capture("04-ready")
                staff("staff/orders/${created.id}/status", buildJsonObject { put("status", "completed") }, HttpMethod.Patch)
                waitReady(vm) { it.activeOrder?.status == "completed" && it.loyalty?.qualifyingOrders == 1 }
                capture("05-completed")
            }
        } finally { staffClient.close() }
        rule.onNodeWithTag("tab_orders").performClick()
        waitReady(vm) { it.orders.any { order -> order.id == created.id && order.status == "completed" } }
        capture("06-history")
        rule.onNodeWithTag("tab_profile").performClick()
        waitReady(vm) { it.loyalty?.qualifyingOrders == 1 }
        rule.onNodeWithText("Completed drink orders: 1").assertExists()
        capture("07-loyalty")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("masterCoffeeLiveOrderComplete", "true") })
    }
    private fun waitReady(vm: CoffeeViewModel, condition: (CoffeeState) -> Boolean) = rule.waitUntil(40_000) { val state = vm.state.value; !state.busy && condition(state) }
    private fun capture(label: String) {
        rule.waitForIdle()
        val tag = when (label) {
            "01-catalog" -> "menu_list"
            "02-customization" -> "customization_list"
            "07-loyalty" -> "profile_list"
            else -> "orders_list"
        }
        rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().recycle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("verification"))
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Android screenshot was unavailable" }
        File(directory, "android-api${android.os.Build.VERSION.SDK_INT}-live-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
