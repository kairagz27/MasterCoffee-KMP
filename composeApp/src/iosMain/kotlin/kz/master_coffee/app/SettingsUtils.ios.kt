package kz.master_coffee.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual class SettingsManager {
    actual fun openAppSettings() {
        // На iOS это открывает системные настройки текущего приложения
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null && UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url)
        }
    }
}

@Composable
actual fun rememberSettingsManager(): SettingsManager {
    return remember { SettingsManager() }
}