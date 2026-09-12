package dev.mausam.home.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class Units { METRIC, IMPERIAL }

/** Pure formatting helpers shared by cards, briefs and the widget. */
class Formatter(val units: Units = Units.METRIC, val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val hourFmt = DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)

    fun temp(c: Double, withUnit: Boolean = false): String {
        val v = if (units == Units.METRIC) c else c * 9 / 5 + 32
        val unit = if (units == Units.METRIC) "°C" else "°F"
        return "${v.roundToInt()}" + if (withUnit) unit else "°"
    }

    fun tempUnit(): String = if (units == Units.METRIC) "°C" else "°F"

    fun speed(kph: Double): String =
        if (units == Units.METRIC) "${kph.roundToInt()} km/h" else "${(kph * 0.621371).roundToInt()} mph"

    fun distance(km: Double): String = when {
        units == Units.IMPERIAL -> String.format(Locale.ENGLISH, "%.1f mi", km * 0.621371)
        km < 1 -> "${(km * 1000).roundToInt()} m"
        else -> String.format(Locale.ENGLISH, "%.1f km", km)
    }

    fun mm(mm: Double): String = if (mm < 1 && mm > 0) "<1 mm" else "${mm.roundToInt()} mm"

    fun time(instant: Instant): String = timeFmt.format(instant.atZone(zone))

    /** "6 am", "7:30 pm" style used in briefs and windows. */
    fun clock(instant: Instant): String {
        val z = instant.atZone(zone)
        val h = z.hour % 12
        val hh = if (h == 0) 12 else h
        val ampm = if (z.hour < 12) "am" else "pm"
        return if (z.minute == 0) "$hh $ampm" else "$hh:${String.format(Locale.ENGLISH, "%02d", z.minute)} $ampm"
    }

    fun hourLabel(instant: Instant): String = hourFmt.format(instant.atZone(zone)).lowercase()
}
