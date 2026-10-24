package kz.master_coffee.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kz.master_coffee.app.data.ApiRepository
import kz.master_coffee.app.data.createPlatformServices
import kz.master_coffee.app.presentation.CoffeeViewModel
import kz.master_coffee.app.presentation.CoffeeApp

@Composable
fun App() {
    val model = viewModel {
        val platform = createPlatformServices()
        CoffeeViewModel(ApiRepository(platform.engine, platform.apiUrl, platform.allowHttp), platform.tokens, platform.local, apiAddress = if (platform.allowHttp) platform.apiUrl else "")
    }
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.setForeground(false) }
    DisposableEffect(model) { onDispose { model.setForeground(false) } }
    val dark = when (state.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MasterCoffeeTheme(dark) { CoffeeApp(state, model) }
}
