package dev.mausam.home.domain.cards

import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.personas.Persona
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Semantic colour of a value; the theme maps it to a colour role. */
enum class Tone { NEUTRAL, GOOD, CAUTION, WARNING, DANGER }

enum class Trend { UP, DOWN, FLAT }

/**
 * Icon keys. Each maps to a Meteocons animated Lottie file (assets/icons/<assetName>.json)
 * with Material Symbols as the fallback. Keys are data, so cards stay UI-free.
 */
enum class WeatherIcon(val assetName: String) {
    CLEAR_DAY("clear-day"), CLEAR_NIGHT("clear-night"),
    PARTLY_CLOUDY_DAY("partly-cloudy-day"), PARTLY_CLOUDY_NIGHT("partly-cloudy-night"),
    CLOUDY("cloudy"), OVERCAST("overcast"), FOG("fog"), HAZE("haze"), DUST("dust"), SMOKE("smoke"),
    DRIZZLE("drizzle"), RAIN("rain"), THUNDERSTORMS("thunderstorms"), THUNDERSTORMS_RAIN("thunderstorms-rain"),
    SNOW("snow"), WIND("wind"), HUMIDITY("humidity"), UV_INDEX("uv-index"), SUNRISE("sunrise"), SUNSET("sunset"),
    THERMOMETER("thermometer"), THERMOMETER_WARMER("thermometer-warmer"), THERMOMETER_COLDER("thermometer-colder"),
    RAINDROPS("raindrops"), RAINDROP("raindrop"), UMBRELLA("umbrella"), COMPASS("compass"),
    ALERT("code-orange"), ALERT_YELLOW("code-yellow"), ALERT_RED("code-red"), TIDE("tide-low"), WAVES("tide-high"), STAR("star"),
    CALENDAR("celsius"), NOT_AVAILABLE("not-available"), SUN_HOT("sun-hot"), SNOWFLAKE("snowflake"), HAIL("hail"), HURRICANE("hurricane"), MIST("mist");

    companion object {
        fun forCondition(c: dev.mausam.home.domain.model.WeatherCondition, isDay: Boolean): WeatherIcon = when (c) {
            dev.mausam.home.domain.model.WeatherCondition.CLEAR -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            dev.mausam.home.domain.model.WeatherCondition.PARTLY_CLOUDY -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            dev.mausam.home.domain.model.WeatherCondition.CLOUDY -> CLOUDY
            dev.mausam.home.domain.model.WeatherCondition.OVERCAST -> OVERCAST
            dev.mausam.home.domain.model.WeatherCondition.FOG -> FOG
            dev.mausam.home.domain.model.WeatherCondition.HAZE -> HAZE
            dev.mausam.home.domain.model.WeatherCondition.DRIZZLE -> DRIZZLE
            dev.mausam.home.domain.model.WeatherCondition.RAIN, dev.mausam.home.domain.model.WeatherCondition.HEAVY_RAIN -> RAIN
            dev.mausam.home.domain.model.WeatherCondition.THUNDERSTORM -> THUNDERSTORMS_RAIN
            dev.mausam.home.domain.model.WeatherCondition.SNOW -> SNOW
            dev.mausam.home.domain.model.WeatherCondition.DUST -> DUST
            dev.mausam.home.domain.model.WeatherCondition.WINDY -> WIND
            dev.mausam.home.domain.model.WeatherCondition.UNKNOWN -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
        }
    }
}

/**
 * One data point. `Pending` is the honest state for a source that is not wired yet; it must
 * never carry a fake number.
 */
sealed interface CardValue {
    data class Ready(
        val primary: String,
        val unit: String? = null,
        val secondary: String? = null,
        val trend: Trend = Trend.FLAT,
        val tone: Tone = Tone.NEUTRAL,
        val icon: WeatherIcon? = null,
        /** Raw number behind `primary` so digit-roll animations know direction. */
        val numeric: Double? = null,
        /** Long-form body for the detail sheet, when the card is text-based. */
        val body: String? = null,
    ) : CardValue

    data class Pending(val reason: String = "Data source being added") : CardValue

    data class Unavailable(val message: String) : CardValue
}

enum class HourlyMetric { TEMPERATURE, PRECIPITATION, WIND, UV, HUMIDITY, VISIBILITY, RUN_SCORE }

/** What the expanded sheet shows. */
sealed interface DetailKind {
    data class Hourly(val metric: HourlyMetric) : DetailKind
    data object SevenDay : DetailKind
    data object AqiTrend : DetailKind
    data object Warnings : DetailKind
    data object Destinations : DetailKind
    data object Text : DetailKind
    data object None : DetailKind
}

/** The single action attached to a card. */
sealed interface CardAction {
    data object OpenDetail : CardAction
    data class DeepLink(val uri: String, val label: String) : CardAction
}

/** User-configurable knobs the rule engine needs. */
data class UserSettings(
    val personas: Set<Persona> = setOf(Persona.GENERAL),
    val units: dev.mausam.home.domain.model.Units = dev.mausam.home.domain.model.Units.METRIC,
    val largeText: Boolean = false,
    val effectsEnabled: Boolean = true,
    val language: String = "en",
    val morningBrief: LocalTime = LocalTime.of(7, 0),
    val eveningBrief: LocalTime = LocalTime.of(18, 0),
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(6, 30),
    val commuteStart: LocalTime = LocalTime.of(8, 0),
    val commuteEnd: LocalTime = LocalTime.of(10, 0),
    val schoolStart: LocalTime = LocalTime.of(7, 0),
    val schoolEnd: LocalTime = LocalTime.of(9, 0),
    /** Material You: seed the palette from the wallpaper (API 31+); off uses the IMD blue scheme. */
    val wallpaperColours: Boolean = true,
) {
    fun isQuiet(t: LocalTime): Boolean =
        if (quietStart <= quietEnd) t >= quietStart && t < quietEnd
        else t >= quietStart || t < quietEnd
}

/** Everything a card needs to render, gate itself, and be ranked. Immutable per home open. */
data class CardContext(
    val now: ZonedDateTime,
    val location: Location,
    val bundle: WeatherBundle,
    val distanceToCoastKm: Double,
    val settings: UserSettings,
    val destinations: List<WeatherBundle> = emptyList(),
) {
    val nowInstant: Instant get() = now.toInstant()
    val fmt: Formatter get() = Formatter(settings.units, location.zoneId)
    val isWeekend: Boolean get() = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
}

/**
 * Card definition is data, not UI. Adding a card is adding one spec to [CardRegistry].
 * `fetch` computes the value from the already-loaded bundle so cards render instantly from cache.
 */
data class CardSpec(
    val id: String,
    val persona: Persona,
    val title: String,
    val sourceLabel: String,
    val detail: DetailKind,
    val action: CardAction = CardAction.OpenDetail,
    /** Wide cards span the full grid width; compact ones sit two per row. */
    val wide: Boolean = false,
    val gate: (CardContext) -> Boolean = { true },
    val fetch: (CardContext) -> CardValue,
)

data class RenderedCard(val spec: CardSpec, val value: CardValue, val score: Double, val pinned: Boolean)
