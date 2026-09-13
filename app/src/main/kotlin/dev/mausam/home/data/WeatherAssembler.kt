package dev.mausam.home.data

import dev.mausam.home.data.cpcb.CpcbAqi
import dev.mausam.home.data.cpcb.CpcbApi
import dev.mausam.home.data.cpcb.CpcbResponse
import dev.mausam.home.data.imd.ImdCodes
import dev.mausam.home.data.net.Http
import dev.mausam.home.data.net.dbl
import dev.mausam.home.data.net.featureProperties
import dev.mausam.home.data.net.int
import dev.mausam.home.data.net.str
import dev.mausam.home.data.openmeteo.OmAirQuality
import dev.mausam.home.data.openmeteo.OmForecast
import dev.mausam.home.data.openmeteo.OmHourly
import dev.mausam.home.data.openmeteo.OmMarine
import dev.mausam.home.data.openmeteo.OpenMeteoApi
import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.geo.PlaceMatch
import dev.mausam.home.domain.geo.Geo
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.AgroAdvisory
import dev.mausam.home.domain.model.AirQuality
import dev.mausam.home.domain.model.AqiPoint
import dev.mausam.home.domain.model.CurrentConditions
import dev.mausam.home.domain.model.DailyForecast
import dev.mausam.home.domain.model.DataKind
import dev.mausam.home.domain.model.HourlyForecast
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.MarineState
import dev.mausam.home.domain.model.SoilState
import dev.mausam.home.domain.model.RainfallSummary
import dev.mausam.home.domain.model.SourceInfo
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.solar.Solar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Identifies one raw upstream payload in the cache. */
enum class SourceKey(val id: String, val national: Boolean = false) {
    IMD_WARNINGS("imd_warnings"),
    IMD_NOWCAST("imd_nowcast"),
    IMD_SYNOP("imd_synop"),
    IMD_METAR("imd_metar"),
    SACHET("sachet", national = true),
    OM_FORECAST("om_forecast"),
    OM_AQI("om_aqi"),
    OM_MARINE("om_marine"),
    CPCB("cpcb");

    companion object {
        fun fromId(id: String): SourceKey? = entries.firstOrNull { it.id == id }
    }
}

data class RawPayload(val json: String, val fetchedAt: Instant, val fromSnapshot: Boolean)

/**
 * Turns raw upstream payloads into one normalised [WeatherBundle]. Pure: no IO, so it is unit
 * tested against the bundled snapshots. Every field records where it came from so cards can
 * label their source honestly, and no field is ever invented.
 */
class WeatherAssembler {
    private val json = Http.json
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val sachetFmt = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH)

    fun assemble(location: Location, raw: Map<SourceKey, RawPayload>, now: Instant): WeatherBundle {
        val zone = location.zoneId
        val sources = mutableMapOf<DataKind, SourceInfo>()

        val forecast = raw[SourceKey.OM_FORECAST]?.let { p -> parse(p) { json.decodeFromString(OmForecast.serializer(), it) } }
        val aqiOm = raw[SourceKey.OM_AQI]?.let { p -> parse(p) { json.decodeFromString(OmAirQuality.serializer(), it) } }
        val marineOm = raw[SourceKey.OM_MARINE]?.let { p -> parse(p) { json.decodeFromString(OmMarine.serializer(), it) } }
        val cpcb = raw[SourceKey.CPCB]?.let { p -> parse(p) { json.decodeFromString(CpcbResponse.serializer(), it) } }
        val synop = raw[SourceKey.IMD_SYNOP]?.let { p -> parse(p) { pickFeature(it, "station_id", location.imdStationId) } }
        val metar = raw[SourceKey.IMD_METAR]?.let { p -> parse(p) { nearestFeature(it, location, "lat", "lon", 120.0) } }
        val warningsRow = raw[SourceKey.IMD_WARNINGS]?.let { p -> parse(p) { pickFeature(it, "District", location.district) } }
        val nowcastRow = raw[SourceKey.IMD_NOWCAST]?.let { p -> parse(p) { pickFeature(it, "District", location.district, "State", location.state) } }
        val sachet = raw[SourceKey.SACHET]?.let { p -> parse(p) { json.decodeFromString(JsonArray.serializer(), it) } }

        // ---- current -------------------------------------------------------------------
        val current = buildCurrent(location, forecast, synop, metar, now, sources, raw)

        // ---- hourly / daily --------------------------------------------------------------
        val hourly = forecast?.hourly?.let { h ->
            h.time.indices.mapNotNull { i ->
                val t = localToInstant(h.time[i], zone) ?: return@mapNotNull null
                val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
                HourlyForecast(
                    time = t, temperatureC = temp, humidityPct = h.humidity.getOrNull(i),
                    precipitationMm = h.precipitation.getOrNull(i) ?: 0.0,
                    precipitationProbabilityPct = h.precipitationProbability.getOrNull(i),
                    windKph = h.windSpeed.getOrNull(i), uvIndex = h.uvIndex.getOrNull(i),
                    cloudCoverPct = h.cloudCover.getOrNull(i), visibilityKm = h.visibility.getOrNull(i)?.let { it / 1000.0 },
                    condition = WeatherCondition.fromWmoCode(h.weatherCode.getOrNull(i)),
                    isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
                )
            }
        } ?: emptyList()
        val today = now.atZone(zone).toLocalDate()
        val allDaily = forecast?.daily?.let { d ->
            d.time.indices.mapNotNull { i ->
                val date = runCatching { LocalDate.parse(d.time[i]) }.getOrNull() ?: return@mapNotNull null
                val min = d.temperatureMin.getOrNull(i) ?: return@mapNotNull null
                val max = d.temperatureMax.getOrNull(i) ?: return@mapNotNull null
                DailyForecast(
                    date = date, minC = min, maxC = max,
                    precipitationMm = d.precipitationSum.getOrNull(i) ?: 0.0,
                    precipitationProbabilityPct = d.precipitationProbabilityMax.getOrNull(i),
                    condition = WeatherCondition.fromWmoCode(d.weatherCode.getOrNull(i)),
                    sunrise = d.sunrise.getOrNull(i)?.let { localTime(it) },
                    sunset = d.sunset.getOrNull(i)?.let { localTime(it) },
                    uvIndexMax = d.uvIndexMax.getOrNull(i), windMaxKph = d.windSpeedMax.getOrNull(i),
                )
            }
        } ?: emptyList()
        val daily = allDaily.filter { !it.date.isBefore(today) }
        raw[SourceKey.OM_FORECAST]?.let {
            sources[DataKind.HOURLY] = SourceInfo(OpenMeteoApi.ATTRIBUTION.tr(), it.fetchedAt, it.fromSnapshot)
            sources[DataKind.DAILY] = SourceInfo(OpenMeteoApi.ATTRIBUTION.tr(), it.fetchedAt, it.fromSnapshot)
        }

        // ---- warnings ------------------------------------------------------------------
        val warnings = mutableListOf<WeatherWarning>()
        warningsRow?.let { warnings += districtWarnings(it, location) }
        nowcastRow?.let { nowcastWarning(it, location)?.let(warnings::add) }
        sachet?.let { warnings += sachetWarnings(it, location) }
        listOfNotNull(raw[SourceKey.IMD_WARNINGS], raw[SourceKey.IMD_NOWCAST], raw[SourceKey.SACHET]).minByOrNull { it.fetchedAt }?.let {
            sources[DataKind.WARNINGS] = SourceInfo("IMD district warnings, IMD nowcast, NDMA SACHET".tr(), it.fetchedAt, it.fromSnapshot)
        }

        // ---- air quality ---------------------------------------------------------------
        val air = buildAirQuality(location, cpcb, aqiOm, zone, now, sources, raw)

        // ---- marine --------------------------------------------------------------------
        val marine = marineOm?.current?.let { c ->
            raw[SourceKey.OM_MARINE]?.let { sources[DataKind.MARINE] = SourceInfo("Open-Meteo marine model".tr(), it.fetchedAt, it.fromSnapshot) }
            MarineState(
                time = localToInstant(c.time, zone) ?: now, waveHeightM = c.waveHeight, wavePeriodS = c.wavePeriod,
                waveDirectionDeg = c.waveDirection, swellHeightM = c.swellWaveHeight, seaSurfaceTempC = c.seaSurfaceTemperature,
            )
        }

        // ---- rainfall ------------------------------------------------------------------
        val rainfall = buildRainfall(synop, allDaily, today, now, sources, raw)
        val soil = forecast?.hourly?.let { buildSoil(it, zone, now, sources, raw[SourceKey.OM_FORECAST]) }

        val fetchedAt = raw.values.maxOfOrNull { it.fetchedAt } ?: now
        return WeatherBundle(
            location = location, current = current, hourly = hourly, daily = daily, warnings = warnings.distinctBy { it.id },
            airQuality = air, marine = marine, rainfall = rainfall, advisory = null as AgroAdvisory?, soil = soil,
            fetchedAt = fetchedAt, sources = sources,
        )
    }

    // ------------------------------------------------------------------------------------
    private fun buildCurrent(
        location: Location, forecast: OmForecast?, synop: JsonObject?, metar: JsonObject?, now: Instant,
        sources: MutableMap<DataKind, SourceInfo>, raw: Map<SourceKey, RawPayload>,
    ): CurrentConditions? {
        val zone = location.zoneId
        val om = forecast?.current
        val synopTime = synop?.let { synopObservationTime(it) }
        val synopFresh = synop != null && synopTime != null && Duration.between(synopTime, now).abs() <= Duration.ofHours(3)
        val metarTime = metar?.let { metarObservationTime(it) }
        val metarFresh = metar != null && metarTime != null && Duration.between(metarTime, now).abs() <= Duration.ofHours(3)

        val temp = (if (synopFresh) synop!!.dbl("dbtemp") else null) ?: (if (metarFresh) metar!!.dbl("temp") else null) ?: om?.temperature ?: return null
        val humidity = (if (synopFresh) synop!!.int("rh") else null) ?: (if (metarFresh) metar!!.int("rh") else null) ?: om?.humidity
        val windKph = (if (synopFresh) synop!!.dbl("windsp")?.let { knotsToKph(it) } else null) ?: om?.windSpeed
        val windDir = (if (synopFresh) synop!!.int("winddir") else null) ?: om?.windDirection
        val gust = (if (synopFresh) synop!!.dbl("10gust")?.let { knotsToKph(it) } else null) ?: om?.windGusts
        val visibilityKm = (if (metarFresh) metar!!.dbl("visibility") else null)?.let { it / 1000.0 }
            ?: (if (synopFresh) synop!!.dbl("visibility") else null)?.let { it / 1000.0 }
            ?: forecast?.hourly?.let { h -> nearestHourIndex(h.time, now, zone)?.let { i -> h.visibility.getOrNull(i)?.let { it / 1000.0 } } }
        val pressure = (if (synopFresh) synop!!.dbl("mslp") else null) ?: om?.pressureMsl
        val cloud = om?.cloudCover ?: (if (synopFresh) synop!!.int("nebulosity")?.let { (it * 100 / 8).coerceIn(0, 100) } else null)
        val precip = (if (synopFresh) synop!!.dbl("3hrlyrain") else null) ?: om?.precipitation

        val isDay = om?.isDay?.let { it == 1 }
            ?: Solar.sunTimes(location.latitude, location.longitude, now.atZone(zone).toLocalDate(), zone).isDay(now)
        val condition = (if (metarFresh) WeatherCondition.fromText(metar!!.str("weather")).takeIf { it != WeatherCondition.UNKNOWN } else null)
            ?: (if (synopFresh) WeatherCondition.fromText(ImdCodes.synopWeatherText(synop!!.int("weather"))).takeIf { it != WeatherCondition.UNKNOWN } else null)
            ?: WeatherCondition.fromWmoCode(om?.weatherCode)
        val uv = forecast?.hourly?.let { h -> nearestHourIndex(h.time, now, zone)?.let { i -> h.uvIndex.getOrNull(i) } }
        val feels = om?.apparentTemperature

        val label = buildString {
            if (synopFresh) append("IMD %s observation".trf(synop!!.str("station") ?: "station".tr()))
            else if (metarFresh) append("IMD %s METAR".trf(metar!!.str("station_name") ?: "airport".tr()))
            if (isNotEmpty() && om != null) append(" + ")
            if (om != null) append(OpenMeteoApi.ATTRIBUTION.tr())
        }
        val payload = (if (synopFresh) raw[SourceKey.IMD_SYNOP] else null) ?: (if (metarFresh) raw[SourceKey.IMD_METAR] else null) ?: raw[SourceKey.OM_FORECAST]
        payload?.let { sources[DataKind.CURRENT] = SourceInfo(label, it.fetchedAt, it.fromSnapshot) }

        val obsTime = (if (synopFresh) synopTime else null) ?: (if (metarFresh) metarTime else null) ?: om?.let { localToInstant(it.time, zone) } ?: now
        return CurrentConditions(
            time = obsTime, temperatureC = temp, feelsLikeC = feels, humidityPct = humidity, windKph = windKph,
            windDirectionDeg = windDir, gustKph = gust, visibilityKm = visibilityKm, cloudCoverPct = cloud, uvIndex = uv,
            pressureHpa = pressure, precipitationMm = precip, condition = condition, isDay = isDay,
        )
    }

    private fun buildAirQuality(
        location: Location, cpcb: CpcbResponse?, om: OmAirQuality?, zone: ZoneId, now: Instant,
        sources: MutableMap<DataKind, SourceInfo>, raw: Map<SourceKey, RawPayload>,
    ): AirQuality? {
        // History always comes from the model feed (CPCB gives no history), on the Indian AQI scale.
        val history = om?.hourly?.let { h ->
            h.time.indices.mapNotNull { i ->
                val t = localToInstant(h.time[i], zone) ?: return@mapNotNull null
                if (t.isAfter(now) || t.isBefore(now.minus(Duration.ofHours(25)))) return@mapNotNull null
                val a = IndianAqi.compute(h.pm25.getOrNull(i), h.pm10.getOrNull(i)) ?: return@mapNotNull null
                AqiPoint(t, a, h.pm25.getOrNull(i), h.pm10.getOrNull(i))
            }
        } ?: emptyList()

        // Prefer the nearest CPCB station. Its values are sub-indices, so the AQI is the worst of
        // them and no concentration is claimed.
        val station = cpcb?.records?.let { CpcbAqi.nearest(it, location.latitude, location.longitude) }
        if (station != null && CpcbAqi.plausible(station)) {
            val updated = station.lastUpdate?.let { parseCpcbTime(it) }
            raw[SourceKey.CPCB]?.let { sources[DataKind.AIR_QUALITY] = SourceInfo("${CpcbApi.ATTRIBUTION.tr()} (${station.name})", it.fetchedAt, it.fromSnapshot) }
            return AirQuality(updated ?: now, station.aqi, null, null, station.dominant, history, station.name)
        }
        val c = om?.current ?: return null
        val aqi = IndianAqi.compute(c.pm25, c.pm10) ?: return null
        raw[SourceKey.OM_AQI]?.let { sources[DataKind.AIR_QUALITY] = SourceInfo("Open-Meteo air-quality model (CAMS)".tr(), it.fetchedAt, it.fromSnapshot) }
        return AirQuality(localToInstant(c.time, zone) ?: now, aqi, c.pm25, c.pm10, IndianAqi.dominant(c.pm25, c.pm10), history, null)
    }

    /** Depth-weighted 0–9 cm and 9–27 cm model soil water at the current hour, plus the 24 h trend. */
    private fun buildSoil(h: OmHourly, zone: ZoneId, now: Instant, sources: MutableMap<DataKind, SourceInfo>, payload: RawPayload?): SoilState? {
        if (h.soil3to9.isEmpty()) return null
        val times = h.time.map { localToInstant(it, zone) }
        val i = times.indexOfLast { it != null && !it.isAfter(now) }.takeIf { it >= 0 } ?: return null
        fun top(at: Int): Double? {
            val a = h.soil0to1.getOrNull(at); val b = h.soil1to3.getOrNull(at); val c = h.soil3to9.getOrNull(at)
            if (a == null || b == null || c == null) return null
            return (a * 1 + b * 2 + c * 6) / 9.0 * 100.0
        }
        val topNow = top(i) ?: return null
        val later = top(minOf(i + 24, h.time.size - 1))
        payload?.let { sources[DataKind.SOIL] = SourceInfo("Open-Meteo soil model".tr(), it.fetchedAt, it.fromSnapshot) }
        return SoilState(
            topPct = topNow,
            rootPct = h.soil9to27.getOrNull(i)?.let { it * 100.0 },
            temperatureC = h.soilTemperature6cm.getOrNull(i),
            trend = later?.let { it - topNow },
            asOf = times[i] ?: now,
        )
    }

    private fun buildRainfall(
        synop: JsonObject?, allDaily: List<DailyForecast>, today: LocalDate, now: Instant,
        sources: MutableMap<DataKind, SourceInfo>, raw: Map<SourceKey, RawPayload>,
    ): RainfallSummary? {
        val obs24h = synop?.dbl("24hrlyrain")
        val pastWeek = allDaily.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(7)) }
        val past7 = pastWeek.takeIf { it.isNotEmpty() }?.sumOf { it.precipitationMm }
        val todayModel = allDaily.firstOrNull { it.date == today }?.precipitationMm
        val todayMm = obs24h ?: todayModel
        if (todayMm == null && past7 == null) return null
        val label = listOfNotNull(
            if (obs24h != null) "IMD station 24 h rainfall".tr() else null,
            if (past7 != null || obs24h == null) OpenMeteoApi.ATTRIBUTION.tr() else null,
        ).joinToString(" + ")
        val payload = (if (obs24h != null) raw[SourceKey.IMD_SYNOP] else null) ?: raw[SourceKey.OM_FORECAST]
        payload?.let { sources[DataKind.RAINFALL] = SourceInfo(label, it.fetchedAt, it.fromSnapshot) }
        return RainfallSummary(todayMm, past7, null, now)
    }

    // ------------------------------------------------------------------------------------ warnings
    private fun districtWarnings(row: JsonObject, location: Location): List<WeatherWarning> {
        val issued = row.str("Date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
        val district = row.str("District") ?: location.district ?: return emptyList()
        return (1..5).mapNotNull { day ->
            val severity = ImdCodes.warningSeverity(row.int("Day${day}_Color")) ?: return@mapNotNull null
            val codes = row.str("Day_$day")
            val event = ImdCodes.eventFromCodes(codes) ?: "Weather warning".tr()
            val text = row.str("Day${day}_text")?.replace("\\r\\n", " ")?.replace("\r\n", " ")?.replace("\n", " ")?.replace(Regex("\\s+"), " ")?.trim()
            val date = issued.plusDays((day - 1).toLong())
            WeatherWarning(
                id = "imd-warn-${district.lowercase().replace(' ', '-')}-$date",
                severity = severity, event = event,
                headline = if (day == 1) event else "%s on %s".trf(event, date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.tr()),
                description = sentenceCase(text ?: event),
                area = titleCase(district), onset = date.atStartOfDay(ist).toInstant(), expires = date.plusDays(1).atStartOfDay(ist).toInstant(),
                source = "IMD district warning".tr(),
            )
        }
    }

    private fun nowcastWarning(row: JsonObject, location: Location): WeatherWarning? {
        val severity = ImdCodes.nowcastSeverity(row.int("Color")) ?: return null
        val message = row.str("message") ?: return null
        val date = row.str("Date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val onset = row.str("toi")?.let { hhmm(it) }?.let { date.atTime(it).atZone(ist).toInstant() }
        val expires = row.str("vupto")?.let { hhmm(it) }?.let { t ->
            val d = if (onset != null && date.atTime(t).atZone(ist).toInstant().isBefore(onset)) date.plusDays(1) else date
            d.atTime(t).atZone(ist).toInstant()
        }
        val cats = (1..19).filter { it != 16 && (row.int("cat$it") ?: 0) > 0 }.mapNotNull { ImdCodes.nowcastCategory(it) }
        val district = row.str("District") ?: location.district ?: ""
        return WeatherWarning(
            id = "imd-nowcast-${district.lowercase().replace(' ', '-')}-${row.str("update_time") ?: date}",
            severity = severity, event = cats.firstOrNull() ?: "Nowcast".tr(),
            headline = sentenceCase(message), description = listOfNotNull(sentenceCase(message), row.str("impact"), row.str("action")).joinToString("\n\n"),
            area = titleCase(district), onset = onset, expires = expires, source = "IMD nowcast".tr(),
        )
    }

    private fun sachetWarnings(alerts: JsonArray, location: Location): List<WeatherWarning> {
        return alerts.mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            val area = a.str("area_description") ?: ""
            val centroid = a.str("centroid")?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
            val near = centroid != null && centroid.size == 2 && Geo.haversineKm(location.latitude, location.longitude, centroid[1], centroid[0]) <= 40.0
            val mentions = PlaceMatch.mentions(area, location.district) || PlaceMatch.mentions(area, location.name)
            if (!near && !mentions) return@mapNotNull null
            val severity = WarningSeverity.fromText(a.str("severity_color")) ?: return@mapNotNull null
            val id = a.str("identifier") ?: return@mapNotNull null
            WeatherWarning(
                id = "sachet-$id", severity = severity, event = a.str("disaster_type") ?: "Alert".tr(),
                headline = a.str("warning_message")?.let(::firstSentence) ?: a.str("disaster_type") ?: "Alert".tr(),
                description = a.str("warning_message") ?: "", area = area,
                onset = a.str("effective_start_time")?.let(::parseSachetTime), expires = a.str("effective_end_time")?.let(::parseSachetTime),
                source = a.str("alert_source")?.let { "NDMA SACHET · %s".trf(it) } ?: "NDMA SACHET".tr(),
            )
        }
    }

    // ------------------------------------------------------------------------------------ helpers
    private inline fun <T> parse(p: RawPayload, block: (String) -> T): T? = runCatching { block(p.json) }.getOrNull()

    /** The row for [value]; among same-name rows the one whose [tieKey] matches [tieValue] wins. */
    private fun pickFeature(jsonText: String, key: String, value: String?, tieKey: String? = null, tieValue: String? = null): JsonObject? {
        val props = json.decodeFromString(JsonObject.serializer(), jsonText).featureProperties()
        if (props.isEmpty()) return null
        if (value == null) return props.singleOrNull()
        val named = props.filter { it.str(key).equals(value, ignoreCase = true) }
        if (named.size > 1 && tieKey != null && tieValue != null) {
            named.firstOrNull { it.str(tieKey).equals(tieValue, ignoreCase = true) }?.let { return it }
        }
        return named.firstOrNull() ?: props.singleOrNull()
    }

    private fun nearestFeature(jsonText: String, location: Location, latKey: String, lonKey: String, maxKm: Double): JsonObject? {
        val props = json.decodeFromString(JsonObject.serializer(), jsonText).featureProperties()
        if (props.size == 1) return props.first()
        return props.mapNotNull { p ->
            val lat = p.dbl(latKey) ?: return@mapNotNull null
            val lon = p.dbl(lonKey) ?: return@mapNotNull null
            p to Geo.haversineKm(location.latitude, location.longitude, lat, lon)
        }.filter { it.second <= maxKm }.minByOrNull { it.second }?.first
    }

    private fun nearestHourIndex(times: List<String>, now: Instant, zone: ZoneId): Int? {
        var best: Int? = null
        var bestDiff = Long.MAX_VALUE
        times.forEachIndexed { i, t ->
            val inst = localToInstant(t, zone) ?: return@forEachIndexed
            val diff = kotlin.math.abs(inst.epochSecond - now.epochSecond)
            if (diff < bestDiff) { bestDiff = diff; best = i }
        }
        return best?.takeIf { bestDiff <= 3600 }
    }

    private fun localToInstant(s: String, zone: ZoneId): Instant? =
        runCatching { LocalDateTime.parse(s).atZone(zone).toInstant() }.getOrNull()

    private fun localTime(s: String): LocalTime? = runCatching { LocalDateTime.parse(s).toLocalTime() }.getOrNull()

    private fun hhmm(s: String): LocalTime? {
        val digits = s.filter { it.isDigit() }.padStart(4, '0')
        if (digits.length != 4) return null
        return runCatching { LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt()) }.getOrNull()
    }

    /** SYNOP: `dat` "2026-09-12Z" + `utc` hour. */
    private fun synopObservationTime(p: JsonObject): Instant? {
        val date = p.str("dat")?.removeSuffix("Z")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val hour = p.int("utc") ?: return null
        return date.atTime(hour.coerceIn(0, 23), 0).toInstant(ZoneOffset.UTC)
    }

    /** METAR: `dat` "2026-09-12Z" + `utc` "11:00". */
    private fun metarObservationTime(p: JsonObject): Instant? {
        val date = p.str("dat")?.removeSuffix("Z")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val t = p.str("utc")?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() } ?: return null
        return date.atTime(t).toInstant(ZoneOffset.UTC)
    }

    /** "Sat Sep 12 16:33:00 IST 2026": the zone token is dropped and IST assumed. */
    private fun parseSachetTime(s: String): Instant? {
        val cleaned = s.replace(Regex("\\s[A-Z]{2,5}\\s(\\d{4})$"), " $1")
        return runCatching { LocalDateTime.parse(cleaned, sachetFmt).atZone(ist).toInstant() }.getOrNull()
    }

    private fun parseCpcbTime(s: String): Instant? = runCatching {
        LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")).atZone(ist).toInstant()
    }.getOrNull()

    private fun knotsToKph(kt: Double) = kt * 1.852

    private fun sentenceCase(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        val lower = if (t.count { it.isUpperCase() } > t.length / 2) t.lowercase() else t
        return lower.replaceFirstChar { it.uppercase() }
    }

    private fun firstSentence(s: String): String = s.trim().split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.take(140) ?: s.take(140)

    private fun titleCase(s: String): String = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
