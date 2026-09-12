package dev.mausam.home.domain.cards

import dev.mausam.home.domain.model.DailyForecast
import dev.mausam.home.domain.model.HourlyForecast
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.solar.Solar
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Derived indices. Every formula cites its source; nothing here is a forecast of our own. */
object Thermal {
    /** Magnus formula dew point (Alduchov & Eskridge 1996 constants). */
    fun dewPointC(tempC: Double, humidityPct: Double): Double {
        val a = 17.625
        val b = 243.04
        val gamma = ln(humidityPct.coerceIn(1.0, 100.0) / 100.0) + a * tempC / (b + tempC)
        return b * gamma / (a - gamma)
    }

    /**
     * NOAA / Rothfusz heat index regression (NWS Technical Attachment SR 90-23) with the
     * standard low-humidity and high-humidity adjustments. Returns °C. Below 27 °C the plain
     * temperature is returned because the regression is not defined there.
     */
    fun heatIndexC(tempC: Double, humidityPct: Double): Double {
        if (tempC < 26.7) return tempC
        val t = tempC * 9 / 5 + 32
        val r = humidityPct.coerceIn(0.0, 100.0)
        val simple = 0.5 * (t + 61.0 + (t - 68.0) * 1.2 + r * 0.094)
        var hi = simple
        if ((simple + t) / 2 >= 80) {
            hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r -
                0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r +
                0.00085282 * t * r * r - 0.00000199 * t * t * r * r
            if (r < 13 && t in 80.0..112.0) {
                hi -= ((13 - r) / 4) * sqrt((17 - kotlin.math.abs(t - 95)) / 17)
            } else if (r > 85 && t in 80.0..87.0) {
                hi += ((r - 85) / 10) * ((87 - t) / 5)
            }
        }
        return (hi - 32) * 5 / 9
    }

    /** Environment Canada humidex: T + 0.5555 (e − 10), e from the dew point. */
    fun humidexC(tempC: Double, humidityPct: Double): Double {
        val td = dewPointC(tempC, humidityPct)
        val e = 6.11 * exp(5417.7530 * (1 / 273.16 - 1 / (273.15 + td)))
        return tempC + 0.5555 * (e - 10)
    }

    enum class Comfort(val label: String, val tone: Tone) {
        COOL("Cool", Tone.NEUTRAL),
        COMFORTABLE("Comfortable", Tone.GOOD),
        WARM("Warm", Tone.NEUTRAL),
        HOT("Hot", Tone.CAUTION),
        VERY_HOT("Very hot", Tone.WARNING),
        DANGEROUS("Dangerous heat", Tone.DANGER),
    }

    /** Comfort from the worse of heat index and humidex, following the humidex comfort bands. */
    fun comfort(tempC: Double, humidityPct: Double): Comfort {
        val idx = max(heatIndexC(tempC, humidityPct), humidexC(tempC, humidityPct))
        return when {
            tempC < 16 -> Comfort.COOL
            idx < 29 -> Comfort.COMFORTABLE
            idx < 35 -> Comfort.WARM
            idx < 40 -> Comfort.HOT
            idx < 46 -> Comfort.VERY_HOT
            else -> Comfort.DANGEROUS
        }
    }
}

object UvEstimate {
    /**
     * Clear-sky UV index from solar elevation: Madronich (2007), "Analytic formula for the
     * clear-sky UV index", UVI ≈ 12.50 · μ0^2.42 · (Ω/300)^−1.23 with μ0 = cos(zenith) and
     * Ω the ozone column in Dobson units. Typical Indian-latitude ozone ≈ 270 DU.
     */
    fun clearSky(elevationDeg: Double, ozoneDu: Double = 270.0): Double {
        if (elevationDeg <= 0) return 0.0
        val mu0 = sin(Math.toRadians(elevationDeg))
        return 12.50 * mu0.pow(2.42) * (ozoneDu / 300.0).pow(-1.23)
    }

    /** US EPA cloud transmission factors: clear 100 %, scattered 89 %, broken 73 %, overcast 31 %. */
    fun cloudFactor(cloudCoverPct: Int?): Double = when {
        cloudCoverPct == null -> 1.0
        cloudCoverPct < 25 -> 1.0
        cloudCoverPct < 50 -> 0.89
        cloudCoverPct < 85 -> 0.73
        else -> 0.31
    }

    fun estimate(latitude: Double, longitude: Double, at: Instant, cloudCoverPct: Int?): Double =
        clearSky(Solar.elevation(latitude, longitude, at)) * cloudFactor(cloudCoverPct)

    fun label(uvi: Double): Pair<String, Tone> = when {
        uvi < 3 -> "Low" to Tone.GOOD
        uvi < 6 -> "Moderate" to Tone.NEUTRAL
        uvi < 8 -> "High" to Tone.CAUTION
        uvi < 11 -> "Very high" to Tone.WARNING
        else -> "Extreme" to Tone.DANGER
    }
}

data class Window(val start: Instant, val end: Instant, val score: Int)

/** Hourly rule score for outdoor exercise, 0–100. Penalties are additive and capped. */
object RunScore {
    fun score(h: HourlyForecast, aqi: Int?): Int {
        var penalty = 0.0
        val t = h.temperatureC
        penalty += when {
            t in 12.0..22.0 -> 0.0
            t < 12 -> (12 - t) * 3
            else -> (t - 22) * 4
        }
        h.humidityPct?.let { if (it > 60) penalty += (it - 60) * 0.8 }
        h.uvIndex?.let { if (it > 3) penalty += (it - 3) * 6 }
        aqi?.let { if (it > 100) penalty += (it - 100) * 0.4 }
        h.precipitationProbabilityPct?.let { penalty += it * 0.4 }
        if (h.precipitationMm >= 1.0) penalty += 30
        h.windKph?.let { if (it > 25) penalty += (it - 25) * 1.0 }
        if (h.condition == WeatherCondition.THUNDERSTORM) penalty += 60
        if (h.condition.isLowVisibility) penalty += 15
        return (100 - penalty).roundToInt().coerceIn(0, 100)
    }

    /**
     * Best contiguous window today between 05:00 and 21:00 with score ≥ [threshold].
     * Falls back to the best single hour when nothing clears the bar.
     */
    fun bestWindow(
        hourly: List<HourlyForecast>,
        aqi: Int?,
        now: ZonedDateTime,
        threshold: Int = 60,
    ): Window? {
        val zone = now.zone
        val today = now.toLocalDate()
        val candidates = hourly.filter {
            val z = it.time.atZone(zone)
            z.toLocalDate() == today && z.hour in 5..20 && !it.time.isBefore(now.toInstant().minusSeconds(3600))
        }
        if (candidates.isEmpty()) return null
        val scored = candidates.map { it to score(it, aqi) }
        var best: Window? = null
        var runStart: HourlyForecast? = null
        var runSum = 0
        var runLen = 0
        fun flush(last: HourlyForecast?) {
            if (runStart != null && last != null && runLen > 0) {
                val w = Window(runStart!!.time, last.time.plusSeconds(3600), runSum / runLen)
                if (best == null || runLen > lengthOf(best!!) || (runLen == lengthOf(best!!) && w.score > best!!.score)) best = w
            }
            runStart = null; runSum = 0; runLen = 0
        }
        var prev: HourlyForecast? = null
        for ((h, s) in scored) {
            if (s >= threshold) {
                if (runStart == null) runStart = h
                runSum += s; runLen++
            } else flush(prev)
            prev = h
        }
        flush(prev)
        if (best != null) return best
        val (h, s) = scored.maxBy { it.second }
        return Window(h.time, h.time.plusSeconds(3600), s)
    }

    private fun lengthOf(w: Window): Int = ((w.end.epochSecond - w.start.epochSecond) / 3600).toInt()
}

/** Day score for events: rain probability, heat, wind and storms. 0–100. */
object DayScore {
    fun score(d: DailyForecast): Int {
        var penalty = 0.0
        d.precipitationProbabilityPct?.let { penalty += it * 0.6 }
        if (d.precipitationMm >= 5) penalty += 15
        if (d.maxC > 33) penalty += (d.maxC - 33) * 4
        if (d.maxC < 22) penalty += (22 - d.maxC) * 2
        d.windMaxKph?.let { if (it > 30) penalty += (it - 30) }
        if (d.condition == WeatherCondition.THUNDERSTORM) penalty += 30
        return (100 - penalty).roundToInt().coerceIn(0, 100)
    }

    fun best(daily: List<DailyForecast>): Pair<DailyForecast, Int>? =
        daily.take(7).map { it to score(it) }.maxByOrNull { it.second }
}

object Packing {
    fun tip(daily: List<DailyForecast>): String? {
        if (daily.isEmpty()) return null
        val week = daily.take(7)
        val tips = mutableListOf<String>()
        val wetDays = week.count { (it.precipitationProbabilityPct ?: 0) >= 40 || it.precipitationMm >= 2 }
        val maxT = week.maxOf { it.maxC }
        val minT = week.minOf { it.minC }
        if (wetDays >= 3) tips += "Umbrella and quick-dry layers, rain on $wetDays of 7 days"
        else if (wetDays > 0) tips += "Pack a compact umbrella"
        if (maxT >= 38) tips += "Light cottons, a cap and water: highs near ${maxT.roundToInt()}°"
        else if (maxT >= 32) tips += "Light clothes, highs near ${maxT.roundToInt()}°"
        if (minT <= 8) tips += "A warm jacket: nights fall to ${minT.roundToInt()}°"
        else if (minT <= 15) tips += "A light jacket for evenings"
        if (week.any { it.condition == WeatherCondition.THUNDERSTORM }) tips += "Storms expected, plan indoor backups"
        if (week.any { (it.uvIndexMax ?: 0.0) >= 8 }) tips += "Sunscreen, UV runs high"
        return tips.firstOrNull() ?: "Settled week, pack for ${minT.roundToInt()}–${maxT.roundToInt()}°"
    }
}

/** Rain in a clock window today (school run, commute). */
data class WindowRain(val maxProbabilityPct: Int, val totalMm: Double, val hours: List<HourlyForecast>) {
    val likely: Boolean get() = maxProbabilityPct >= 50 || totalMm >= 1.0
    val possible: Boolean get() = maxProbabilityPct >= 30 || totalMm >= 0.3
}

object Commute {
    fun rainIn(hourly: List<HourlyForecast>, now: ZonedDateTime, start: LocalTime, end: LocalTime, zone: ZoneId): WindowRain {
        val today = now.toLocalDate()
        val hours = hourly.filter {
            val z = it.time.atZone(zone)
            z.toLocalDate() == today && !z.toLocalTime().isBefore(start) && z.toLocalTime().isBefore(end)
        }
        return WindowRain(
            hours.maxOfOrNull { it.precipitationProbabilityPct ?: 0 } ?: 0,
            hours.sumOf { it.precipitationMm },
            hours,
        )
    }

    fun nextHours(hourly: List<HourlyForecast>, now: Instant, count: Int): WindowRain {
        val hours = hourly.filter { !it.time.isBefore(now.minusSeconds(3600)) }.take(count)
        return WindowRain(
            hours.maxOfOrNull { it.precipitationProbabilityPct ?: 0 } ?: 0,
            hours.sumOf { it.precipitationMm },
            hours,
        )
    }
}
