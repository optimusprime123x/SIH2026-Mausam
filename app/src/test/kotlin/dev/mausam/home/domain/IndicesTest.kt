package dev.mausam.home.domain

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.cards.Thermal
import dev.mausam.home.domain.cards.UvEstimate
import dev.mausam.home.domain.geo.Coastline
import dev.mausam.home.domain.model.Freshness
import org.junit.Test
import java.time.Duration

class IndicesTest {
    @Test
    fun `heat index matches the NOAA chart`() {
        // 90 °F / 70 % RH → 105 °F on the NWS chart (≈ 40.6 °C)
        assertThat(Thermal.heatIndexC(32.2, 70.0)).isIn(Range.closed(39.5, 42.0))
        // Below the regression's range the plain temperature is returned
        assertThat(Thermal.heatIndexC(20.0, 90.0)).isWithin(0.01).of(20.0)
    }

    @Test
    fun `humidex and dew point match Environment Canada examples`() {
        assertThat(Thermal.dewPointC(25.0, 60.0)).isIn(Range.closed(16.0, 17.5))
        assertThat(Thermal.humidexC(30.0, 70.0)).isIn(Range.closed(39.0, 43.0))
        assertThat(Thermal.comfort(30.0, 70.0)).isEqualTo(Thermal.Comfort.VERY_HOT)
        assertThat(Thermal.comfort(22.0, 40.0)).isEqualTo(Thermal.Comfort.COMFORTABLE)
    }

    @Test
    fun `indian aqi breakpoints`() {
        assertThat(IndianAqi.fromPm25(30.0)).isEqualTo(50)
        assertThat(IndianAqi.fromPm25(60.0)).isEqualTo(100)
        assertThat(IndianAqi.fromPm25(90.0)).isEqualTo(200)
        assertThat(IndianAqi.fromPm25(45.0)).isIn(Range.closed(75, 76))
        assertThat(IndianAqi.fromPm10(100.0)).isEqualTo(100)
        assertThat(IndianAqi.compute(120.0, 50.0)).isEqualTo(300)
        assertThat(IndianAqi.category(180)).isEqualTo(IndianAqi.Category.MODERATE)
        assertThat(IndianAqi.category(450)).isEqualTo(IndianAqi.Category.SEVERE)
        assertThat(IndianAqi.dominant(70.0, 60.0)).isEqualTo("PM2.5")
    }

    @Test
    fun `uv estimate is zero at night and high at tropical noon`() {
        assertThat(UvEstimate.estimate(28.6, 77.2, Fixtures.instant(2026, 9, 12, 1, 0), 0)).isWithin(0.001).of(0.0)
        val noonClear = UvEstimate.estimate(28.6, 77.2, Fixtures.instant(2026, 9, 12, 12, 15), 0)
        assertThat(noonClear).isIn(Range.closed(7.0, 12.0))
        val noonOvercast = UvEstimate.estimate(28.6, 77.2, Fixtures.instant(2026, 9, 12, 12, 15), 95)
        assertThat(noonOvercast).isLessThan(noonClear * 0.4)
    }

    @Test
    fun `coastline gate`() {
        assertThat(Coastline.distanceKm(19.0760, 72.8777)).isLessThan(15.0) // Mumbai
        assertThat(Coastline.distanceKm(13.0827, 80.2707)).isLessThan(15.0) // Chennai
        assertThat(Coastline.distanceKm(28.6139, 77.2090)).isGreaterThan(500.0) // Delhi
        assertThat(Coastline.isCoastal(12.9716, 77.5946)).isFalse() // Bengaluru
    }

    @Test
    fun `freshness turns stale after two hours`() {
        val fetched = Fixtures.instant(2026, 9, 12, 12, 10)
        val fresh = Freshness.of(fetched, fetched.plus(Duration.ofMinutes(30)), Fixtures.zone)
        val stale = Freshness.of(fetched, fetched.plus(Duration.ofHours(3)), Fixtures.zone)
        assertThat(fresh.isStale).isFalse()
        assertThat(fresh.label).isEqualTo("as of 12:10")
        assertThat(stale.isStale).isTrue()
        assertThat(stale.label).isEqualTo("offline, last updated 12:10")
    }
}
