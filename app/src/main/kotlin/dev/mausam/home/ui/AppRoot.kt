package dev.mausam.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mausam.home.AppGraph
import dev.mausam.home.ui.home.HomeScreen
import dev.mausam.home.ui.locations.LocationsScreen
import dev.mausam.home.ui.onboarding.OnboardingScreen
import dev.mausam.home.ui.settings.SettingsScreen
import dev.mausam.home.ui.theme.MausamTheme
import dev.mausam.home.ui.theme.micaBase

enum class Route { HOME, LOCATIONS, SETTINGS }

@Composable
fun AppRoot(graph: AppGraph) {
    val onboarded by graph.repository.onboarded.collectAsState(initial = null)
    val settings by graph.repository.settings.collectAsState(initial = null)
    MausamTheme(settings = settings) {
        when (onboarded) {
            null -> Box(Modifier.fillMaxSize().background(micaBase()))
            false -> OnboardingScreen(vm = graphViewModel(), onDone = {})
            true -> MainNav()
        }
    }
}

@Composable
private fun MainNav() {
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    BackHandler(enabled = route != Route.HOME) { route = Route.HOME }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        route,
        transitionSpec = {
            val forward = targetState != Route.HOME
            if (forward) {
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { -it / 5 } + fadeOut(effects))
            } else {
                (slideInHorizontally(spatial) { -it / 5 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
            }
        },
        label = "nav",
    ) { r ->
        when (r) {
            Route.HOME -> HomeScreen(vm = graphViewModel(), onOpenLocations = { route = Route.LOCATIONS }, onOpenSettings = { route = Route.SETTINGS })
            Route.LOCATIONS -> LocationsScreen(vm = graphViewModel(), onBack = { route = Route.HOME })
            Route.SETTINGS -> SettingsScreen(vm = graphViewModel(), onBack = { route = Route.HOME })
        }
    }
}
