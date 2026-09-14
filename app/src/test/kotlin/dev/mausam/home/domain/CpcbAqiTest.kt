package dev.mausam.home.domain

import dev.mausam.home.data.cpcb.CpcbAqi
import dev.mausam.home.data.cpcb.CpcbResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpcbAqiTest {
    private val json = Json { ignoreUnknownKeys = true }

    // The shape data.gov.in returns in September 2026: avg_value, not pollutant_avg.
    private val body = """{"total":3,"count":3,"records":[
      {"state":"Delhi","city":"Delhi","station":"Shadipur, Delhi - CPCB","last_update":"13-09-2026 11:00:00","latitude":"28.6514","longitude":"77.1473","pollutant_id":"PM2.5","min_value":"23","max_value":"96","avg_value":"53"},
      {"state":"Delhi","city":"Delhi","station":"Shadipur, Delhi - CPCB","last_update":"13-09-2026 11:00:00","latitude":"28.6514","longitude":"77.1473","pollutant_id":"PM10","min_value":"40","max_value":"120","avg_value":"81"},
      {"state":"Delhi","city":"Delhi","station":"Pusa, Delhi - DPCC","last_update":"13-09-2026 11:00:00","latitude":"28.6390","longitude":"77.1460","pollutant_id":"PM2.5","min_value":"12","max_value":"70","avg_value":"35"},
      {"state":"Delhi","city":"Delhi","station":"Cantonment Area, Delhi - DPCC","last_update":"13-09-2026 11:00:00","latitude":"28.5900","longitude":"77.1300","pollutant_id":"PM2.5","min_value":"NA","max_value":"NA","avg_value":"NA"}
    ]}"""

    @Test fun worstSubIndexOfNearestStation() {
        val r = json.decodeFromString(CpcbResponse.serializer(), body)
        val s = CpcbAqi.nearest(r.records, 28.65, 77.15)!!
        assertEquals("Shadipur, Delhi - CPCB", s.name)
        assertEquals(81, s.aqi)
        assertEquals("PM10", s.dominant)
    }

    @Test fun legacyFieldNamesStillRead() {
        val legacy = body.replace("avg_value", "pollutant_avg").replace("min_value", "pollutant_min").replace("max_value", "pollutant_max")
        val r = json.decodeFromString(CpcbResponse.serializer(), legacy)
        assertEquals(53, r.records.first().subIndex)
    }

    @Test fun naStationsAndFarStationsAreSkipped() {
        val r = json.decodeFromString(CpcbResponse.serializer(), body)
        assertNull(CpcbAqi.nearest(r.records, 22.57, 88.36))
        assertEquals("Pusa, Delhi - DPCC", CpcbAqi.nearest(r.records, 28.639, 77.146)!!.name)
    }
}
