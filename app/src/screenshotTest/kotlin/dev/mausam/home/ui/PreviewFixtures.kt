package dev.mausam.home.ui

import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.model.AirQuality
import dev.mausam.home.domain.model.AqiPoint
import dev.mausam.home.domain.model.CurrentConditions
import dev.mausam.home.domain.model.DailyForecast
import dev.mausam.home.domain.model.DataKind
import dev.mausam.home.domain.model.HourlyForecast
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.MarineState
import dev.mausam.home.domain.model.SourceInfo
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.personas.Persona
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object PreviewFixtures {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val delhi = Location("delhi", "New Delhi", 28.6139, 77.2090, region = "Delhi", zoneId = zone, imdStationId = "42182")
    val mumbai = Location("mumbai", "Mumbai", 19.0760, 72.8777, region = "Maharashtra", zoneId = zone, imdStationId = "43003")

    /** A weekday morning: Friday 11 Sep 2026, 06:30 IST. */
    val now: ZonedDateTime = ZonedDateTime.of(2026, 9, 11, 6, 30, 0, 0, zone)

    fun hourly(now: ZonedDateTime, hours: Int = 48, rainFrom: Int = -1, rainTo: Int = -1): List<HourlyForecast> {
        val start = now.withMinute(0).withSecond(0).withNano(0)
        return (0 until hours).map { i ->
            val t = start.plusHours(i.toLong())
            val h = t.hour
            val temp = 24.0 + 8.0 * Math.sin((h - 6) / 24.0 * Math.PI)  // 24 at 6, ~32 at 15
            val rainy = h in rainFrom until rainTo
            HourlyForecast(
                time = t.toInstant(), temperatureC = temp, humidityPct = if (h < 10) 55 else 70,
                precipitationMm = if (rainy) 2.0 else 0.0, precipitationProbabilityPct = if (rainy) 70 else 10,
                windKph = 12.0, uvIndex = if (h in 10..15) 7.0 else if (h in 7..9 || h in 16..17) 3.0 else 0.0,
                cloudCoverPct = if (rainy) 90 else 30, visibilityKm = 8.0,
                condition = if (rainy) WeatherCondition.RAIN else WeatherCondition.PARTLY_CLOUDY, isDay = h in 6..18,
            )
        }
    }

    fun daily(now: ZonedDateTime, days: Int = 7, minC: Double = 24.0, maxC: Double = 33.0): List<DailyForecast> =
        (0 until days).map { i ->
            DailyForecast(
                date = now.toLocalDate().plusDays(i.toLong()), minC = minC, maxC = maxC,
                precipitationMm = if (i == 2) 12.0 else 0.0, precipitationProbabilityPct = if (i == 2) 70 else 10 + i * 5,
                condition = if (i == 2) WeatherCondition.RAIN else WeatherCondition.PARTLY_CLOUDY,
                sunrise = LocalTime.of(6, 5), sunset = LocalTime.of(18, 30), uvIndexMax = 8.0, windMaxKph = 20.0,
            )
        }

    fun current(now: ZonedDateTime, temp: Double = 27.0, humidity: Int = 65, visibilityKm: Double = 6.0): CurrentConditions =
        CurrentConditions(
            time = now.toInstant(), temperatureC = temp, feelsLikeC = temp + 2, humidityPct = humidity, windKph = 10.0,
            windDirectionDeg = 270, gustKph = 18.0, visibilityKm = visibilityKm, cloudCoverPct = 30, uvIndex = null,
            pressureHpa = 1008.0, precipitationMm = 0.0, condition = WeatherCondition.PARTLY_CLOUDY, isDay = true,
        )

    fun aqi(now: ZonedDateTime, value: Int = 120): AirQuality = AirQuality(
        time = now.toInstant(), aqi = value, pm25 = 70.0, pm10 = 110.0, dominant = "PM2.5",
        history24h = (0 until 24).map { AqiPoint(now.toInstant().minusSeconds((24 - it) * 3600L), value - 20 + it, null, null) },
        stationName = "Test station",
    )

    fun warning(now: ZonedDateTime, severity: WarningSeverity = WarningSeverity.ORANGE, event: String = "Heavy rain"): WeatherWarning =
        WeatherWarning(
            id = "w-${event.lowercase().replace(' ', '-')}", severity = severity, event = event,
            headline = "$event likely over the district", description = "Detailed text.", area = "New Delhi",
            onset = now.toInstant().minusSeconds(3600), expires = now.toInstant().plusSeconds(24 * 3600), source = "test",
        )

    fun bundle(
        now: ZonedDateTime = PreviewFixtures.now,
        location: Location = delhi,
        warnings: List<WeatherWarning> = emptyList(),
        rainFrom: Int = -1, rainTo: Int = -1,
        withAqi: Boolean = true,
        marine: MarineState? = null,
    ): WeatherBundle = WeatherBundle(
        location = location, current = current(now), hourly = hourly(now, rainFrom = rainFrom, rainTo = rainTo),
        daily = daily(now), warnings = warnings, airQuality = if (withAqi) aqi(now) else null, marine = marine,
        rainfall = null, advisory = null, fetchedAt = now.toInstant(),
        sources = mapOf(DataKind.CURRENT to SourceInfo("test", now.toInstant(), false)),
    )

    fun context(
        bundle: WeatherBundle = bundle(),
        now: ZonedDateTime = PreviewFixtures.now,
        personas: Set<Persona> = Persona.entries.toSet(),
        distanceToCoastKm: Double = 900.0,
        destinations: List<WeatherBundle> = emptyList(),
    ): CardContext = CardContext(
        now = now, location = bundle.location, bundle = bundle, distanceToCoastKm = distanceToCoastKm,
        settings = UserSettings(personas = personas), destinations = destinations,
    )

    fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant()
}
