package dev.mausam.home.domain.aqi

import kotlin.math.roundToInt

/**
 * CPCB National Air Quality Index (2014) sub-index calculation for PM2.5 and PM10 (24-hour
 * breakpoints). The overall AQI is the worst sub-index. Values above 500 are clamped.
 */
object IndianAqi {
    private data class Band(val cLo: Double, val cHi: Double, val iLo: Int, val iHi: Int)

    private val pm25Bands = listOf(
        Band(0.0, 30.0, 0, 50), Band(30.0, 60.0, 51, 100), Band(60.0, 90.0, 101, 200),
        Band(90.0, 120.0, 201, 300), Band(120.0, 250.0, 301, 400), Band(250.0, 500.0, 401, 500),
    )
    private val pm10Bands = listOf(
        Band(0.0, 50.0, 0, 50), Band(50.0, 100.0, 51, 100), Band(100.0, 250.0, 101, 200),
        Band(250.0, 350.0, 201, 300), Band(350.0, 430.0, 301, 400), Band(430.0, 600.0, 401, 500),
    )

    private fun subIndex(c: Double, bands: List<Band>): Int {
        if (c <= 0) return 0
        val b = bands.firstOrNull { c <= it.cHi } ?: return 500
        val i = (b.iHi - b.iLo).toDouble() / (b.cHi - b.cLo) * (c - b.cLo) + b.iLo
        return i.roundToInt().coerceIn(0, 500)
    }

    fun fromPm25(pm25: Double): Int = subIndex(pm25, pm25Bands)
    fun fromPm10(pm10: Double): Int = subIndex(pm10, pm10Bands)

    fun compute(pm25: Double?, pm10: Double?): Int? {
        val a = pm25?.let(::fromPm25)
        val b = pm10?.let(::fromPm10)
        return listOfNotNull(a, b).maxOrNull()
    }

    fun dominant(pm25: Double?, pm10: Double?): String? {
        val a = pm25?.let(::fromPm25) ?: -1
        val b = pm10?.let(::fromPm10) ?: -1
        return when {
            a < 0 && b < 0 -> null
            a >= b -> "PM2.5"
            else -> "PM10"
        }
    }

    enum class Category(val label: String, val maxAqi: Int, val advice: String) {
        GOOD("Good", 50, "Minimal impact"),
        SATISFACTORY("Satisfactory", 100, "Minor breathing discomfort to sensitive people"),
        MODERATE("Moderate", 200, "Breathing discomfort to people with lung or heart disease"),
        POOR("Poor", 300, "Breathing discomfort to most people on prolonged exposure"),
        VERY_POOR("Very poor", 400, "Respiratory illness on prolonged exposure"),
        SEVERE("Severe", Int.MAX_VALUE, "Affects healthy people; serious for those with disease");
    }

    fun category(aqi: Int): Category = Category.entries.first { aqi <= it.maxAqi }
}
