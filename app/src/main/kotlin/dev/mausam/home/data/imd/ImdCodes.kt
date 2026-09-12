package dev.mausam.home.data.imd

import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.model.WarningSeverity

/**
 * Code tables lifted from the JavaScript of IMD's own map pages (districtWiseWarningGIS.php and
 * districtWiseNowcastGIS.php) and cross-checked against the live layer data.
 */
object ImdCodes {
    /**
     * `Day_N` warning codes on `district_warnings_india`. Values are the English source phrases;
     * translate at lookup ([eventFromCodes], [nowcastCategory]), never here, so a language change
     * is picked up on the next assemble instead of being frozen at class init.
     */
    val warningCategories: Map<Int, String> = mapOf(
        1 to "No warning", 2 to "Heavy rain", 3 to "Heavy snow", 4 to "Thunderstorm & lightning, squall",
        5 to "Hailstorm", 6 to "Dust storm", 7 to "Dust raising winds", 8 to "Strong surface winds",
        9 to "Heat wave", 10 to "Hot day", 11 to "Warm night", 12 to "Cold wave", 13 to "Cold day",
        14 to "Ground frost", 15 to "Fog", 16 to "Very heavy rain", 17 to "Extremely heavy rain",
    )

    /** `catN` flags on `NowcastWarningDistrict`. */
    val nowcastCategories: Map<Int, String> = mapOf(
        1 to "No warning", 2 to "Light rain", 3 to "Light snow", 4 to "Light thunderstorm",
        5 to "Slight dust storm", 6 to "Low lightning probability", 7 to "Moderate rain", 8 to "Moderate snow",
        9 to "Moderate thunderstorm", 10 to "Moderate dust storm", 11 to "Moderate lightning probability",
        12 to "Heavy rain", 13 to "Heavy snow", 14 to "Severe thunderstorm", 15 to "Very severe thunderstorm",
        17 to "Thunderstorm with hail", 18 to "Severe dust storm", 19 to "High lightning probability",
    )

    /**
     * `DayN_Color` on the district-warning layer counts DOWN in severity: 1 red, 2 orange, 3 yellow,
     * 4 green (no warning), 0 no data. Verified: code "1" (no warning) pairs with colour 4 on 270 of
     * 281 districts, and very-heavy-rain codes pair with colour 2.
     */
    fun warningSeverity(color: Int?): WarningSeverity? = when (color) {
        1 -> WarningSeverity.RED
        2 -> WarningSeverity.ORANGE
        3 -> WarningSeverity.YELLOW
        else -> null
    }

    /**
     * `Color` on the nowcast layer counts UP: 1 green (no nowcast), 2 yellow, 3 orange, 4 red.
     * Verified: every district carrying a live `message` had colour 2 or 3; 323 silent ones had 1.
     */
    fun nowcastSeverity(color: Int?): WarningSeverity? = when (color) {
        2 -> WarningSeverity.YELLOW
        3 -> WarningSeverity.ORANGE
        4 -> WarningSeverity.RED
        else -> null
    }

    fun eventFromCodes(dayCodes: String?): String? {
        val names = dayCodes?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it > 1 }
            ?.mapNotNull { warningCategories[it] } ?: emptyList()
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.tr() }
    }

    /** The `catN` phrase for [index], translated for display. */
    fun nowcastCategory(index: Int): String? = nowcastCategories[index]?.tr()

    /**
     * SYNOP `weather` present-weather code (WMO 4677 ww) → coarse condition. Deliberately NOT
     * translated: the result feeds [dev.mausam.home.domain.model.WeatherCondition.fromText], an
     * English keyword parser, and never reaches the screen.
     */
    fun synopWeatherText(ww: Int?): String? = when (ww) {
        null -> null
        in 0..3 -> "Clear"
        in 4..9 -> "Haze"
        10 -> "Mist"
        11, 12 -> "Fog"
        in 13..19 -> "Thunderstorm"
        in 20..29 -> "Recent rain"
        in 30..39 -> "Dust storm"
        in 40..49 -> "Fog"
        in 50..59 -> "Drizzle"
        in 60..69 -> "Rain"
        in 70..79 -> "Snow"
        in 80..89 -> "Rain showers"
        in 90..99 -> "Thunderstorm"
        else -> null
    }
}
