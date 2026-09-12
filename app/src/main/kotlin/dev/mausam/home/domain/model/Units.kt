package dev.mausam.home.domain.model

import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class Units { METRIC, IMPERIAL }

/** Pure formatting helpers shared by cards, briefs and the widget. */
class Formatter(val units: Units = Units.METRIC, val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun temp(c: Double, withUnit: Boolean = false): String {
        val v = if (units == Units.METRIC) c else c * 9 / 5 + 32
        return "${v.roundToInt()}" + if (withUnit) tempUnit() else "°"
    }

    fun tempUnit(): String = (if (units == Units.METRIC) "°C" else "°F").tr()

    fun speed(kph: Double): String =
        if (units == Units.METRIC) "%d km/h".trf(kph.roundToInt()) else "%d mph".trf((kph * 0.621371).roundToInt())

    fun distance(km: Double): String = when {
        units == Units.IMPERIAL -> "%.1f mi".trf(km * 0.621371)
        km < 1 -> "%d m".trf((km * 1000).roundToInt())
        else -> "%.1f km".trf(km)
    }

    fun mm(mm: Double): String = if (mm < 1 && mm > 0) "<1 mm".tr() else "%d mm".trf(mm.roundToInt())

    fun time(instant: Instant): String = timeFmt.format(instant.atZone(zone))

    /** "6 am", "7:30 pm" style used in briefs and windows. */
    fun clock(instant: Instant): String {
        val z = instant.atZone(zone)
        val hm = if (z.minute == 0) "${twelveHour(z.hour)}" else "${twelveHour(z.hour)}:${String.format(Locale.ENGLISH, "%02d", z.minute)}"
        return meridiem(z.hour).trf(hm)
    }

    /** "6 am", "12 pm": the hour alone, as the hourly strip labels it. */
    fun hourLabel(instant: Instant): String {
        val z = instant.atZone(zone)
        return meridiem(z.hour).trf(twelveHour(z.hour))
    }

    private fun twelveHour(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

    /** Template with the day-half word; Hindi swaps it for the IMD-style पूर्वाह्न / अपराह्न prefix. */
    private fun meridiem(hour: Int): String = if (hour < 12) "%s am" else "%s pm"
}
