package kz.master_coffee.app.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*

/** Debug-only instrumentation host. It never opens the application's real account storage. */
object MobileTestContent { var content: (@Composable () -> Unit)? by mutableStateOf(null) }
class MobileTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MobileTestContent.content?.invoke() }
    }
}
