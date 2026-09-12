package dev.mausam.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mausam.home.AppGraph
import dev.mausam.home.MausamApp
import dev.mausam.home.ui.home.HomeViewModel
import dev.mausam.home.ui.locations.LocationsViewModel
import dev.mausam.home.ui.onboarding.OnboardingViewModel
import dev.mausam.home.ui.settings.SettingsViewModel

class GraphViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(graph)
        modelClass.isAssignableFrom(OnboardingViewModel::class.java) -> OnboardingViewModel(graph)
        modelClass.isAssignableFrom(LocationsViewModel::class.java) -> LocationsViewModel(graph)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(graph)
        else -> error("Unknown ViewModel ${modelClass.name}")
    } as T
}

@Composable
inline fun <reified VM : ViewModel> graphViewModel(): VM {
    val graph = MausamApp.graph(LocalContext.current)
    return viewModel(factory = GraphViewModelFactory(graph))
}
