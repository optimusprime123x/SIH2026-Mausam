package dev.mausam.home.data.imd

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * IMD's own GeoServer (OGC WFS 2.0). No key, no whitelist: it is what mausam.imd.gov.in's public
 * map pages call from the browser. Geometry is never requested on mobile; a filtered district
 * row is about 600 bytes.
 */
interface ImdWfsApi {
    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&outputFormat=application/json")
    suspend fun getFeature(
        @Query("typeNames") layer: String,
        @Query("CQL_FILTER") cql: String? = null,
        @Query("propertyName") properties: String? = null,
        @Query("count") count: Int? = null,
        /** `minLon,minLat,maxLon,maxLat,EPSG:4326`; matches feature envelopes, see [bboxAround]. */
        @Query("bbox") bbox: String? = null,
    ): JsonObject

    companion object {
        const val BASE_URL = "https://reactjs.imd.gov.in/geoserver/imd/"

        const val LAYER_DISTRICT_WARNINGS = "imd:district_warnings_india"
        const val LAYER_DISTRICT_NOWCAST = "imd:NowcastWarningDistrict"
        const val LAYER_SYNOP = "imd:synop_data_layer"
        const val LAYER_METAR = "imd:metar_data_layer"
        const val LAYER_SUBDIV_RAINFALL = "imd:subdiv_rainfall_now"

        /** Everything except `geom`; polygons would make the response 15× larger. */
        const val PROPS_DISTRICT_WARNINGS =
            "Date,District,Day_1,Day_2,Day_3,Day_4,Day_5,Day1_Color,Day2_Color,Day3_Color,Day4_Color,Day5_Color," +
                "Day1_text,Day2_text,Day3_text,Day4_text,Day5_text,updated_at,state,sub"
        const val PROPS_DISTRICT_NOWCAST =
            "Date,District,State,message,impact,action,toi,vupto,Color,update_time," +
                "cat1,cat2,cat3,cat4,cat5,cat6,cat7,cat8,cat9,cat10,cat11,cat12,cat13,cat14,cat15,cat16,cat17,cat18,cat19"

        fun districtFilter(district: String): String = "District='${district.uppercase().replace("'", "''")}'"
        fun districtsFilter(districts: Collection<String>): String = districts.joinToString(" OR ") { "District='${it.replace("'", "''")}'" }

        /**
         * A tiny envelope around a point. IMD declares the warnings layer in the wrong CRS, so CQL
         * spatial predicates (INTERSECTS, DWITHIN) fail server-side; the WFS bbox parameter still
         * works and returns every district whose envelope covers the point.
         */
        fun bboxAround(lat: Double, lon: Double, halfDeg: Double = 0.002): String =
            String.format(java.util.Locale.ENGLISH, "%.5f,%.5f,%.5f,%.5f,EPSG:4326", lon - halfDeg, lat - halfDeg, lon + halfDeg, lat + halfDeg)
        fun synopFilter(stationId: String): String = "station_id=$stationId"
        fun metarFilter(stationId: String): String = "station_id='${stationId.replace("'", "''")}'"
    }
}
