package dev.mausam.home.domain

import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardPref
import dev.mausam.home.domain.cards.CardUsage
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.WeatherBundle
import kotlinx.coroutines.flow.Flow

/**
 * The contract the UI, workers and widget program against. The data layer implements it;
 * nothing above this line touches Retrofit, Room or DataStore directly.
 */
interface HomeRepository {
    val settings: Flow<UserSettings>
    suspend fun currentSettings(): UserSettings
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)

    val locations: Flow<List<Location>>
    suspend fun currentLocations(): List<Location>
    suspend fun primaryLocation(): Location?
    suspend fun setPrimary(locationId: String)
    suspend fun addLocation(location: Location)
    suspend fun removeLocation(locationId: String)
    suspend fun reorderLocations(ids: List<String>)

    /** Cache-first bundle stream; emits the cached bundle instantly, then refreshed data. */
    fun bundle(location: Location): Flow<CachedResult<WeatherBundle>?>
    suspend fun cachedBundle(location: Location): CachedResult<WeatherBundle>?
    suspend fun refresh(location: Location): CachedResult<WeatherBundle>

    /** Assembles a ready-to-rank context from cache (or after a refresh when requested). */
    suspend fun buildContext(location: Location, refresh: Boolean): CardContext?

    val cardUsage: Flow<Map<String, CardUsage>>
    val cardPrefs: Flow<Map<String, CardPref>>
    suspend fun recordTap(cardId: String)
    suspend fun updatePref(cardId: String, transform: (CardPref) -> CardPref)

    /** The warning id whose home banner was closed by the user, if any. */
    val dismissedBanner: Flow<String?>
    suspend fun dismissBanner(warningId: String?)

    suspend fun notifiedWarningIds(): Set<String>
    suspend fun markWarningsNotified(ids: Set<String>)

    /** True once the persona picker has been completed or skipped. */
    val onboarded: Flow<Boolean>
    suspend fun setOnboarded(done: Boolean)
}
