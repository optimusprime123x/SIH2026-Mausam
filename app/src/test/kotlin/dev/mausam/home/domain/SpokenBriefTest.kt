package dev.mausam.home.domain

import dev.mausam.home.domain.briefs.SpokenBrief
import dev.mausam.home.domain.model.AirQuality
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpokenBriefTest {
    private val fmt = Formatter()
    private val bundle = Fixtures.bundle()

    @Test fun fullBrief() {
        val cur = bundle.current!!.copy(condition = WeatherCondition.HAZE, temperatureC = 28.0, feelsLikeC = 33.0)
        val air = AirQuality(Instant.EPOCH, 120, null, null, null, emptyList(), null)
        val warn = Fixtures.warning(Fixtures.now, WarningSeverity.YELLOW, "Thunderstorm & lightning")
        val text = SpokenBrief.compose("Kolkata", cur, air, warn, fmt)
        assertEquals(
            "In Kolkata, it is currently hazy. The temperature is 28 degrees, but it feels like 33 degrees. " +
                "The air quality index is 120, moderate. There is an active yellow alert: Thunderstorm & lightning likely over the district.",
            text,
        )
    }

    @Test fun noAlertNoAir() {
        val cur = bundle.current!!.copy(condition = WeatherCondition.CLEAR, temperatureC = 30.4, feelsLikeC = 30.0)
        val text = SpokenBrief.compose("Pune", cur, null, null, fmt)
        assertEquals("In Pune, it is currently clear. The temperature is 30 degrees. There are no active weather alerts.", text)
    }

    @Test fun neverContainsSymbols() {
        val text = SpokenBrief.compose("Delhi", bundle.current, bundle.airQuality, null, fmt)
        assertTrue(text, '°' !in text && '·' !in text)
    }
}
