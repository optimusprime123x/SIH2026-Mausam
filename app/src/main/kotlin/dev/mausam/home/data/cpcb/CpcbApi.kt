package dev.mausam.home.data.cpcb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CPCB real-time AQI via data.gov.in. One row per pollutant per station. The key ships in
 * BuildConfig (a data.gov.in key is per-user, free and not secret-grade); the shared sample key
 * is rate-limited, so the repository falls back to Open-Meteo's model AQI on any failure.
 */
interface CpcbApi {
    @GET("resource/3b01bcb8-0b14-4abf-b6f2-c1bfd384ba69?format=json")
    suspend fun stations(
        @Query("api-key") apiKey: String,
        @Query("filters[state]") state: String? = null,
        @Query("filters[city]") city: String? = null,
        @Query("limit") limit: Int = 200,
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

@Serializable
data class CpcbRecord(
    val state: String? = null,
    val city: String? = null,
    val station: String? = null,
    @SerialName("last_update") val lastUpdate: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    @SerialName("pollutant_id") val pollutantId: String? = null,
    @SerialName("pollutant_min") val pollutantMin: String? = null,
    @SerialName("pollutant_max") val pollutantMax: String? = null,
    @SerialName("pollutant_avg") val pollutantAvg: String? = null,
)
