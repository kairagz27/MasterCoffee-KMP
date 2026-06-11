package kz.master_coffee.app

import androidx.compose.runtime.Composable

// Говорим общему коду, что у нас будет менеджер для открытия настроек
expect class SettingsManager {
    fun openAppSettings()
}

@Composable
expect fun rememberSettingsManager(): SettingsManager