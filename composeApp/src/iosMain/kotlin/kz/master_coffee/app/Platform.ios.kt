package kz.master_coffee.app
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIDevice
import androidx.compose.runtime.Composable

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getCurrentDateTime(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "dd.MM.yyyy HH:mm"
    return formatter.stringFromDate(NSDate())
}

@Composable
actual fun MasterBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Для iOS пока оставляем пустым, чтобы не было крашей.
    // Нативный iOS-свайп назад реализуется через UINavigationController
    // или внутри самого Compose UI (через detectDragGestures).
}