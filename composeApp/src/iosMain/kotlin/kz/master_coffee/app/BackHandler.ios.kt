package kz.master_coffee.app
import androidx.compose.runtime.Composable
// UIKit hosts have no Android system Back button; the common toolbar provides navigation.
@Composable actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
