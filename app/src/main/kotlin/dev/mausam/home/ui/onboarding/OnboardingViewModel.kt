package dev.mausam.home.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mausam.home.AppGraph
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.personas.Persona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { LOCATION, PERSONAS }

class OnboardingViewModel(private val graph: AppGraph) : ViewModel() {
    val step = MutableStateFlow(OnboardingStep.LOCATION)
    val locating = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Location>>(emptyList())
    val chosen = MutableStateFlow<Location?>(null)
    val selected = MutableStateFlow<Set<Persona>>(emptySet())
    val error = MutableStateFlow<String?>(null)

    fun onQuery(q: String) {
        query.value = q
        results.value = graph.stations.search(q)
    }

    fun useDeviceLocation() {
        viewModelScope.launch {
            locating.value = true
            val loc = runCatching { graph.deviceLocation.current() }.getOrNull()
            locating.value = false
            if (loc == null) {
                error.value = "Couldn't get a fix. Search for your city instead."
            } else choose(loc)
        }
    }

    fun choose(location: Location) {
        chosen.value = location
        viewModelScope.launch {
            graph.repository.addLocation(location)
            graph.repository.setPrimary(location.id)
            step.value = OnboardingStep.PERSONAS
            launch { runCatching { graph.repository.refresh(location) } }
        }
    }

    fun toggle(p: Persona) {
        selected.value = if (p in selected.value) selected.value - p else selected.value + p
    }

    fun finish(skip: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val personas = if (skip || selected.value.isEmpty()) setOf(Persona.GENERAL) else selected.value + Persona.GENERAL
            graph.repository.updateSettings { it.copy(personas = personas) }
            graph.repository.setOnboarded(true)
            graph.scheduler.scheduleBriefs(graph.repository.currentSettings())
            graph.scheduler.refreshNow()
            onDone()
        }
    }
}
