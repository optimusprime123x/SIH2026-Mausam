package dev.mausam.home.domain

import com.google.common.truth.Truth.assertThat
import dev.mausam.home.domain.solar.Solar
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class SolarTest {
    @Test
    fun `delhi sunrise and sunset in september are within a few minutes of published times`() {
        val t = Solar.sunTimes(28.6139, 77.2090, LocalDate.of(2026, 9, 12), Fixtures.zone)
        val rise = t.sunriseLocal(Fixtures.zone)!!
        val set = t.sunsetLocal(Fixtures.zone)!!
        // timeanddate.com style reference for New Delhi, 12 Sep: ~06:06 / ~18:33 IST
        assertThat(Duration.between(LocalTime.of(6, 6), rise).abs().toMinutes()).isAtMost(12)
        assertThat(Duration.between(LocalTime.of(18, 33), set).abs().toMinutes()).isAtMost(12)
    }

    @Test
    fun `elevation is high at noon and negative at midnight`() {
        val noon = Solar.elevation(28.6139, 77.2090, Fixtures.instant(2026, 9, 12, 12, 15))
        val midnight = Solar.elevation(28.6139, 77.2090, Fixtures.instant(2026, 9, 12, 0, 30))
        assertThat(noon).isIn(com.google.common.collect.Range.closed(58.0, 72.0))
        assertThat(midnight).isLessThan(0.0)
    }

    @Test
    fun `isDay follows sunrise and sunset`() {
        val t = Solar.sunTimes(19.0760, 72.8777, LocalDate.of(2026, 1, 15), Fixtures.zone)
        assertThat(t.isDay(Fixtures.instant(2026, 1, 15, 13, 0))).isTrue()
        assertThat(t.isDay(Fixtures.instant(2026, 1, 15, 3, 0))).isFalse()
    }
}
