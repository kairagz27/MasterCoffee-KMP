package kz.master_coffee.app

import androidx.compose.runtime.Composable

// Говорим общему коду, что у нас будет менеджер для шаринга
expect class ShareManager {
    fun shareText(text: String)
}

// Говорим интерфейсу, что мы сможем этот менеджер получить
@Composable
expect fun rememberShareManager(): ShareManager