package dev.mausam.home.data.imd

import dev.mausam.home.data.net.arr
import dev.mausam.home.data.net.obj
import dev.mausam.home.data.net.str
import dev.mausam.home.domain.geo.Geo
import dev.mausam.home.domain.model.Location
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.put

/**
 * Finds the IMD district warnings row for a point, by geography rather than by name.
 *
 * IMD's layer keys districts by name only, with its own spellings for same-name districts
 * (BALRAMPUR-UP / BALRAMPUR-CG, HAMIRPUR / HAMIRPURR), an empty state column, and a declared
 * CRS that makes server-side spatial predicates fail. So: ask for every district whose envelope
 * covers the point (one small request, no geometry). One candidate, or one that agrees with the
 * name we already hold, settles it. Otherwise fetch just those candidates' polygons and test the
 * point against them on the device. The result is the row as a one-feature collection without
 * geometry, plus IMD's spelling of the district so later lookups can use the fast name path.
 */
class DistrictResolver(private val imd: ImdWfsApi) {

    class Resolved(val collection: JsonObject, val districtName: String?)

    suspend fun warningsFor(loc: Location): Resolved? {
        val bbox = ImdWfsApi.bboxAround(loc.latitude, loc.longitude)
        val candidates = imd.getFeature(ImdWfsApi.LAYER_DISTRICT_WARNINGS, properties = ImdWfsApi.PROPS_DISTRICT_WARNINGS, bbox = bbox)
            .features().filter { it.districtName() != null }
        val chosen: JsonObject? = when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first()
            else -> candidates.firstOrNull { it.districtName().equals(loc.district, ignoreCase = true) }
                ?: resolveByPolygon(loc, candidates.mapNotNull { it.districtName() })
        }
        val feature = chosen ?: return null
        return Resolved(singleCollection(feature), feature.districtName())
    }

    /** GeoServer refuses bbox and CQL together, so the polygon fetch is by the candidate names alone (about 5 KB each). */
    private suspend fun resolveByPolygon(loc: Location, names: List<String>): JsonObject? {
        val withGeom = imd.getFeature(
            ImdWfsApi.LAYER_DISTRICT_WARNINGS,
            cql = ImdWfsApi.districtsFilter(names),
            properties = ImdWfsApi.PROPS_DISTRICT_WARNINGS + ",geom",
        ).features()
        return withGeom.firstOrNull { f -> f.obj("geometry")?.let { Geo.pointInPolygon(loc.latitude, loc.longitude, rings(it)) } == true }
    }

    private fun JsonObject.features(): List<JsonObject> = arr("features")?.mapNotNull { it as? JsonObject } ?: emptyList()
    private fun JsonObject.districtName(): String? = obj("properties")?.str("District")

    /** Drops the geometry: a district polygon is hundreds of KB the cache never needs. */
    private fun singleCollection(feature: JsonObject): JsonObject = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(listOf(buildJsonObject {
            put("type", "Feature")
            feature["id"]?.let { put("id", it) }
            feature["properties"]?.let { put("properties", it) }
        })))
    }

    companion object {
        /** GeoJSON Polygon or MultiPolygon → rings of lon/lat pairs (all polygons flattened). */
        fun rings(geometry: JsonObject): List<List<DoubleArray>> {
            val coords = geometry.arr("coordinates") ?: return emptyList()
            fun ring(a: JsonArray): List<DoubleArray> = a.mapNotNull { p ->
                val pt = p as? JsonArray ?: return@mapNotNull null
                val x = (pt.getOrNull(0) as? JsonPrimitive)?.double ?: return@mapNotNull null
                val y = (pt.getOrNull(1) as? JsonPrimitive)?.double ?: return@mapNotNull null
                doubleArrayOf(x, y)
            }
            return when (geometry.str("type")) {
                "Polygon" -> coords.mapNotNull { (it as? JsonArray)?.let(::ring) }
                "MultiPolygon" -> coords.flatMap { poly -> (poly as? JsonArray)?.mapNotNull { (it as? JsonArray)?.let(::ring) } ?: emptyList() }
                else -> emptyList()
            }
        }
    }
}
