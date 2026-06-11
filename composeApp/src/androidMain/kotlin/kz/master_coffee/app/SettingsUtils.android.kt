package kz.master_coffee.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class SettingsManager(private val context: Context) {
    actual fun openAppSettings() {
        // Пытаемся открыть сразу настройки уведомлений нашего приложения
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Если Android старый и не знает про меню уведомлений, открываем просто настройки приложения
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}

@Composable
actual fun rememberSettingsManager(): SettingsManager {
    val context = LocalContext.current
    return remember { SettingsManager(context) }
}