package dev.mausam.home.data.snapshots

import android.content.Context
import dev.mausam.home.data.net.Http
import dev.mausam.home.domain.geo.Geo
import kotlinx.serialization.Serializable

/**
 * Raw JSON captured from every live endpoint, bundled under `assets/snapshots/`. IMD layers are
 * national (one file covers every district and station); Open-Meteo files are per city and the
 * nearest city is used. The repository falls back here on any network failure so a demo cannot
 * die on a bad endpoint. Snapshot-backed data is always flagged so the UI can say so.
 */
@Serializable
data class SnapshotCity(val key: String, val name: String, val lat: Double, val lon: Double, val marine: Boolean = false)

@Serializable
data class SnapshotIndex(val capturedAt: String, val cities: List<SnapshotCity>)

class SnapshotSource(private val context: Context) {
    private val index: SnapshotIndex by lazy {
        runCatching {
            Http.json.decodeFromString(SnapshotIndex.serializer(), read("snapshots/index.json")!!)
        }.getOrElse { SnapshotIndex("unknown", emptyList()) }
    }

    val capturedAt: String get() = index.capturedAt

    private fun read(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()

    fun national(name: String): String? = read("snapshots/$name.json")

    fun nearestCity(lat: Double, lon: Double, needMarine: Boolean = false): SnapshotCity? =
        index.cities.filter { !needMarine || it.marine }.minByOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }

    fun perCity(prefix: String, lat: Double, lon: Double, needMarine: Boolean = false): String? {
        val city = nearestCity(lat, lon, needMarine) ?: return null
        return read("snapshots/${prefix}_${city.key}.json")
    }
}
