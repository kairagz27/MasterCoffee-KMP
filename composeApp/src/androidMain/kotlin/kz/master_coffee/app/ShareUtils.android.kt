package kz.master_coffee.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Реализация для Android: используем классический Intent
actual class ShareManager(private val context: Context) {
    actual fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Поделиться промокодом")
        shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }
}

// На Андроиде нам нужен Context, чтобы запустить окно
@Composable
actual fun rememberShareManager(): ShareManager {
    val context = LocalContext.current
    return remember { ShareManager(context) }
}