package dev.mausam.home.data.fake

import dev.mausam.home.domain.HomeRepository
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardPref
import dev.mausam.home.domain.cards.CardUsage
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.geo.Coastline
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.WeatherBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Pure in-memory implementation of [HomeRepository]. Used by Compose previews and tests, and as
 * the placeholder graph until the network-backed repository is wired. Bundles can be seeded.
 */
class InMemoryHomeRepository(
    initialLocations: List<Location> = emptyList(),
    initialSettings: UserSettings = UserSettings(),
    private val seed: (Location) -> WeatherBundle? = { null },
) : HomeRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)
    private val locationsFlow = MutableStateFlow(initialLocations)
    private val primaryId = MutableStateFlow(initialLocations.firstOrNull()?.id)
    private val bundles = MutableStateFlow<Map<String, CachedResult<WeatherBundle>>>(emptyMap())
    private val usage = MutableStateFlow<Map<String, CardUsage>>(emptyMap())
    private val prefs = MutableStateFlow<Map<String, CardPref>>(emptyMap())
    private val notified = MutableStateFlow<Set<String>>(emptySet())
    private val onboardedFlow = MutableStateFlow(false)
    private val dismissed = MutableStateFlow<String?>(null)

    override val settings: Flow<UserSettings> = settingsFlow
    override suspend fun currentSettings(): UserSettings = settingsFlow.value
    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) = settingsFlow.update(transform)

    override val locations: Flow<List<Location>> = locationsFlow
    override suspend fun currentLocations(): List<Location> = locationsFlow.value
    override suspend fun primaryLocation(): Location? =
        locationsFlow.value.firstOrNull { it.id == primaryId.value } ?: locationsFlow.value.firstOrNull()
    override suspend fun setPrimary(locationId: String) { primaryId.value = locationId }
    override suspend fun addLocation(location: Location) {
        locationsFlow.update { list -> if (list.any { it.id == location.id }) list else list + location }
        if (primaryId.value == null) primaryId.value = location.id
    }
    override suspend fun removeLocation(locationId: String) {
        locationsFlow.update { list -> list.filterNot { it.id == locationId } }
        if (primaryId.value == locationId) primaryId.value = locationsFlow.value.firstOrNull()?.id
    }
    override suspend fun reorderLocations(ids: List<String>) {
        locationsFlow.update { list -> ids.mapNotNull { id -> list.firstOrNull { it.id == id } } + list.filter { it.id !in ids } }
    }

    override fun bundle(location: Location): Flow<CachedResult<WeatherBundle>?> = bundles.map { it[location.id] }
    override suspend fun cachedBundle(location: Location): CachedResult<WeatherBundle>? = bundles.value[location.id]
    override suspend fun refresh(location: Location): CachedResult<WeatherBundle> {
        val now = Instant.now()
        val data = seed(location) ?: bundles.value[location.id]?.data ?: WeatherBundle.empty(location, now)
        val result = CachedResult(data, now, isStale = false, origin = CachedResult.Origin.CACHE)
        bundles.update { it + (location.id to result) }
        return result
    }

    override suspend fun buildContext(location: Location, refresh: Boolean): CardContext? {
        val cached = if (refresh) refresh(location) else cachedBundle(location) ?: refresh(location)
        val now = ZonedDateTime.now(location.zoneId)
        val others = locationsFlow.value.filter { it.id != location.id }.mapNotNull { bundles.value[it.id]?.data }
        return CardContext(
            now = now, location = location, bundle = cached.data,
            distanceToCoastKm = Coastline.distanceKm(location.latitude, location.longitude),
            settings = settingsFlow.value, destinations = others,
        )
    }

    override val cardUsage: Flow<Map<String, CardUsage>> = usage
    override val cardPrefs: Flow<Map<String, CardPref>> = prefs
    override suspend fun recordTap(cardId: String) {
        val now = Instant.now()
        usage.update { m -> m + (cardId to (m[cardId] ?: CardUsage(cardId, 0.0, now)).tapped(now)) }
    }
    override suspend fun updatePref(cardId: String, transform: (CardPref) -> CardPref) {
        prefs.update { m -> m + (cardId to transform(m[cardId] ?: CardPref(cardId))) }
    }

    override val dismissedBanner: Flow<String?> = dismissed
    override suspend fun dismissBanner(warningId: String?) { dismissed.value = warningId }
    override suspend fun notifiedWarningIds(): Set<String> = notified.value
    override suspend fun markWarningsNotified(ids: Set<String>) = notified.update { it + ids }

    override val onboarded: Flow<Boolean> = onboardedFlow
    override suspend fun setOnboarded(done: Boolean) { onboardedFlow.value = done }

    /** Marks everything stale, e.g. to preview the offline state. */
    fun ageCache(by: Duration) {
        bundles.update { m -> m.mapValues { (_, v) -> v.copy(fetchedAt = v.fetchedAt.minus(by), isStale = by > Freshness.STALE_AFTER) } }
    }
}
