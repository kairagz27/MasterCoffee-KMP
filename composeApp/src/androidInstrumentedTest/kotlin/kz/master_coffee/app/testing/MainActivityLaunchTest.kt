package kz.master_coffee.app.testing

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kz.master_coffee.app.MainActivity
import kz.master_coffee.app.data.SettingsLocalStore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt in only on a clean CI device. Existing accounts and application data are never erased. */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun coldLaunchRotationAndBackgroundUseTheActualApplication() {
        assumeTrue("Enable masterCoffeeLaunchSmoke only on a clean verification device", InstrumentationRegistry.getArguments().getString("masterCoffeeLaunchSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appDark = !systemDark
        val preferences = SettingsLocalStore()
        val previousTheme = preferences.theme
        preferences.theme = if (appDark) "dark" else "light"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(30_000) { rule.onAllNodesWithTag("auth_email").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                scenario.onActivity { assertEquals(!appDark, WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars) }
                capture("01-cold-auth")
                val draft = "rotation-draft@verification.invalid"
                rule.onNodeWithTag("auth_email").performTextInput(draft)
                scenario.recreate()
                rule.waitUntil(30_000) { rule.onAllNodesWithTag("auth_email").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                rule.onNodeWithTag("auth_email").assertTextContains(draft)
                scenario.onActivity { assertEquals(!appDark, WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars) }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                rule.onNodeWithTag("auth_email").assertTextContains(draft)
                capture("02-rotation-resume")
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("masterCoffeeLaunchComplete", "true") })
                // No password or sign-in is performed; the device's account storage remains untouched.
            }
        } finally {
            preferences.theme = previousTheme
        }
    }

    private fun capture(label: String) {
        rule.waitForIdle()
        rule.onNodeWithTag("auth_email").performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap().recycle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("verification"))
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "android-api${android.os.Build.VERSION.SDK_INT}-main-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
