package dev.mausam.home.domain.solar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class SunTimes(val sunrise: Instant?, val sunset: Instant?, val solarNoon: Instant) {
    fun sunriseLocal(zone: ZoneId): LocalTime? = sunrise?.atZone(zone)?.toLocalTime()
    fun sunsetLocal(zone: ZoneId): LocalTime? = sunset?.atZone(zone)?.toLocalTime()
    fun isDay(at: Instant): Boolean {
        val r = sunrise ?: return true
        val s = sunset ?: return true
        return !at.isBefore(r) && at.isBefore(s)
    }
}

/**
 * NOAA solar position algorithm (Meeus-based; the same equations as the NOAA Solar Calculator).
 * Accurate to roughly a minute for sunrise/sunset and a fraction of a degree for elevation.
 * Computed entirely on device: no data source is needed for sunrise, sunset or UV estimation.
 */
object Solar {
    private const val ZENITH_OFFICIAL = 90.833 // includes refraction + solar radius

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val d = date.dayOfMonth
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private class Ephemeris(t: Double) {
        val geomMeanLong = ((280.46646 + t * (36000.76983 + t * 0.0003032)) % 360 + 360) % 360
        val geomMeanAnom = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccent = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val sunEqCtr = sin(rad(geomMeanAnom)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(rad(2 * geomMeanAnom)) * (0.019993 - 0.000101 * t) +
            sin(rad(3 * geomMeanAnom)) * 0.000289
        val trueLong = geomMeanLong + sunEqCtr
        val appLong = trueLong - 0.00569 - 0.00478 * sin(rad(125.04 - 1934.136 * t))
        val meanObliq = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliqCorr = meanObliq + 0.00256 * cos(rad(125.04 - 1934.136 * t))
        val declination = deg(asin(sin(rad(obliqCorr)) * sin(rad(appLong))))
        val eqTimeMinutes: Double
        init {
            val y = tan(rad(obliqCorr / 2)).let { it * it }
            val l0 = rad(geomMeanLong)
            val m = rad(geomMeanAnom)
            eqTimeMinutes = 4 * deg(
                y * sin(2 * l0) - 2 * eccent * sin(m) + 4 * eccent * y * sin(m) * cos(2 * l0) -
                    0.5 * y * y * sin(4 * l0) - 1.25 * eccent * eccent * sin(2 * m),
            )
        }
    }

    /** Sunrise, sunset and solar noon for the civil date at the given location. */
    fun sunTimes(latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId): SunTimes {
        // Evaluate the ephemeris at local noon so the day boundary follows the user's zone.
        val localNoon = date.atTime(12, 0).atZone(zone).toInstant()
        val jd = julianDay(localNoon.atZone(ZoneOffset.UTC).toLocalDate()) +
            (localNoon.atZone(ZoneOffset.UTC).toLocalTime().toSecondOfDay() / 86400.0)
        val t = (jd - 2451545.0) / 36525.0
        val e = Ephemeris(t)
        val noonMinutesUtc = 720 - 4 * longitude - e.eqTimeMinutes
        val cosHa = cos(rad(ZENITH_OFFICIAL)) / (cos(rad(latitude)) * cos(rad(e.declination))) -
            tan(rad(latitude)) * tan(rad(e.declination))
        val utcMidnight = localNoon.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val noon = utcMidnight.plusSeconds((noonMinutesUtc * 60).toLong())
        if (cosHa < -1 || cosHa > 1) return SunTimes(null, null, noon) // polar day/night
        val ha = deg(acos(cosHa))
        val rise = utcMidnight.plusSeconds(((noonMinutesUtc - ha * 4) * 60).toLong())
        val set = utcMidnight.plusSeconds(((noonMinutesUtc + ha * 4) * 60).toLong())
        return SunTimes(rise, set, noon)
    }

    /** Solar elevation angle in degrees above the horizon (negative below), no refraction. */
    fun elevation(latitude: Double, longitude: Double, at: Instant): Double {
        val utc = at.atZone(ZoneOffset.UTC)
        val jd = julianDay(utc.toLocalDate()) + utc.toLocalTime().toSecondOfDay() / 86400.0
        val t = (jd - 2451545.0) / 36525.0
        val e = Ephemeris(t)
        val minutes = utc.toLocalTime().toSecondOfDay() / 60.0
        var trueSolarTime = (minutes + e.eqTimeMinutes + 4 * longitude) % 1440
        if (trueSolarTime < 0) trueSolarTime += 1440
        var hourAngle = trueSolarTime / 4 - 180
        if (hourAngle < -180) hourAngle += 360
        val cosZenith = sin(rad(latitude)) * sin(rad(e.declination)) +
            cos(rad(latitude)) * cos(rad(e.declination)) * cos(rad(hourAngle))
        val zenith = deg(acos(cosZenith.coerceIn(-1.0, 1.0)))
        return 90 - zenith
    }

    /** Compass bearing of the sun, degrees clockwise from north. */
    fun azimuth(latitude: Double, longitude: Double, at: Instant): Double {
        val utc = at.atZone(ZoneOffset.UTC)
        val jd = julianDay(utc.toLocalDate()) + utc.toLocalTime().toSecondOfDay() / 86400.0
        val t = (jd - 2451545.0) / 36525.0
        val e = Ephemeris(t)
        val minutes = utc.toLocalTime().toSecondOfDay() / 60.0
        var tst = (minutes + e.eqTimeMinutes + 4 * longitude) % 1440
        if (tst < 0) tst += 1440
        var ha = tst / 4 - 180
        if (ha < -180) ha += 360
        val lat = rad(latitude)
        val dec = rad(e.declination)
        val h = rad(ha)
        val az = atan2(sin(h), cos(h) * sin(lat) - tan(dec) * cos(lat))
        return (deg(az) + 180 + 360) % 360
    }
}
