package dev.mausam.home.data.cpcb

import dev.mausam.home.domain.geo.Geo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CPCB real-time AQI via data.gov.in. One row per pollutant per station; the values are CPCB
 * **sub-indices** (0–500), not concentrations: the CO rows read 50–80, which no µg/m³ or mg/m³
 * reading could be. The overall AQI is the worst sub-index, as CPCB defines it.
 *
 * The key ships in BuildConfig (a data.gov.in key is per-user, free and not secret-grade). The
 * shared sample key is capped at 10 rows per call and rate-limited, so the repository asks for
 * one pollutant at a time and falls back to Open-Meteo's model AQI, marked as an estimate.
 */
interface CpcbApi {
    @GET("resource/3b01bcb8-0b14-4abf-b6f2-c1bfd384ba69?format=json")
    suspend fun stations(
        @Query("api-key") apiKey: String,
        @Query("filters[state]") state: String? = null,
        @Query("filters[city]") city: String? = null,
        @Query("filters[pollutant_id]") pollutant: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): CpcbResponse

    companion object {
        const val BASE_URL = "https://api.data.gov.in/"
        const val ATTRIBUTION = "CPCB via data.gov.in"
    }
}

@Serializable
data class CpcbResponse(
    val total: Int? = null,
    val count: Int? = null,
    @SerialName("updated_date") val updatedDate: String? = null,
    val records: List<CpcbRecord> = emptyList(),
)

/** data.gov.in renamed `pollutant_avg` to `avg_value` in 2026; both spellings are read. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CpcbRecord(
    val state: String? = null,
    val city: String? = null,
    val station: String? = null,
    @SerialName("last_update") val lastUpdate: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    @SerialName("pollutant_id") val pollutantId: String? = null,
    @JsonNames("pollutant_min") @SerialName("min_value") val minValue: String? = null,
    @JsonNames("pollutant_max") @SerialName("max_value") val maxValue: String? = null,
    @JsonNames("pollutant_avg") @SerialName("avg_value") val avgValue: String? = null,
) {
    /** The sub-index, or null for "NA". */
    val subIndex: Int? get() = avgValue?.trim()?.toDoubleOrNull()?.let { it.toInt().coerceIn(0, 500) }
}

/** One station's PM sub-indices and its distance from the user. */
data class CpcbStation(val name: String, val distanceKm: Double, val pm25Index: Int?, val pm10Index: Int?, val lastUpdate: String?) {
    val aqi: Int get() = listOfNotNull(pm25Index, pm10Index).max()
    val dominant: String get() = if ((pm25Index ?: -1) >= (pm10Index ?: -1)) "PM2.5" else "PM10"
}

object CpcbAqi {
    /** The nearest station within [maxKm] that reports at least one PM sub-index. */
    fun nearest(records: List<CpcbRecord>, lat: Double, lon: Double, maxKm: Double = 60.0): CpcbStation? =
        records.groupBy { it.station ?: "" }.filterKeys { it.isNotEmpty() }.mapNotNull { (name, rows) ->
            val sLat = rows.firstNotNullOfOrNull { it.latitude?.toDoubleOrNull() } ?: return@mapNotNull null
            val sLon = rows.firstNotNullOfOrNull { it.longitude?.toDoubleOrNull() } ?: return@mapNotNull null
            val pm25 = rows.firstOrNull { it.pollutantId.equals("PM2.5", true) }?.subIndex
            val pm10 = rows.firstOrNull { it.pollutantId.equals("PM10", true) }?.subIndex
            if (pm25 == null && pm10 == null) return@mapNotNull null
            CpcbStation(name, Geo.haversineKm(lat, lon, sLat, sLon), pm25, pm10, rows.firstNotNullOfOrNull { it.lastUpdate })
        }.filter { it.distanceKm <= maxKm }.minByOrNull { it.distanceKm }

    /** Sanity: a sub-index must sit inside the CPCB scale. */
    fun plausible(station: CpcbStation): Boolean = station.aqi in 1..500
}
