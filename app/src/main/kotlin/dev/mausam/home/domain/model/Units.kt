package dev.mausam.home.domain.model

import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dev.mausam.home.domain.i18n.L10n
import dev.mausam.home.domain.i18n.Lang
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

    /**
     * "6 am – 10 am". Hindi reads the way people say it: "सुबह 6 से 10 बजे" within one part of
     * the day, "सुबह 6 से दोपहर 2 बजे" across two.
     */
    fun clockRange(from: Instant, to: Instant): String {
        if (L10n.lang != Lang.HI) return "${clock(from)} – ${clock(to)}"
        val a = from.atZone(zone); val b = to.atZone(zone)
        val ha = hm(a); val hb = hm(b)
        return if (dayPart(a.hour) == dayPart(b.hour)) "${dayPart(a.hour)} $ha से $hb बजे" else "${dayPart(a.hour)} $ha से ${dayPart(b.hour)} $hb बजे"
    }

    private fun hm(z: java.time.ZonedDateTime): String =
        if (z.minute == 0) "${twelveHour(z.hour)}" else "${twelveHour(z.hour)}:${String.format(Locale.ENGLISH, "%02d", z.minute)}"

    /** सुबह / दोपहर / शाम / रात, the everyday words rather than the formal पूर्वाह्न / अपराह्न. */
    private fun dayPart(hour: Int): String = when (hour) {
        in 4..11 -> "सुबह"
        in 12..15 -> "दोपहर"
        in 16..19 -> "शाम"
        else -> "रात"
    }

    /** Template with the day-half word; Hindi uses the part-of-day word instead. */
    private fun meridiem(hour: Int): String =
        if (L10n.lang == Lang.HI) "${dayPart(hour)} %s बजे" else if (hour < 12) "%s am" else "%s pm"
}
