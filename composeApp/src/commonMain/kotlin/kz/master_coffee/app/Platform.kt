package kz.master_coffee.app
import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
// Говорим общему коду: "Ожидай, что каждая платформа даст тебе функцию для времени"
expect fun getCurrentDateTime(): String

@Composable
expect fun MasterBackHandler(enabled: Boolean, onBack: () -> Unit)