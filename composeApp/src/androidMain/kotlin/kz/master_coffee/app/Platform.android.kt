package kz.master_coffee.app
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}



actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentDateTime(): String {
    // Форматируем красиво: День.Месяц.Год Час:Минута
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date())
}

@Composable
actual fun MasterBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // На Андроиде используем родной системный перехватчик
    BackHandler(enabled = enabled, onBack = onBack)
}