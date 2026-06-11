package kz.master_coffee.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

// Реализация для iOS: используем UIActivityViewController
actual class ShareManager {
    actual fun shareText(text: String) {
        val currentViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (currentViewController != null) {
            val activityViewController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            currentViewController.presentViewController(activityViewController, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberShareManager(): ShareManager {
    return remember { ShareManager() }
}