package dev.mausam.home.data

import dev.mausam.home.BuildConfig
import dev.mausam.home.data.cache.CardPrefEntity
import dev.mausam.home.data.cache.CardUsageEntity
import dev.mausam.home.data.cache.LocationEntity
import dev.mausam.home.data.cache.MausamDatabase
import dev.mausam.home.data.cache.RawPayloadEntity
import dev.mausam.home.data.cpcb.CpcbApi
import dev.mausam.home.data.geo.Stations
import dev.mausam.home.data.imd.DistrictResolver
import dev.mausam.home.data.imd.ImdWfsApi
import dev.mausam.home.data.ndma.SachetApi
import dev.mausam.home.data.net.Http
import dev.mausam.home.data.openmeteo.OpenMeteoApi
import dev.mausam.home.data.prefs.SettingsStore
import dev.mausam.home.data.snapshots.SnapshotSource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Network-backed [HomeRepository]. Raw payloads are cached per (source, location) in Room and
 * re-assembled on read, so cache, network and snapshot all flow through the same normaliser.
 * Fallback chain per source: network → existing cache → bundled snapshot.
 */
class HomeRepositoryImpl(
    private val db: MausamDatabase,
    private val settingsStore: SettingsStore,
    private val imd: ImdWfsApi,
    private val sachet: SachetApi,
    private val openMeteo: OpenMeteoApi,
    private val cpcb: CpcbApi,
    private val snapshots: SnapshotSource,
    private val stations: Stations,
    private val assembler: WeatherAssembler = WeatherAssembler(),
    private val clock: () -> Instant = { Instant.now() },
) : HomeRepository {
    private val districts = DistrictResolver(imd)
    private val refreshLocks = mutableMapOf<String, Mutex>()
    private fun lockFor(id: String) = synchronized(refreshLocks) { refreshLocks.getOrPut(id) { Mutex() } }

    // ---------------------------------------------------------------- settings
    override val settings: Flow<UserSettings> get() = settingsStore.settings
    override suspend fun currentSettings(): UserSettings = settingsStore.current()
    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) = settingsStore.update(transform)
    override val onboarded: Flow<Boolean> get() = settingsStore.onboarded
    override suspend fun setOnboarded(done: Boolean) = settingsStore.setOnboarded(done)

    // ---------------------------------------------------------------- locations
    override val locations: Flow<List<Location>> = db.locations().observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun currentLocations(): List<Location> = db.locations().getAll().map { it.toDomain() }
    override suspend fun primaryLocation(): Location? {
        val all = currentLocations()
        val id = settingsStore.primaryLocationId.first()
        return all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }
    override val primaryLocationId: Flow<String?> get() = settingsStore.primaryLocationId
    override suspend fun setPrimary(locationId: String) = settingsStore.setPrimaryLocation(locationId)
    override suspend fun addLocation(location: Location) {
        val enriched = stations.enrich(location)
        val existing = db.locations().get(enriched.id)
        db.locations().upsert(enriched.toEntity(existing?.sortOrder ?: db.locations().nextOrder()))
        if (settingsStore.primaryLocationId.first() == null) settingsStore.setPrimaryLocation(enriched.id)
    }
    override suspend fun removeLocation(locationId: String) {
        db.locations().delete(locationId)
        db.rawPayloads().deleteFor(locationId)
        if (settingsStore.primaryLocationId.first() == locationId) {
            db.locations().getAll().firstOrNull()?.let { settingsStore.setPrimaryLocation(it.id) }
        }
    }
    override suspend fun reorderLocations(ids: List<String>) {
        ids.forEachIndexed { i, id -> db.locations().setOrder(id, i) }
    }

    // ---------------------------------------------------------------- bundles
    // Assembly decodes several hundred KB of JSON, so it never runs on the collector's (main) thread.
    override fun bundle(location: Location): Flow<CachedResult<WeatherBundle>?> =
        db.rawPayloads().observeFor(location.id)
            .map { rows -> rows.takeIf { it.isNotEmpty() }?.let { assembleRows(location, it) } }
            .flowOn(Dispatchers.Default)

    override suspend fun cachedBundle(location: Location): CachedResult<WeatherBundle>? = withContext(Dispatchers.Default) {
        db.rawPayloads().getFor(location.id).takeIf { it.isNotEmpty() }?.let { assembleRows(location, it) }
    }

    private fun assembleRows(location: Location, rows: List<RawPayloadEntity>): CachedResult<WeatherBundle> {
        val now = clock()
        val raw = rows.mapNotNull { r -> SourceKey.fromId(r.source)?.let { it to RawPayload(r.json, Instant.ofEpochMilli(r.fetchedAt), r.fromSnapshot) } }.toMap()
        val bundle = assembler.assemble(location, raw, now)
        val newest = raw.values.filter { !it.fromSnapshot }.maxOfOrNull { it.fetchedAt }
        val origin = when {
            newest == null -> CachedResult.Origin.SNAPSHOT
            Duration.between(newest, now) < Duration.ofMinutes(2) -> CachedResult.Origin.NETWORK
            else -> CachedResult.Origin.CACHE
        }
        val fetchedAt = newest ?: raw.values.maxOfOrNull { it.fetchedAt } ?: now
        return CachedResult(bundle, fetchedAt, Freshness.of(fetchedAt, now, location.zoneId).isStale, origin)
    }

    override suspend fun refresh(location: Location): CachedResult<WeatherBundle> = lockFor(location.id).withLock {
        withContext(Dispatchers.IO) {
            val loc = if (location.district == null || location.imdStationId == null) stations.enrich(location) else location
            coroutineScope {
                val jobs = listOf(
                    async { fetch(SourceKey.OM_FORECAST, loc) { Http.json.encodeToString(dev.mausam.home.data.openmeteo.OmForecast.serializer(), openMeteo.forecast(loc.latitude, loc.longitude)) } },
                    async { fetch(SourceKey.OM_AQI, loc) { Http.json.encodeToString(dev.mausam.home.data.openmeteo.OmAirQuality.serializer(), openMeteo.airQuality(loc.latitude, loc.longitude)) } },
                    async {
                        if (Coastline.isCoastal(loc.latitude, loc.longitude, 60.0)) {
                            val (lat, lon) = offshorePoint(loc)
                            fetch(SourceKey.OM_MARINE, loc) { Http.json.encodeToString(dev.mausam.home.data.openmeteo.OmMarine.serializer(), openMeteo.marine(lat, lon)) }
                        } else null
                    },
                    async {
                        loc.imdStationId?.let { id -> fetch(SourceKey.IMD_SYNOP, loc) { Http.json.encodeToString(JsonObject.serializer(), imd.getFeature(ImdWfsApi.LAYER_SYNOP, ImdWfsApi.synopFilter(id))) } }
                    },
                    async {
                        stations.nearestMetar(loc.latitude, loc.longitude)?.let { m ->
                            fetch(SourceKey.IMD_METAR, loc) { Http.json.encodeToString(JsonObject.serializer(), imd.getFeature(ImdWfsApi.LAYER_METAR, ImdWfsApi.metarFilter(m.id))) }
                        }
                    },
                    async {
                        fetch(SourceKey.IMD_WARNINGS, loc) {
                            // By geography first (IMD's district names do not match ours everywhere), by name as a fallback.
                            val resolved = districts.warningsFor(loc)
                            if (resolved != null) {
                                val name = resolved.districtName
                                if (name != null && !name.equals(loc.district, ignoreCase = true)) db.locations().setDistrict(loc.id, name)
                                Http.json.encodeToString(JsonObject.serializer(), resolved.collection)
                            } else {
                                val d = loc.district ?: error("no district")
                                Http.json.encodeToString(JsonObject.serializer(), imd.getFeature(ImdWfsApi.LAYER_DISTRICT_WARNINGS, ImdWfsApi.districtFilter(d), ImdWfsApi.PROPS_DISTRICT_WARNINGS))
                            }
                        }
                    },
                    async {
                        loc.district?.let { d ->
                            fetch(SourceKey.IMD_NOWCAST, loc) {
                                Http.json.encodeToString(JsonObject.serializer(), imd.getFeature(ImdWfsApi.LAYER_DISTRICT_NOWCAST, ImdWfsApi.districtFilter(d), ImdWfsApi.PROPS_DISTRICT_NOWCAST))
                            }
                        }
                    },
                    async { fetchNational(SourceKey.SACHET) { Http.json.encodeToString(JsonArray.serializer(), sachet.allAlerts()) } },
                    async {
                        fetch(SourceKey.CPCB, loc) {
                            val r = cpcb.stations(BuildConfig.CPCB_API_KEY, state = loc.state?.let(::cpcbStateName))
                            if (r.records.isEmpty()) error("empty") else Http.json.encodeToString(dev.mausam.home.data.cpcb.CpcbResponse.serializer(), r)
                        }
                    },
                )
                val outcomes: List<Boolean?> = jobs.map { it.await() }
                // Every source failed: the caller learns it, whatever the cache still holds.
                if (outcomes.none { it == true }) throw RefreshFailedException()
            }
            cachedBundle(loc) ?: CachedResult(WeatherBundle.empty(loc, clock()), clock(), true, CachedResult.Origin.CACHE)
        }
    }

    /**
     * Network first; on failure keep the existing cache, else seed from the bundled snapshot,
     * stamped with the snapshot's capture time so "as of" never claims it is fresh. Returns
     * whether the network call succeeded.
     */
    private suspend fun fetch(key: SourceKey, loc: Location, call: suspend () -> String): Boolean {
        val entityKey = RawPayloadEntity.key(key.id, loc.id)
        val result = runCatching { call() }
        if (result.isSuccess) {
            db.rawPayloads().upsert(RawPayloadEntity(entityKey, key.id, loc.id, result.getOrThrow(), clock().toEpochMilli(), false))
            return true
        }
        if (db.rawPayloads().get(entityKey) != null) return false
        snapshotFor(key, loc)?.let { json ->
            db.rawPayloads().upsert(RawPayloadEntity(entityKey, key.id, loc.id, json, snapshotStamp(), true))
        }
        return false
    }

    private suspend fun fetchNational(key: SourceKey, call: suspend () -> String): Boolean {
        val entityKey = RawPayloadEntity.key(key.id, "*")
        val result = runCatching { call() }
        if (result.isSuccess) {
            db.rawPayloads().upsert(RawPayloadEntity(entityKey, key.id, "*", result.getOrThrow(), clock().toEpochMilli(), false))
            return true
        }
        // Real data, however old, beats the bundled snapshot: seed only when there is nothing.
        if (db.rawPayloads().get(entityKey) != null) return false
        snapshots.national(key.id)?.let { db.rawPayloads().upsert(RawPayloadEntity(entityKey, key.id, "*", it, snapshotStamp(), true)) }
        return false
    }

    /** Snapshot rows carry the capture time, so freshness is honest about bundled data. */
    private fun snapshotStamp(): Long = runCatching { Instant.parse(snapshots.capturedAt).toEpochMilli() }.getOrElse { clock().toEpochMilli() }

    private fun snapshotFor(key: SourceKey, loc: Location): String? = when (key) {
        SourceKey.IMD_WARNINGS, SourceKey.IMD_NOWCAST, SourceKey.IMD_SYNOP, SourceKey.IMD_METAR, SourceKey.SACHET -> snapshots.national(key.id)
        SourceKey.OM_FORECAST -> snapshots.perCity("om_forecast", loc.latitude, loc.longitude)
        SourceKey.OM_AQI -> snapshots.perCity("om_aqi", loc.latitude, loc.longitude)
        SourceKey.OM_MARINE -> snapshots.perCity("om_marine", loc.latitude, loc.longitude, needMarine = true)
        SourceKey.CPCB -> null
    }

    /** Marine models return nulls on land, so nudge a coastal city toward the nearest coastline point. */
    private fun offshorePoint(loc: Location): Pair<Double, Double> {
        val nearest = Coastline.points.minBy { (lat, lon) -> dev.mausam.home.domain.geo.Geo.haversineKm(loc.latitude, loc.longitude, lat, lon) }
        // Step 0.15° (~16 km) past the coastline point, away from the city.
        val dLat = nearest.first - loc.latitude
        val dLon = nearest.second - loc.longitude
        val len = kotlin.math.sqrt(dLat * dLat + dLon * dLon).takeIf { it > 1e-6 } ?: return nearest.first to (nearest.second - 0.2)
        return (nearest.first + dLat / len * 0.15) to (nearest.second + dLon / len * 0.15)
    }

    private fun cpcbStateName(s: String): String = s.lowercase().split(' ').joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }

    override suspend fun buildContext(location: Location, refresh: Boolean): CardContext? {
        val cached = if (refresh) refresh(location) else (cachedBundle(location) ?: refresh(location))
        val now = ZonedDateTime.ofInstant(clock(), location.zoneId)
        val others = currentLocations().filter { it.id != location.id }.mapNotNull { cachedBundle(it)?.data }
        return CardContext(
            now = now, location = location, bundle = cached.data,
            distanceToCoastKm = Coastline.distanceKm(location.latitude, location.longitude),
            settings = settingsStore.current(), destinations = others,
        )
    }

    // ---------------------------------------------------------------- cards
    override val cardUsage: Flow<Map<String, CardUsage>> =
        db.cardUsage().observeAll().map { list -> list.associate { it.cardId to CardUsage(it.cardId, it.score, Instant.ofEpochMilli(it.updatedAt)) } }
    override val cardPrefs: Flow<Map<String, CardPref>> =
        db.cardPrefs().observeAll().map { list -> list.associate { it.cardId to it.toDomain() } }

    override suspend fun recordTap(cardId: String) {
        val now = clock()
        val current = db.cardUsage().get(cardId)?.let { CardUsage(it.cardId, it.score, Instant.ofEpochMilli(it.updatedAt)) } ?: CardUsage(cardId, 0.0, now)
        val next = current.tapped(now)
        db.cardUsage().upsert(CardUsageEntity(cardId, next.score, next.updatedAt.toEpochMilli()))
    }

    override suspend fun updatePref(cardId: String, transform: (CardPref) -> CardPref) {
        val current = db.cardPrefs().get(cardId)?.toDomain() ?: CardPref(cardId)
        val next = transform(current)
        db.cardPrefs().upsert(CardPrefEntity(cardId, next.pinnedAt?.toEpochMilli(), next.hidden, next.boostUntil?.toEpochMilli(), next.added))
    }

    override val dismissedBanner: Flow<String?> get() = settingsStore.dismissedBanner
    override suspend fun dismissBanner(warningId: String?) = settingsStore.dismissBanner(warningId)
    override suspend fun notifiedWarningIds(): Set<String> = settingsStore.notifiedWarnings()
    override suspend fun markWarningsNotified(ids: Set<String>) = settingsStore.markNotified(ids)

    // ---------------------------------------------------------------- mapping
    private fun LocationEntity.toDomain() = Location(
        id = id, name = name, latitude = latitude, longitude = longitude, region = region,
        zoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("Asia/Kolkata")),
        imdStationId = stationId, district = district, state = state,
    )

    private fun Location.toEntity(order: Int) = LocationEntity(
        id = id, name = name, region = region, latitude = latitude, longitude = longitude, zoneId = zoneId.id,
        stationId = imdStationId, district = district, state = state, sortOrder = order,
    )

    private fun CardPrefEntity.toDomain() = CardPref(
        cardId, pinnedAt?.let(Instant::ofEpochMilli), hidden, boostUntil?.let(Instant::ofEpochMilli), added,
    )
}

/** Thrown by [HomeRepository.refresh] when no source could be reached; cached data stays in place. */
class RefreshFailedException : java.io.IOException("No weather source could be reached")
