package dev.mausam.home.domain

import com.google.common.truth.Truth.assertThat
import dev.mausam.home.data.WeatherAssembler
import dev.mausam.home.data.RawPayload
import dev.mausam.home.data.SourceKey
import dev.mausam.home.data.geo.Stations
import dev.mausam.home.data.geo.StationsFile
import dev.mausam.home.data.net.Http
import dev.mausam.home.domain.model.DataKind
import dev.mausam.home.domain.model.Location
import org.junit.Test
import java.io.File
import java.time.Instant

/** The bundled snapshots must always assemble into a usable bundle: this is the demo's safety net. */
class AssemblerTest {
    private val assets = File("src/main/assets")
    private fun read(name: String) = File(assets, "snapshots/$name.json").readText()
    private val stations = Stations.of(Http.json.decodeFromString(StationsFile.serializer(), File(assets, "stations.json").readText()))

    @Test
    fun `stations table resolves major cities to IMD keys`() {
        val delhi = stations.resolve(28.61, 77.21)
        assertThat(delhi.station?.id).isEqualTo("42182")
        assertThat(delhi.district?.name).isEqualTo("NEW DELHI")
        assertThat(stations.resolve(19.08, 72.88).district?.name).contains("MUMBAI")
        assertThat(stations.search("chenn").map { it.name }).contains("Chennai")
        assertThat(stations.defaultLocation().imdStationId).isEqualTo("42182")
    }

    @Test
    fun `delhi snapshot assembles with IMD current, forecast, warnings and AQI`() {
        val loc = stations.enrich(Location("delhi", "New Delhi", 28.61, 77.21))
        val at = Instant.parse("2026-09-12T11:30:00Z")
        val raw = mapOf(
            SourceKey.IMD_WARNINGS to RawPayload(read("imd_warnings"), at, true),
            SourceKey.IMD_NOWCAST to RawPayload(read("imd_nowcast"), at, true),
            SourceKey.IMD_SYNOP to RawPayload(read("imd_synop"), at, true),
            SourceKey.IMD_METAR to RawPayload(read("imd_metar"), at, true),
            SourceKey.SACHET to RawPayload(read("sachet"), at, true),
            SourceKey.OM_FORECAST to RawPayload(read("om_forecast_delhi"), at, true),
            SourceKey.OM_AQI to RawPayload(read("om_aqi_delhi"), at, true),
        )
        val b = WeatherAssembler().assemble(loc, raw, at)
        assertThat(b.current).isNotNull()
        assertThat(b.current!!.temperatureC).isIn(com.google.common.collect.Range.closed(15.0, 45.0))
        assertThat(b.hourly.size).isAtLeast(100)
        assertThat(b.daily.size).isAtLeast(6)
        assertThat(b.daily.first().sunrise).isNotNull()
        assertThat(b.airQuality).isNotNull()
        assertThat(b.airQuality!!.history24h.size).isAtLeast(12)
        assertThat(b.warnings).isNotEmpty()
        assertThat(b.sources).containsKey(DataKind.CURRENT)
        assertThat(b.sources.getValue(DataKind.CURRENT).label).contains("IMD")
        assertThat(b.sources.getValue(DataKind.CURRENT).fromSnapshot).isTrue()
        assertThat(b.rainfall).isNotNull()
    }

    @Test
    fun `mumbai snapshot carries marine state`() {
        val loc = stations.enrich(Location("mumbai", "Mumbai", 19.08, 72.88))
        val at = Instant.parse("2026-09-12T11:30:00Z")
        val raw = mapOf(
            SourceKey.OM_FORECAST to RawPayload(read("om_forecast_mumbai"), at, true),
            SourceKey.OM_MARINE to RawPayload(read("om_marine_mumbai"), at, true),
        )
        val b = WeatherAssembler().assemble(loc, raw, at)
        assertThat(b.marine?.waveHeightM).isNotNull()
        assertThat(b.current).isNotNull()
    }

    @Test
    fun `assembler tolerates an empty payload map`() {
        val b = WeatherAssembler().assemble(Location("x", "X", 20.0, 78.0), emptyMap(), Instant.now())
        assertThat(b.current).isNull()
        assertThat(b.hourly).isEmpty()
    }
}
