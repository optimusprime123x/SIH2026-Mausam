package dev.mausam.home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mausam.home.AppGraph
import dev.mausam.home.domain.briefs.BriefComposer
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.personas.Persona
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val repo = graph.repository
    val settings: StateFlow<UserSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch {
        repo.updateSettings(transform)
        graph.scheduler.scheduleBriefs(repo.currentSettings())
    }

    /** Demo hook: posts the morning brief for the first selected persona right now. */
    fun previewBrief(evening: Boolean) = viewModelScope.launch {
        val loc = repo.primaryLocation() ?: return@launch
        val ctx = repo.buildContext(loc, refresh = false) ?: return@launch
        val s = repo.currentSettings()
        val persona = s.personas.firstOrNull { it != Persona.GENERAL } ?: Persona.GENERAL
        graph.notifier.postBrief(BriefComposer.compose(persona, ctx, evening))
    }
}
