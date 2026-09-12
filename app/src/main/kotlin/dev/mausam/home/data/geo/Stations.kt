package dev.mausam.home.data.geo

import android.content.Context
import dev.mausam.home.data.net.Http
import dev.mausam.home.domain.geo.Geo
import dev.mausam.home.domain.model.Location
import kotlinx.serialization.Serializable
import java.time.ZoneId

/**
 * Bundled `assets/stations.json`: IMD SYNOP stations (ids match IMD's published station ids,
 * 42182 = New Delhi-Safdarjung), METAR airports, and district centroids with state and
 * subdivision. Lets any lat/lon resolve to the three keys the IMD layers are filtered on,
 * entirely offline.
 */
@Serializable
data class StationEntry(val id: String, val name: String, val lat: Double, val lon: Double)

@Serializable
data class DistrictEntry(
    val name: String,
    val state: String,
    val subdiv: String? = null,
    val lat: Double,
    val lon: Double,
    val code: Long? = null,
)

@Serializable
data class StationsFile(
    val stations: List<StationEntry> = emptyList(),
    val metar: List<StationEntry> = emptyList(),
    val districts: List<DistrictEntry> = emptyList(),
)

data class Resolved(val station: StationEntry?, val metar: StationEntry?, val district: DistrictEntry?)

class Stations private constructor(val file: StationsFile) {
    companion object {
        const val DEFAULT_STATION_ID = "42182"
        const val ASSET = "stations.json"

        fun load(context: Context): Stations {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            return Stations(Http.json.decodeFromString(StationsFile.serializer(), text))
        }

        fun of(file: StationsFile) = Stations(file)
    }

    fun nearestStation(lat: Double, lon: Double, maxKm: Double = 150.0): StationEntry? =
        file.stations.minByOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }
            ?.takeIf { Geo.haversineKm(lat, lon, it.lat, it.lon) <= maxKm }

    fun nearestMetar(lat: Double, lon: Double, maxKm: Double = 120.0): StationEntry? =
        file.metar.minByOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }
            ?.takeIf { Geo.haversineKm(lat, lon, it.lat, it.lon) <= maxKm }

    fun nearestDistrict(lat: Double, lon: Double): DistrictEntry? =
        file.districts.minByOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }

    fun resolve(lat: Double, lon: Double): Resolved =
        Resolved(nearestStation(lat, lon), nearestMetar(lat, lon), nearestDistrict(lat, lon))

    /** Fills the IMD keys on a location the user picked by coordinates or search. */
    fun enrich(location: Location): Location {
        val r = resolve(location.latitude, location.longitude)
        return location.copy(
            imdStationId = location.imdStationId ?: r.station?.id,
            district = location.district ?: r.district?.name,
            state = location.state ?: r.district?.state,
            region = location.region ?: r.district?.state?.let(::titleCase),
        )
    }

    /** Offline place search over station names and district names. */
    fun search(query: String, limit: Int = 12): List<Location> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val fromStations = file.stations.filter { it.name.lowercase().contains(q) }.map { s ->
            val d = nearestDistrict(s.lat, s.lon)
            Location(
                id = "st-${s.id}", name = cleanStationName(s.name), latitude = s.lat, longitude = s.lon,
                region = d?.state?.let(::titleCase), imdStationId = s.id, district = d?.name, state = d?.state,
            )
        }
        val fromDistricts = file.districts.filter { it.name.lowercase().contains(q) }.map { d ->
            val s = nearestStation(d.lat, d.lon)
            Location(
                id = "dt-${d.name.lowercase().replace(' ', '-')}-${d.state.lowercase().replace(' ', '-')}",
                name = titleCase(d.name), latitude = d.lat, longitude = d.lon,
                region = titleCase(d.state), imdStationId = s?.id, district = d.name, state = d.state,
            )
        }
        return (fromStations + fromDistricts)
            .sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
            .distinctBy { it.name.lowercase() to it.region?.lowercase() }
            .take(limit)
    }

    fun defaultLocation(): Location {
        val s = file.stations.firstOrNull { it.id == DEFAULT_STATION_ID }
        return if (s != null) {
            val d = nearestDistrict(s.lat, s.lon)
            Location("st-${s.id}", "New Delhi", s.lat, s.lon, region = "Delhi", zoneId = ZoneId.of("Asia/Kolkata"),
                imdStationId = s.id, district = d?.name ?: "NEW DELHI", state = d?.state ?: "DELHI")
        } else Location("st-42182", "New Delhi", 28.58, 77.20, region = "Delhi", imdStationId = DEFAULT_STATION_ID, district = "NEW DELHI", state = "DELHI")
    }

    private fun cleanStationName(n: String): String = n.substringBefore('-').substringBefore('/').trim().let(::titleCase)

    private fun titleCase(s: String): String = s.lowercase().split(' ').joinToString(" ") { w ->
        if (w.length <= 2 && w != "of") w.uppercase() else w.replaceFirstChar { it.uppercase() }
    }.replace(" And ", " and ").replace(" Of ", " of ")
}
