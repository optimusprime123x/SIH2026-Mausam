package dev.mausam.home.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mausam.home.AppGraph
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.WeatherBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import dev.mausam.home.domain.model.Units
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocationRow(val location: Location, val bundle: CachedResult<WeatherBundle>?, val isPrimary: Boolean)

class LocationsViewModel(private val graph: AppGraph) : ViewModel() {
    private val repo = graph.repository
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Location>>(emptyList())
    val locating = MutableStateFlow(false)
    val units: StateFlow<Units> = repo.settings.map { it.units }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Units.METRIC)

    val rows: StateFlow<List<LocationRow>> = repo.locations.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(combine(list.map { loc -> repo.bundle(loc).map { loc to it } }) { it.toList() }, repo.primaryLocationId) { pairs, primaryId ->
            val effective = primaryId ?: list.firstOrNull()?.id
            pairs.map { (loc, b) -> LocationRow(loc, b, loc.id == effective) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQuery(q: String) { query.value = q; results.value = graph.stations.search(q) }

    fun add(location: Location) = viewModelScope.launch {
        repo.addLocation(location)
        query.value = ""; results.value = emptyList()
        runCatching { repo.refresh(location) }
    }

    fun addDeviceLocation() = viewModelScope.launch {
        locating.value = true
        val loc = runCatching { graph.deviceLocation.current() }.getOrNull()
        locating.value = false
        if (loc != null) add(loc)
    }

    fun setPrimary(id: String) = viewModelScope.launch { repo.setPrimary(id) }
    fun remove(id: String) = viewModelScope.launch { repo.removeLocation(id) }

    fun move(id: String, delta: Int) = viewModelScope.launch {
        val ids = repo.currentLocations().map { it.id }.toMutableList()
        val i = ids.indexOf(id); val j = i + delta
        if (i < 0 || j < 0 || j >= ids.size) return@launch
        ids[i] = ids[j].also { ids[j] = ids[i] }
        repo.reorderLocations(ids)
    }
}
