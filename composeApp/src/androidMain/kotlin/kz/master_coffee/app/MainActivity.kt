package kz.master_coffee.app

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import kz.master_coffee.app.data.AndroidEnvironment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidEnvironment.initialize(this)

        setContent {
            App(onDarkModeChanged = { dark -> updateSystemBarAppearance(window, dark) })
        }
    }
}

/** The in-app theme can differ from the device theme used during Activity creation. */
fun updateSystemBarAppearance(window: Window, dark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
