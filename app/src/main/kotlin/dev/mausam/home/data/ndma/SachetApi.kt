package dev.mausam.home.data.ndma

import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET

/**
 * NDMA SACHET: the national Common Alerting Protocol feed. IMD regional centres, INCOIS and the
 * state disaster authorities all publish here. Open JSON, public domain, no parameters: fetch the
 * whole list (about 70 KB) once per refresh and filter on device.
 */
interface SachetApi {
    @GET("FetchAllAlertDetails")
    suspend fun allAlerts(): JsonArray

    companion object {
        const val BASE_URL = "https://sachet.ndma.gov.in/cap_public_website/"
    }
}
