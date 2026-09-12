package dev.mausam.home.domain.model

import dev.mausam.home.domain.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A place the user cares about. `id` is the stable cache key. */
data class Location(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
    val zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
    val imdStationId: String? = null,
    val district: String? = null,
    val state: String? = null,
)

enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, OVERCAST, FOG, HAZE, DRIZZLE, RAIN, HEAVY_RAIN,
    THUNDERSTORM, SNOW, DUST, WINDY, UNKNOWN;

    val isWet: Boolean get() = this == DRIZZLE || this == RAIN || this == HEAVY_RAIN || this == THUNDERSTORM
    val isLowVisibility: Boolean get() = this == FOG || this == HAZE || this == DUST

    fun label(): String = when (this) {
        CLEAR -> "Clear"
        PARTLY_CLOUDY -> "Partly cloudy"
        CLOUDY -> "Cloudy"
        OVERCAST -> "Overcast"
        FOG -> "Fog"
        HAZE -> "Haze"
        DRIZZLE -> "Drizzle"
        RAIN -> "Rain"
        HEAVY_RAIN -> "Heavy rain"
        THUNDERSTORM -> "Thunderstorm"
        SNOW -> "Snow"
        DUST -> "Dust"
        WINDY -> "Windy"
        UNKNOWN -> "—"
    }.tr()

    companion object {
        /** WMO 4677 present-weather codes as used by Open-Meteo and MET Norway. */
        fun fromWmoCode(code: Int?): WeatherCondition = when (code) {
            null -> UNKNOWN
            0 -> CLEAR
            1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 66, 80 -> RAIN
            63, 81 -> RAIN
            65, 67, 82 -> HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }

        /** Best-effort mapping of free-text conditions (IMD bulletins, CAP headlines). */
        fun fromText(text: String?): WeatherCondition {
            val t = text?.lowercase() ?: return UNKNOWN
            return when {
                "thunder" in t || "lightning" in t -> THUNDERSTORM
                "heavy rain" in t || "very heavy" in t || "extremely heavy" in t -> HEAVY_RAIN
                "drizzle" in t -> DRIZZLE
                "rain" in t || "shower" in t -> RAIN
                "snow" in t -> SNOW
                "fog" in t || "mist" in t -> FOG
                "haze" in t || "smoke" in t -> HAZE
                "dust" in t || "sand" in t -> DUST
                "overcast" in t -> OVERCAST
                "partly" in t || "mainly clear" in t -> PARTLY_CLOUDY
                "cloud" in t -> CLOUDY
                "clear" in t || "sunny" in t || "fair" in t -> CLEAR
                "wind" in t || "gale" in t || "squall" in t -> WINDY
                else -> UNKNOWN
            }
        }
    }
}

/** What the ambient scene should paint. Derived, never stored. */
enum class SceneKind { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, FOG, DRIZZLE, RAIN, THUNDERSTORM;

    companion object {
        fun from(condition: WeatherCondition, isDay: Boolean): SceneKind = when (condition) {
            WeatherCondition.CLEAR -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY, WeatherCondition.OVERCAST, WeatherCondition.WINDY -> CLOUDY
            WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.DUST -> FOG
            WeatherCondition.DRIZZLE -> DRIZZLE
            WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN, WeatherCondition.SNOW -> RAIN
            WeatherCondition.THUNDERSTORM -> THUNDERSTORM
            WeatherCondition.UNKNOWN -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
        }
    }
}

data class CurrentConditions(
    val time: Instant,
    val temperatureC: Double,
    val feelsLikeC: Double?,
    val humidityPct: Int?,
    val windKph: Double?,
    val windDirectionDeg: Int?,
    val gustKph: Double?,
    val visibilityKm: Double?,
    val cloudCoverPct: Int?,
    val uvIndex: Double?,
    val pressureHpa: Double?,
    val precipitationMm: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
)

data class HourlyForecast(
    val time: Instant,
    val temperatureC: Double,
    val humidityPct: Int?,
    val precipitationMm: Double,
    val precipitationProbabilityPct: Int?,
    val windKph: Double?,
    val uvIndex: Double?,
    val cloudCoverPct: Int?,
    val visibilityKm: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val minC: Double,
    val maxC: Double,
    val precipitationMm: Double,
    val precipitationProbabilityPct: Int?,
    val condition: WeatherCondition,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val uvIndexMax: Double?,
    val windMaxKph: Double?,
)

enum class WarningSeverity(val rank: Int, val rawLabel: String, val rawAdvice: String) {
    YELLOW(1, "Yellow", "Be updated"),
    ORANGE(2, "Orange", "Be prepared"),
    RED(3, "Red", "Take action");

    /** Translated for display; [rawLabel] / [rawAdvice] stay English and key the translation table. */
    val label: String get() = rawLabel.tr()
    val advice: String get() = rawAdvice.tr()

    /** Only orange and red may push a notification; yellow is banner-only. */
    val pushes: Boolean get() = this != YELLOW

    companion object {
        fun fromText(text: String?): WarningSeverity? {
            val t = text?.lowercase() ?: return null
            return when {
                "red" in t || "extreme" in t -> RED
                "orange" in t || "severe" in t -> ORANGE
                "yellow" in t || "moderate" in t -> YELLOW
                else -> null
            }
        }
    }
}

data class WeatherWarning(
    val id: String,
    val severity: WarningSeverity,
    val event: String,
    val headline: String,
    val description: String,
    val area: String,
    val onset: Instant?,
    val expires: Instant?,
    val source: String,
) {
    fun isActive(now: Instant): Boolean =
        (onset == null || !onset.isAfter(now)) && (expires == null || expires.isAfter(now))
}

data class AqiPoint(val time: Instant, val aqi: Int, val pm25: Double?, val pm10: Double?)

data class AirQuality(
    val time: Instant,
    val aqi: Int,
    val pm25: Double?,
    val pm10: Double?,
    val dominant: String?,
    val history24h: List<AqiPoint>,
    val stationName: String?,
)

data class MarineState(
    val time: Instant,
    val waveHeightM: Double?,
    val wavePeriodS: Double?,
    val waveDirectionDeg: Int?,
    val swellHeightM: Double?,
    val seaSurfaceTempC: Double?,
)

data class RainfallSummary(
    val todayMm: Double?,
    val past7DaysMm: Double?,
    val normalPast7DaysMm: Double?,
    val asOf: Instant,
)

data class AgroAdvisory(val title: String, val body: String, val issuedAt: Instant?, val source: String)

/**
 * Model soil water, as volumetric percent (m³ water per m³ soil × 100). [topPct] is the
 * depth-weighted 0–9 cm layer, [rootPct] the 9–27 cm layer, [trend] the change in [topPct]
 * over the next 24 hours. Thresholds are indicative; field capacity depends on soil type.
 */
data class SoilState(
    val topPct: Double,
    val rootPct: Double?,
    val temperatureC: Double?,
    val trend: Double?,
    val asOf: Instant,
) {
    val category: SoilCategory get() = SoilCategory.of(topPct)
}

enum class SoilCategory { VERY_DRY, DRY, ADEQUATE, WET, SATURATED;
    companion object {
        fun of(pct: Double): SoilCategory = when {
            pct < 10 -> VERY_DRY
            pct < 18 -> DRY
            pct < 32 -> ADEQUATE
            pct < 42 -> WET
            else -> SATURATED
        }
    }
}

/** Which upstream provided each kind of data, so cards can label their source honestly. */
enum class DataKind { CURRENT, HOURLY, DAILY, WARNINGS, AIR_QUALITY, MARINE, RAINFALL, ADVISORY, SOIL }

data class SourceInfo(val label: String, val fetchedAt: Instant, val fromSnapshot: Boolean)

data class WeatherBundle(
    val location: Location,
    val current: CurrentConditions?,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val warnings: List<WeatherWarning>,
    val airQuality: AirQuality?,
    val marine: MarineState?,
    val rainfall: RainfallSummary?,
    val advisory: AgroAdvisory?,
    val fetchedAt: Instant,
    val sources: Map<DataKind, SourceInfo>,
    val soil: SoilState? = null,
) {
    fun activeWarnings(now: Instant): List<WeatherWarning> =
        warnings.filter { it.isActive(now) }.sortedByDescending { it.severity.rank }

    fun hourlyFrom(now: Instant, hours: Int): List<HourlyForecast> =
        hourly.filter { !it.time.isBefore(now.minusSeconds(3600)) }.take(hours)

    companion object {
        fun empty(location: Location, now: Instant): WeatherBundle = WeatherBundle(
            location, null, emptyList(), emptyList(), emptyList(), null, null, null, null, now, emptyMap(),
        )
    }
}
