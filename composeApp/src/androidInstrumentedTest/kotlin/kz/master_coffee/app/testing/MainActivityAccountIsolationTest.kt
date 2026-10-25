package kz.master_coffee.app.testing

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kz.master_coffee.app.BuildConfig
import kz.master_coffee.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Clean-emulator opt-in. Every account/order action uses the real MainActivity and its native
 * platform stores. No fake repository, dependency override, raw data clearing or force-stop.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityAccountIsolationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun nativeAccountsRemainIsolatedAcrossLogoutAndNewMainActivity() {
        val arguments = InstrumentationRegistry.getArguments()
        val liveApi = arguments.getString("masterCoffeeLiveApi")?.trimEnd('/')
        assumeTrue(
            "Native accounts require clean-device launch opt-in and a development API",
            arguments.getString("masterCoffeeLaunchSmoke") == "true" && !liveApi.isNullOrBlank()
        )
        assumeTrue(
            "Native account fixtures must never run on a physical user's device",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"
        )
        assertEquals("Actual App must use the opted-in development API", liveApi, BuildConfig.API_BASE_URL.trimEnd('/'))
        val fixture = UUID.randomUUID().toString()
        val emailA = "native-a-$fixture@verification.invalid"
        val emailB = "native-b-$fixture@verification.invalid"
        val password = "Native-ui-verification-2026!"
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitEnabled("auth_submit")
            rule.onNodeWithText("EN").performScrollTo().performClick()
            register(emailA, password, "Native verification A")
            addDrink(selectCafe = true)
            cartAction("cart_calculate")
            scrollTo("cart_list", hasTestTag("cart_confirm"))
            clickEnabled("cart_confirm")
            waitTag("order_status")
            rule.onNodeWithTag("order_status").assertTextEquals("Created")
            val orderA = visibleOrderNumber()
            assertTrue("Real API must return an MC order number", orderA.matches(Regex("MC-[0-9]+")))
            rule.onNodeWithTag("tab_orders").performClick()
            waitText(orderA, substring = true)
            scrollTo("orders_list", hasText(orderA, substring = true) and hasClickAction())

            // This cart must be erased by logout, and must not appear in the second account.
            addDrink(selectCafe = false)
            scrollTo("cart_list", hasTestTag("cart_calculate"))
            waitEnabled("cart_calculate")
            rule.onAllNodesWithText(EMPTY_CART).assertCountEquals(0)
            capture("01-a-cart-before-logout", "cart_list")
            logout()

            register(emailB, password, "Native verification B")
            assertEmptyOrders()
            capture("02-b-empty-history", "orders_list")
            assertProfile(emailB, 0)
            assertEmptyCart()

            // Closing the Activity clears the ViewModelStore. A new MainActivity must validate the
            // actual Android Keystore session and reload the account, rather than reuse a fake VM.
            scenario.close()
            scenario = null
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitTag("tab_profile")
            assertProfile(emailB, 0)
            capture("03-b-native-session-restored", "profile_list")
            assertEmptyCart()
            assertEmptyOrders()
            logout()

            login(emailA, password)
            assertProfile(emailA, 0)
            rule.onAllNodesWithText(emailB).assertCountEquals(0)
            assertEmptyCart() // Product added before A's logout was deliberately cleared.
            rule.onNodeWithTag("tab_orders").performClick()
            waitTag("orders_list")
            scrollTo("orders_list", hasText(orderA, substring = true) and hasClickAction())
            rule.onNode(hasText(orderA, substring = true) and hasClickAction()).performClick()
            waitTag("order_status")
            rule.onNodeWithTag("order_status").assertTextEquals("Created")
            scrollTo("orders_list", hasText("Cancel order") and hasClickAction())
            rule.onNode(hasText("Cancel order") and hasClickAction()).performClick()
            rule.onNodeWithText("Continue").performClick()
            rule.waitUntil(40_000) {
                rule.onAllNodes(hasTestTag("order_status") and hasText("Cancelled"))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            rule.onNodeWithTag("orders_list").performScrollToIndex(0)
            waitText(orderA, substring = true)
            capture("04-a-history-returned-cancelled", "orders_list")
            logout()
        } finally {
            // Leave the clean CI device signed out for MainActivityLaunchTest. Waiting for the
            // enabled submit button also waits for serialized token/cart/navigation erasure.
            try {
                if (scenario != null) cleanupVisibleSession()
            } finally {
                scenario?.close()
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0, Bundle().apply { putString("masterCoffeeNativeAccountsComplete", "true") }
        )
    }

    private fun register(email: String, password: String, name: String) {
        waitEnabled("auth_submit")
        if (!exists(hasTestTag("auth_name"))) {
            rule.onNodeWithTag("auth_switch").performScrollTo().performClick()
        }
        rule.onNodeWithTag("auth_name").performScrollTo().performTextReplacement(name)
        rule.onNodeWithTag("auth_email").performScrollTo().performTextReplacement(email)
        rule.onNodeWithTag("auth_password").performScrollTo().performTextReplacement(password)
        rule.onNodeWithTag("auth_submit").performScrollTo()
        clickEnabled("auth_submit")
        waitTag("tab_cafes")
        waitAccountIdle()
    }

    private fun login(email: String, password: String) {
        waitEnabled("auth_submit")
        if (exists(hasTestTag("auth_name"))) {
            rule.onNodeWithTag("auth_switch").performScrollTo().performClick()
        }
        rule.onNodeWithTag("auth_email").performScrollTo().performTextReplacement(email)
        rule.onNodeWithTag("auth_password").performScrollTo().performTextReplacement(password)
        rule.onNodeWithTag("auth_submit").performScrollTo()
        clickEnabled("auth_submit")
        waitTag("tab_profile")
        waitAccountIdle()
    }

    private fun addDrink(selectCafe: Boolean) {
        if (selectCafe) {
            rule.onNodeWithTag("tab_cafes").performClick()
            waitTag("cafe_list")
            scrollTo("cafe_list", hasTestTag("cafe_1"))
            clickEnabled("cafe_1")
        } else rule.onNodeWithTag("tab_menu").performClick()
        waitTag("menu_list")
        scrollTo("menu_list", hasTestTag("product_3"))
        clickEnabled("product_3")
        waitTag("customization_list")
        rule.onNodeWithTag("size_3-450").performScrollTo().performClick()
        clickEnabled("product_add")
        waitTag("cart_list")
        scrollTo("cart_list", hasTestTag("cart_calculate"))
        waitEnabled("cart_calculate")
    }

    private fun cartAction(tag: String) {
        scrollTo("cart_list", hasTestTag(tag))
        clickEnabled(tag)
    }

    private fun assertEmptyOrders() {
        rule.onNodeWithTag("tab_orders").performClick()
        waitTag("orders_list")
        waitAccountIdle()
        waitText(EMPTY_ORDERS)
        rule.onAllNodes(hasText("MC-", substring = true)).assertCountEquals(0)
    }

    private fun assertEmptyCart() {
        rule.onNodeWithTag("tab_cart").performClick()
        waitTag("cart_list")
        waitText(EMPTY_CART)
        rule.onAllNodesWithTag("cart_calculate").assertCountEquals(0)
        rule.onAllNodesWithTag("cart_confirm").assertCountEquals(0)
    }

    private fun assertProfile(email: String, receipts: Int) {
        rule.onNodeWithTag("tab_profile").performClick()
        waitTag("profile_list")
        waitAccountIdle()
        val profileEmail = hasText(email) and hasAnyAncestor(hasTestTag("profile_list"))
        scrollTo("profile_list", profileEmail)
        rule.onNode(profileEmail).assertIsDisplayed()
        scrollTo("profile_list", hasText("Completed drink orders: $receipts"))
        waitText("Completed drink orders: $receipts")
    }

    private fun logout() {
        rule.onNodeWithTag("tab_profile").performClick()
        waitTag("profile_list")
        scrollTo("profile_list", hasTestTag("profile_logout"))
        clickEnabled("profile_logout")
        waitEnabled("auth_submit")
        rule.onAllNodesWithTag("tab_profile").assertCountEquals(0)
    }

    private fun cleanupVisibleSession() {
        if (exists(hasTestTag("auth_email"))) {
            waitEnabled("auth_submit")
            return
        }
        // A failed customization assertion may have left the app's own modal dialog open.
        if (exists(hasTestTag("customization_list"))) {
            rule.onNodeWithContentDescription("Close").performClick()
        }
        if (exists(hasTestTag("tab_profile"))) logout()
    }

    private fun visibleOrderNumber(): String {
        waitText("Order № MC-", substring = true)
        return rule.onAllNodes(hasText("Order № MC-", substring = true)).fetchSemanticsNodes()
            .asSequence().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().asSequence() }
            .mapNotNull { Regex("MC-[0-9]+").find(it.text)?.value }.first()
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        rule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun waitTag(tag: String) = rule.waitUntil(40_000) { exists(hasTestTag(tag)) }

    private fun waitEnabled(tag: String) = rule.waitUntil(40_000) { exists(hasTestTag(tag) and isEnabled()) }

    private fun waitAccountIdle() =
        rule.waitUntil(40_000) { exists(hasContentDescription("Refresh") and isEnabled()) }

    private fun waitText(value: String, substring: Boolean = false) =
        rule.waitUntil(40_000) { exists(hasText(value, substring = substring)) }

    private fun clickEnabled(tag: String) {
        waitEnabled(tag)
        rule.onNodeWithTag(tag).performClick()
    }

    private fun scrollTo(container: String, target: SemanticsMatcher) {
        rule.waitUntil(40_000) {
            runCatching {
                rule.onNodeWithTag(container).performScrollToNode(target)
                exists(target)
            }.getOrDefault(false)
        }
    }

    private fun capture(label: String, tag: String) {
        rule.waitForIdle()
        rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().recycle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("verification"))
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "android-api${Build.VERSION.SDK_INT}-native-accounts-$label.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private companion object {
        const val EMPTY_CART = "Your cart is empty. Choose a drink from the menu."
        const val EMPTY_ORDERS = "Your orders will appear here."
    }
}
