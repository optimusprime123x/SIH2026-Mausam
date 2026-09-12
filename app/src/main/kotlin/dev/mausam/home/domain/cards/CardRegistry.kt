package dev.mausam.home.domain.cards

import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.model.DataKind
import dev.mausam.home.domain.model.SoilCategory
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.domain.solar.Solar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Every card the home can show. Adding a card is adding one spec here.
 * Ordering within this list is the tie-breaker after ranking.
 */
object CardRegistry {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    /** "Mon" → translated day name. */
    private fun day(d: LocalDate): String = dayFmt.format(d).tr()

    /** "Mon 14 Sep" with the day and month names translated, digits Latin. */
    private fun date(d: LocalDate): String = "%s %d %s".trf(day(d), d.dayOfMonth, monthFmt.format(d).tr())

    private fun toneForSeverity(s: WarningSeverity) = when (s) {
        WarningSeverity.YELLOW -> Tone.CAUTION
        WarningSeverity.ORANGE -> Tone.WARNING
        WarningSeverity.RED -> Tone.DANGER
    }

    private fun warningsMatching(ctx: CardContext, vararg keywords: String): List<WeatherWarning> =
        ctx.bundle.activeWarnings(ctx.nowInstant).filter { w ->
            keywords.any { k -> w.event.contains(k, true) || w.headline.contains(k, true) }
        }

    private fun warningCard(w: WeatherWarning, ctx: CardContext): CardValue.Ready = CardValue.Ready(
        primary = w.severity.label.tr(),
        secondary = w.headline.tr(),
        tone = toneForSeverity(w.severity),
        icon = WeatherIcon.ALERT,
        body = w.description.tr(),
    )

    private fun CardContext.aqiNow(): Int? = bundle.airQuality?.aqi

    // ---------------------------------------------------------------- Health-conscious
    val aqi: CardSpec = CardSpec(
        id = "health.aqi", persona = Persona.GENERAL, titleEn = "Air quality",
        sourceLabelEn = "CPCB via data.gov.in", detail = DetailKind.AqiTrend, kind = DataKind.AIR_QUALITY, wide = true,
    ) { ctx ->
        val aq = ctx.bundle.airQuality ?: return@CardSpec CardValue.Pending("Air quality data source being added".tr())
        val cat = IndianAqi.category(aq.aqi)
        val earlier = aq.history24h.firstOrNull()?.aqi
        val trend = when {
            earlier == null -> Trend.FLAT
            aq.aqi > earlier + 10 -> Trend.UP
            aq.aqi < earlier - 10 -> Trend.DOWN
            else -> Trend.FLAT
        }
        val tone = when (cat) {
            IndianAqi.Category.GOOD, IndianAqi.Category.SATISFACTORY -> Tone.GOOD
            IndianAqi.Category.MODERATE -> Tone.CAUTION
            IndianAqi.Category.POOR -> Tone.WARNING
            else -> Tone.DANGER
        }
        val delta = earlier?.let { aq.aqi - it }
        val deltaText = when {
            delta == null -> "24 h trend unavailable".tr()
            delta > 0 -> "up %d since yesterday".trf(delta)
            delta < 0 -> "down %d since yesterday".trf(-delta)
            else -> "unchanged since yesterday".tr()
        }
        CardValue.Ready(
            primary = aq.aqi.toString(), unit = "AQI",
            secondary = "${cat.label.tr()} · $deltaText",
            trend = trend, tone = tone, icon = WeatherIcon.AIR, numeric = aq.aqi.toDouble(),
            body = cat.advice.tr(),
        )
    }

    val humidity: CardSpec = CardSpec(
        id = "health.humidity", persona = Persona.HEALTH, titleEn = "Humidity",
        sourceLabelEn = "Current observation", detail = DetailKind.Hourly(HourlyMetric.HUMIDITY), kind = DataKind.CURRENT,
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation".tr())
        val h = cur.humidityPct ?: return@CardSpec CardValue.Unavailable("Humidity not reported".tr())
        val dew = Thermal.dewPointC(cur.temperatureC, h.toDouble())
        val label = when {
            h >= 80 -> "Muggy"
            h >= 60 -> "Humid"
            h >= 30 -> "Comfortable"
            else -> "Dry"
        }
        CardValue.Ready(
            primary = h.toString(), unit = "%", secondary = "%s · dew point %s".trf(label.tr(), ctx.fmt.temp(dew)),
            tone = if (h >= 80 || h < 20) Tone.CAUTION else Tone.GOOD, icon = WeatherIcon.HUMIDITY, numeric = h.toDouble(),
        )
    }

    val uv: CardSpec = CardSpec(
        id = "health.uv", persona = Persona.HEALTH, titleEn = "UV index",
        sourceLabelEn = "Estimated from sun and cloud cover", detail = DetailKind.Hourly(HourlyMetric.UV), kind = DataKind.HOURLY,
    ) { ctx ->
        val reported = ctx.bundle.current?.uvIndex
        val cloud = ctx.bundle.current?.cloudCoverPct
        val uvi = reported ?: UvEstimate.estimate(ctx.location.latitude, ctx.location.longitude, ctx.nowInstant, cloud)
        val (label, tone) = UvEstimate.label(uvi)
        CardValue.Ready(
            primary = uvi.roundToInt().toString(), secondary = if (reported == null) "%s · estimate".trf(label.tr()) else label.tr(),
            tone = tone, icon = WeatherIcon.UV_INDEX, numeric = uvi,
            body = "Estimated from the sun's height and today's cloud cover, not measured at a station.".tr(),
        )
    }

    val pollen: CardSpec = CardSpec(
        id = "health.pollen", persona = Persona.HEALTH, titleEn = "Pollen",
        sourceLabelEn = "Coming soon", detail = DetailKind.None,
    ) { CardValue.Pending("Pollen data source being added".tr()) }

    // ---------------------------------------------------------------- Outdoor fitness
    val sun: CardSpec = CardSpec(
        id = "fitness.sun", persona = Persona.FITNESS, titleEn = "Sunrise & sunset",
        sourceLabelEn = "", detail = DetailKind.Text, kind = DataKind.DAILY,
    ) { ctx ->
        val times = Solar.sunTimes(ctx.location.latitude, ctx.location.longitude, ctx.now.toLocalDate(), ctx.location.zoneId)
        val rise = times.sunrise?.let(ctx.fmt::time) ?: "—"
        val set = times.sunset?.let(ctx.fmt::time) ?: "—"
        val isDay = times.isDay(ctx.nowInstant)
        val next = if (isDay) "Sunset %s".trf(set) else "Sunrise %s".trf(rise)
        val dayLen = if (times.sunrise != null && times.sunset != null) {
            val mins = (times.sunset.epochSecond - times.sunrise.epochSecond) / 60
            "%d h %d min of daylight".trf(mins / 60, mins % 60)
        } else null
        CardValue.Ready(
            primary = if (isDay) set else rise, secondary = listOfNotNull(next, dayLen).joinToString(" · "),
            icon = if (isDay) WeatherIcon.SUNSET else WeatherIcon.SUNRISE,
            body = "Sunrise %s · Sunset %s. NOAA solar position algorithm, computed on device.".trf(rise, set),
        )
    }

    val runWindow: CardSpec = CardSpec(
        id = "fitness.run", persona = Persona.FITNESS, titleEn = "Best running hours",
        sourceLabelEn = "Based on temperature, humidity, UV and air quality", detail = DetailKind.Hourly(HourlyMetric.RUN_SCORE), kind = DataKind.HOURLY, wide = true,
    ) { ctx ->
        val w = RunScore.bestWindow(ctx.bundle.hourly, ctx.aqiNow(), ctx.now)
            ?: return@CardSpec CardValue.Unavailable("No more daylight hours today".tr())
        val tone = when {
            w.score >= 75 -> Tone.GOOD
            w.score >= 60 -> Tone.NEUTRAL
            else -> Tone.CAUTION
        }
        val quality = when {
            w.score >= 75 -> "Great conditions"
            w.score >= 60 -> "Good conditions"
            w.score >= 40 -> "Fair conditions"
            else -> "Poor conditions"
        }
        CardValue.Ready(
            primary = ctx.fmt.clockRange(w.start, w.end),
            secondary = quality.tr(),
            tone = tone, icon = WeatherIcon.RUN, numeric = w.score.toDouble(),
        )
    }

    val wind: CardSpec = CardSpec(
        id = "fitness.wind", persona = Persona.FITNESS, titleEn = "Wind",
        sourceLabelEn = "Current observation", detail = DetailKind.Hourly(HourlyMetric.WIND), kind = DataKind.CURRENT,
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation".tr())
        val kph = cur.windKph ?: return@CardSpec CardValue.Unavailable("Wind not reported".tr())
        val dir = cur.windDirectionDeg?.let { compass(it) }
        val gust = cur.gustKph?.let { "gusts %s".trf(ctx.fmt.speed(it)) }
        CardValue.Ready(
            primary = ctx.fmt.speed(kph).substringBefore(' '), unit = ctx.fmt.speed(kph).substringAfter(' '),
            secondary = listOfNotNull(dir?.let { "from %s".trf(it.tr()) }, gust).joinToString(" · ").ifEmpty { null },
            tone = if (kph >= 40) Tone.CAUTION else Tone.NEUTRAL, icon = WeatherIcon.WIND, numeric = kph,
        )
    }

    val heatAlert: CardSpec = CardSpec(
        id = "fitness.heat", persona = Persona.FITNESS, titleEn = "Heat alert",
        sourceLabelEn = "District warnings", detail = DetailKind.Warnings, kind = DataKind.HOURLY,
        gate = { ctx -> warningsMatching(ctx, "heat").isNotEmpty() || (ctx.bundle.daily.firstOrNull()?.maxC ?: 0.0) >= 40 },
    ) { ctx ->
        val w = warningsMatching(ctx, "heat").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val max = ctx.bundle.daily.first().maxC
            CardValue.Ready(
                primary = ctx.fmt.temp(max), secondary = "Forecast high, avoid midday exertion".tr(),
                tone = Tone.WARNING, icon = WeatherIcon.THERMOMETER_WARMER, numeric = max,
            )
        }
    }

    // ---------------------------------------------------------------- Beach
    val seaState: CardSpec = CardSpec(
        id = "beach.sea", persona = Persona.BEACH, titleEn = "Sea state",
        sourceLabelEn = "Ocean state forecast", detail = DetailKind.Text, kind = DataKind.MARINE, wide = true,
        gate = { it.distanceToCoastKm <= 30.0 },
    ) { ctx ->
        val m = ctx.bundle.marine ?: return@CardSpec CardValue.Pending("Ocean state forecast being added".tr())
        val h = m.waveHeightM ?: return@CardSpec CardValue.Pending("Wave height not available".tr())
        val state = when {
            h < 0.5 -> "Calm"
            h < 1.25 -> "Slight"
            h < 2.5 -> "Moderate"
            h < 4.0 -> "Rough"
            else -> "Very rough"
        }
        val tone = when {
            h < 1.25 -> Tone.GOOD
            h < 2.5 -> Tone.CAUTION
            else -> Tone.WARNING
        }
        val extras = listOfNotNull(
            m.wavePeriodS?.let { "%d s period".trf(it.roundToInt()) },
            m.seaSurfaceTempC?.let { "water %s".trf(ctx.fmt.temp(it)) },
        ).joinToString(" · ")
        CardValue.Ready(
            primary = String.format(Locale.ENGLISH, "%.1f", h), unit = "m", secondary = "${state.tr()} · $extras",
            tone = tone, icon = WeatherIcon.WAVES, numeric = h,
        )
    }

    val tides: CardSpec = CardSpec(
        id = "beach.tides", persona = Persona.BEACH, titleEn = "Tides",
        sourceLabelEn = "Coming soon", detail = DetailKind.None,
        gate = { it.distanceToCoastKm <= 30.0 },
    ) { CardValue.Pending("Tide predictions being added".tr()) }

    // ---------------------------------------------------------------- Travellers
    val destinations: CardSpec = CardSpec(
        id = "travel.destinations", persona = Persona.TRAVEL, titleEn = "Saved destinations",
        sourceLabelEn = "7-day city forecast", detail = DetailKind.Destinations, kind = DataKind.DAILY, wide = true,
    ) { ctx ->
        if (ctx.destinations.isEmpty()) return@CardSpec CardValue.Unavailable("No saved destinations yet, add a city in Locations!".tr())
        val first = ctx.destinations.first()
        val today = first.daily.firstOrNull()
        val more = ctx.destinations.size - 1
        val brief = today?.let { "${ctx.fmt.temp(it.minC)} – ${ctx.fmt.temp(it.maxC)} · ${it.condition.label().tr()}" } ?: "No forecast cached".tr()
        CardValue.Ready(
            primary = first.location.name,
            secondary = when {
                more == 1 -> "%s · +%d more saved destination".trf(brief, more)
                more > 1 -> "%s · +%d more saved destinations".trf(brief, more)
                else -> brief
            },
            icon = today?.let { WeatherIcon.forCondition(it.condition, true) },
            body = ctx.destinations.joinToString("\n") { b ->
                val d = b.daily.firstOrNull()
                "${b.location.name}: " + (d?.let { "${ctx.fmt.temp(it.minC)}–${ctx.fmt.temp(it.maxC)}, ${it.condition.label().tr()}" } ?: "—")
            },
        )
    }

    val destinationSevere: CardSpec = CardSpec(
        id = "travel.severe", persona = Persona.TRAVEL, titleEn = "Severe weather at destination",
        sourceLabelEn = "District warnings", detail = DetailKind.Warnings, kind = DataKind.WARNINGS,
        gate = { ctx -> ctx.destinations.any { it.activeWarnings(ctx.nowInstant).isNotEmpty() } },
    ) { ctx ->
        val (bundle, w) = ctx.destinations.flatMap { b -> b.activeWarnings(ctx.nowInstant).map { b to it } }
            .maxBy { it.second.severity.rank }
        CardValue.Ready(
            primary = w.severity.label.tr(), secondary = "${bundle.location.name}: ${w.headline.tr()}",
            tone = toneForSeverity(w.severity), icon = WeatherIcon.ALERT, body = w.description.tr(),
        )
    }

    val packing: CardSpec = CardSpec(
        id = "travel.packing", persona = Persona.TRAVEL, titleEn = "Packing tip",
        sourceLabelEn = "Based on the 7-day forecast", detail = DetailKind.SevenDay, kind = DataKind.DAILY,
    ) { ctx ->
        val target = ctx.destinations.firstOrNull() ?: ctx.bundle
        val tip = Packing.tip(target.daily) ?: return@CardSpec CardValue.Unavailable("No forecast cached".tr())
        CardValue.Ready(primary = tip, secondary = "For %s".trf(target.location.name), icon = WeatherIcon.LUGGAGE)
    }

    // ---------------------------------------------------------------- Parents
    val schoolRun: CardSpec = CardSpec(
        id = "parents.school", persona = Persona.PARENTS, titleEn = "School commute",
        sourceLabelEn = "Hourly forecast, 7 to 9 am", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION), kind = DataKind.HOURLY,
    ) { ctx ->
        val s = ctx.settings
        val r = Commute.rainIn(ctx.bundle.hourly, ctx.now, s.schoolStart, s.schoolEnd, ctx.location.zoneId)
        if (r.hours.isEmpty()) return@CardSpec CardValue.Unavailable("Window has passed for today".tr())
        val temp = r.hours.first().temperatureC
        when {
            r.likely -> CardValue.Ready("Rain likely".tr(), secondary = "%d%% chance, %s · umbrellas".trf(r.maxProbabilityPct, ctx.fmt.mm(r.totalMm)), tone = Tone.WARNING, icon = WeatherIcon.UMBRELLA, numeric = r.maxProbabilityPct.toDouble())
            r.possible -> CardValue.Ready("Rain possible".tr(), secondary = "%d%% chance · %s".trf(r.maxProbabilityPct, ctx.fmt.temp(temp)), tone = Tone.CAUTION, icon = WeatherIcon.RAINDROPS, numeric = r.maxProbabilityPct.toDouble())
            else -> CardValue.Ready("Dry".tr(), secondary = "%s at drop-off".trf(ctx.fmt.temp(temp)), tone = Tone.GOOD, icon = WeatherIcon.forCondition(r.hours.first().condition, true), numeric = r.maxProbabilityPct.toDouble())
        }
    }

    val rainNext3h: CardSpec = CardSpec(
        id = "parents.rain3h", persona = Persona.PARENTS, titleEn = "Rain next 3 hours",
        sourceLabelEn = "Hourly forecast", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION), kind = DataKind.HOURLY,
    ) { ctx ->
        val r = Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3)
        if (r.hours.isEmpty()) return@CardSpec CardValue.Unavailable("No hourly forecast cached".tr())
        val tone = when {
            r.likely -> Tone.WARNING
            r.possible -> Tone.CAUTION
            else -> Tone.GOOD
        }
        CardValue.Ready(
            primary = "${r.maxProbabilityPct}", unit = "%",
            secondary = if (r.totalMm > 0) "%s expected".trf(ctx.fmt.mm(r.totalMm)) else "No rain expected".tr(),
            tone = tone, icon = WeatherIcon.RAINDROPS, numeric = r.maxProbabilityPct.toDouble(),
        )
    }

    val severeWarnings: CardSpec = CardSpec(
        id = "parents.severe", persona = Persona.PARENTS, titleEn = "Severe warnings",
        sourceLabelEn = "District warnings", detail = DetailKind.Warnings, kind = DataKind.WARNINGS, wide = true,
        gate = { it.bundle.activeWarnings(it.nowInstant).isNotEmpty() },
    ) { ctx -> warningCard(ctx.bundle.activeWarnings(ctx.nowInstant).first(), ctx) }

    // ---------------------------------------------------------------- Agriculture
    val rainfall: CardSpec = CardSpec(
        id = "agri.rainfall", persona = Persona.AGRICULTURE, titleEn = "Rainfall",
        sourceLabelEn = "District rainfall", detail = DetailKind.Text, kind = DataKind.RAINFALL,
    ) { ctx ->
        val r = ctx.bundle.rainfall ?: return@CardSpec CardValue.Pending("District rainfall source being added".tr())
        val today = r.todayMm ?: return@CardSpec CardValue.Pending("District rainfall source being added".tr())
        val week = r.past7DaysMm?.let { "%s past week".trf(ctx.fmt.mm(it)) }
        val normal = r.normalPast7DaysMm?.let { n -> r.past7DaysMm?.let { w -> if (n > 0) "%d%% vs normal".trf(((w - n) / n * 100).roundToInt()) else null } }
        CardValue.Ready(
            primary = today.roundToInt().toString(), unit = "mm today".tr(),
            secondary = listOfNotNull(week, normal).joinToString(" · ").ifEmpty { null },
            icon = WeatherIcon.RAINDROP, numeric = today,
        )
    }

    val soil: CardSpec = CardSpec(
        id = "agri.soil", persona = Persona.AGRICULTURE, titleEn = "Soil moisture",
        sourceLabelEn = "Open-Meteo soil model", detail = DetailKind.Text, kind = DataKind.SOIL,
    ) { ctx ->
        val s = ctx.bundle.soil ?: return@CardSpec CardValue.Unavailable("Soil moisture needs a fresh forecast".tr())
        val label = when (s.category) {
            SoilCategory.VERY_DRY -> "Very dry soil".tr()
            SoilCategory.DRY -> "Dry soil".tr()
            SoilCategory.ADEQUATE -> "Adequate moisture".tr()
            SoilCategory.WET -> "Wet soil".tr()
            SoilCategory.SATURATED -> "Saturated soil".tr()
        }
        val tone = when (s.category) {
            SoilCategory.VERY_DRY -> Tone.WARNING
            SoilCategory.DRY -> Tone.CAUTION
            SoilCategory.ADEQUATE -> Tone.GOOD
            SoilCategory.WET -> Tone.NEUTRAL
            SoilCategory.SATURATED -> Tone.CAUTION
        }
        val trend = s.trend?.let { d -> when { d >= 3 -> "rising".tr(); d <= -3 -> "drying".tr(); else -> "steady".tr() } }
        val root = s.rootPct?.let { "root zone %d%%".trf(it.roundToInt()) }
        val advice = when (s.category) {
            SoilCategory.VERY_DRY, SoilCategory.DRY -> "Top soil is drying out; irrigate unless rain is due in the next day.".tr()
            SoilCategory.ADEQUATE -> "Soil water in the top 9 cm is in a comfortable range for most crops.".tr()
            SoilCategory.WET -> "Top soil is wet; hold irrigation and check drainage in low fields.".tr()
            SoilCategory.SATURATED -> "Soil is saturated; avoid field traffic and watch for waterlogging.".tr()
        }
        CardValue.Ready(
            primary = s.topPct.roundToInt().toString(), unit = "% top 9 cm".tr(),
            secondary = listOfNotNull(label, root, trend).joinToString(" · "),
            tone = tone, icon = WeatherIcon.SOIL, numeric = s.topPct,
            body = listOfNotNull(
                advice,
                s.temperatureC?.let { "Soil temperature at 6 cm: %s.".trf(ctx.fmt.temp(it, true)) },
                "Volumetric water content from the Open-Meteo soil model; field capacity varies with soil type, so treat thresholds as a guide.".tr(),
            ).joinToString(" "),
        )
    }

    val rainOutlook: CardSpec = CardSpec(
        id = "agri.outlook", persona = Persona.AGRICULTURE, titleEn = "7-day rain outlook",
        sourceLabelEn = "City forecast", detail = DetailKind.SevenDay, kind = DataKind.DAILY, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached".tr())
        val total = week.sumOf { it.precipitationMm }
        val wet = week.filter { (it.precipitationProbabilityPct ?: 0) >= 40 || it.precipitationMm >= 2 }
        val advice = when {
            total >= 25 -> "skip watering"
            total >= 5 -> "light watering only"
            else -> "water as usual"
        }.tr()
        CardValue.Ready(
            primary = ctx.fmt.mm(total),
            secondary = if (wet.size == 1) "%d wet day · %s".trf(wet.size, advice) else "%d wet days · %s".trf(wet.size, advice),
            tone = if (total >= 50) Tone.CAUTION else Tone.NEUTRAL, icon = WeatherIcon.RAIN, numeric = total,
        )
    }

    val frost: CardSpec = CardSpec(
        id = "agri.frost", persona = Persona.AGRICULTURE, titleEn = "Frost & cold wave",
        sourceLabelEn = "District warnings and forecast minimum", detail = DetailKind.Warnings, kind = DataKind.DAILY,
        gate = { ctx -> warningsMatching(ctx, "cold", "frost").isNotEmpty() || ctx.bundle.daily.take(3).any { it.minC < 5 } },
    ) { ctx ->
        val w = warningsMatching(ctx, "cold", "frost").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val d = ctx.bundle.daily.take(3).minBy { it.minC }
            CardValue.Ready(
                primary = ctx.fmt.temp(d.minC), secondary = "Low on %s night · cover seedlings".trf(day(d.date)),
                tone = Tone.WARNING, icon = WeatherIcon.THERMOMETER_COLDER, numeric = d.minC,
            )
        }
    }

    val agromet: CardSpec = CardSpec(
        id = "agri.advisory", persona = Persona.AGRICULTURE, titleEn = "Agromet advisory",
        sourceLabelEn = "IMD agromet bulletin", detail = DetailKind.Text, kind = DataKind.ADVISORY, wide = true,
    ) { ctx ->
        val a = ctx.bundle.advisory ?: return@CardSpec CardValue.Pending("Agromet advisory source being added".tr())
        CardValue.Ready(primary = a.title, secondary = a.body.take(120), icon = WeatherIcon.AGRO, body = a.body)
    }

    // ---------------------------------------------------------------- Commuters
    val visibility: CardSpec = CardSpec(
        id = "commute.visibility", persona = Persona.COMMUTERS, titleEn = "Visibility & fog",
        sourceLabelEn = "Current observation and warnings", detail = DetailKind.Hourly(HourlyMetric.VISIBILITY), kind = DataKind.CURRENT,
        gate = { ctx ->
            val cur = ctx.bundle.current
            (cur?.visibilityKm ?: 99.0) < 4.0 || cur?.condition?.isLowVisibility == true || warningsMatching(ctx, "fog").isNotEmpty()
        },
    ) { ctx ->
        val cur = ctx.bundle.current
        val km = cur?.visibilityKm
        val w = warningsMatching(ctx, "fog").firstOrNull()
        val label = when {
            km == null -> cur?.condition?.label() ?: "Low visibility"
            km < 0.2 -> "Very dense fog"
            km < 0.5 -> "Dense fog"
            km < 1.0 -> "Moderate fog"
            km < 4.0 -> "Shallow fog or haze"
            else -> "Clear"
        }.tr()
        CardValue.Ready(
            primary = km?.let(ctx.fmt::distance) ?: label,
            secondary = w?.headline?.tr() ?: "%s · use low beams, keep distance".trf(label),
            tone = if ((km ?: 1.0) < 1.0 || w?.severity == WarningSeverity.ORANGE || w?.severity == WarningSeverity.RED) Tone.WARNING else Tone.CAUTION,
            icon = WeatherIcon.FOG, numeric = km, body = w?.description?.tr(),
        )
    }

    val stormAlert: CardSpec = CardSpec(
        id = "commute.storm", persona = Persona.COMMUTERS, titleEn = "Storm alert",
        sourceLabelEn = "Nowcast and district warnings", detail = DetailKind.Warnings, kind = DataKind.WARNINGS,
        gate = { ctx ->
            warningsMatching(ctx, "thunder", "storm", "squall", "lightning").isNotEmpty() ||
                Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3).hours.any { it.condition == WeatherCondition.THUNDERSTORM }
        },
    ) { ctx ->
        val w = warningsMatching(ctx, "thunder", "storm", "squall", "lightning").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val h = Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3).hours.first { it.condition == WeatherCondition.THUNDERSTORM }
            CardValue.Ready("Thunderstorm".tr(), secondary = "Expected around %s".trf(ctx.fmt.clock(h.time)), tone = Tone.WARNING, icon = WeatherIcon.THUNDERSTORMS_RAIN)
        }
    }

    val leaveEarlier: CardSpec = CardSpec(
        id = "commute.leave", persona = Persona.COMMUTERS, titleEn = "Leave earlier",
        sourceLabelEn = "Rain overlapping your commute", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION), kind = DataKind.HOURLY,
        gate = { ctx ->
            !ctx.isWeekend && Commute.rainIn(ctx.bundle.hourly, ctx.now, ctx.settings.commuteStart, ctx.settings.commuteEnd, ctx.location.zoneId).possible
        },
    ) { ctx ->
        val r = Commute.rainIn(ctx.bundle.hourly, ctx.now, ctx.settings.commuteStart, ctx.settings.commuteEnd, ctx.location.zoneId)
        val minutes = if (r.likely) 20 else 10
        CardValue.Ready(
            primary = "+%d min".trf(minutes),
            secondary = "%d%% rain chance %s".trf(r.maxProbabilityPct, ctx.fmt.clockRange(r.hours.first().time, r.hours.last().time.plusSeconds(3600))),
            tone = if (r.likely) Tone.WARNING else Tone.CAUTION, icon = WeatherIcon.COMMUTE, numeric = minutes.toDouble(),
        )
    }

    val traffic: CardSpec = CardSpec(
        id = "commute.traffic", persona = Persona.COMMUTERS, titleEn = "Traffic",
        sourceLabelEn = "Opens Maps", detail = DetailKind.None,
        action = CardAction.DeepLink("geo:0,0?q=traffic", "Open in Maps"),
    ) { ctx ->
        CardValue.Ready(primary = "Live traffic".tr(), secondary = "Open Maps for %s".trf(ctx.location.name), icon = WeatherIcon.TRAFFIC)
    }

    // ---------------------------------------------------------------- Event planners
    val outlook7d: CardSpec = CardSpec(
        id = "events.outlook", persona = Persona.EVENTS, titleEn = "7-day outlook",
        sourceLabelEn = "City forecast", detail = DetailKind.SevenDay, kind = DataKind.DAILY, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached".tr())
        val wettest = week.maxBy { it.precipitationProbabilityPct ?: 0 }
        val driest = week.minBy { it.precipitationProbabilityPct ?: 0 }
        CardValue.Ready(
            primary = "${ctx.fmt.temp(week.minOf { it.minC })} – ${ctx.fmt.temp(week.maxOf { it.maxC })}",
            secondary = "Wettest %s %d%% · driest %s".trf(day(wettest.date), wettest.precipitationProbabilityPct ?: 0, day(driest.date)),
            icon = WeatherIcon.forCondition(week.first().condition, true),
        )
    }

    val comfort: CardSpec = CardSpec(
        id = "events.comfort", persona = Persona.EVENTS, titleEn = "Comfort index",
        sourceLabelEn = "Heat index and humidex", detail = DetailKind.Hourly(HourlyMetric.TEMPERATURE), kind = DataKind.CURRENT,
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation".tr())
        val h = cur.humidityPct ?: return@CardSpec CardValue.Unavailable("Humidity not reported".tr())
        val c = Thermal.comfort(cur.temperatureC, h.toDouble())
        val feels = maxOf(Thermal.heatIndexC(cur.temperatureC, h.toDouble()), Thermal.humidexC(cur.temperatureC, h.toDouble()))
        CardValue.Ready(
            primary = c.label.tr(), secondary = "Feels like %s".trf(ctx.fmt.temp(feels)),
            tone = c.tone, icon = WeatherIcon.COMFORT, numeric = feels,
        )
    }

    val bestDay: CardSpec = CardSpec(
        id = "events.bestday", persona = Persona.EVENTS, titleEn = "Best day this week",
        sourceLabelEn = "Based on rain, heat and wind", detail = DetailKind.SevenDay, kind = DataKind.DAILY,
    ) { ctx ->
        val (d, s) = DayScore.best(ctx.bundle.daily) ?: return@CardSpec CardValue.Unavailable("No forecast cached".tr())
        CardValue.Ready(
            primary = date(d.date),
            secondary = "%s · %d%% rain".trf(ctx.fmt.temp(d.maxC), d.precipitationProbabilityPct ?: 0),
            tone = if (s >= 70) Tone.GOOD else Tone.NEUTRAL, icon = WeatherIcon.STAR, numeric = s.toDouble(),
        )
    }

    // ---------------------------------------------------------------- General
    val hourly: CardSpec = CardSpec(
        id = "general.hourly", persona = Persona.GENERAL, titleEn = "Next 24 hours",
        sourceLabelEn = "Hourly forecast", detail = DetailKind.Hourly(HourlyMetric.TEMPERATURE), kind = DataKind.HOURLY, wide = true,
    ) { ctx ->
        val next = ctx.bundle.hourlyFrom(ctx.nowInstant, 24)
        if (next.isEmpty()) return@CardSpec CardValue.Unavailable("No hourly forecast cached".tr())
        val hi = next.maxBy { it.temperatureC }
        val lo = next.minBy { it.temperatureC }
        val rainAt = next.firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        CardValue.Ready(
            primary = "${ctx.fmt.temp(lo.temperatureC)} – ${ctx.fmt.temp(hi.temperatureC)}",
            secondary = rainAt?.let { "Rain likely from %s".trf(ctx.fmt.clock(it.time)) } ?: "No rain expected".tr(),
            icon = WeatherIcon.forCondition(next.first().condition, next.first().isDay),
        )
    }

    val sevenDay: CardSpec = CardSpec(
        id = "general.week", persona = Persona.GENERAL, titleEn = "7-day forecast",
        sourceLabelEn = "City forecast", detail = DetailKind.SevenDay, kind = DataKind.DAILY, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached".tr())
        val tomorrow = week.getOrNull(1) ?: week.first()
        CardValue.Ready(
            primary = "${ctx.fmt.temp(tomorrow.minC)} – ${ctx.fmt.temp(tomorrow.maxC)}",
            secondary = "Tomorrow · %s · %d%% rain".trf(tomorrow.condition.label().tr(), tomorrow.precipitationProbabilityPct ?: 0),
            icon = WeatherIcon.forCondition(tomorrow.condition, true),
        )
    }

    val warnings: CardSpec = CardSpec(
        id = "general.warnings", persona = Persona.GENERAL, titleEn = "Warnings",
        sourceLabelEn = "District warnings", detail = DetailKind.Warnings, kind = DataKind.WARNINGS, wide = true,
        gate = { it.bundle.activeWarnings(it.nowInstant).isNotEmpty() },
    ) { ctx -> warningCard(ctx.bundle.activeWarnings(ctx.nowInstant).first(), ctx) }

    val all: List<CardSpec> = listOf(
        warnings, severeWarnings,
        aqi, humidity, uv, pollen,
        sun, runWindow, wind, heatAlert,
        seaState, tides,
        destinations, destinationSevere, packing,
        schoolRun, rainNext3h,
        rainfall, soil, rainOutlook, frost, agromet,
        visibility, stormAlert, leaveEarlier, traffic,
        outlook7d, comfort, bestDay,
        hourly, sevenDay,
    )

    fun byId(id: String): CardSpec? = all.firstOrNull { it.id == id }

    fun forPersona(p: Persona): List<CardSpec> = all.filter { it.persona == p }

    private fun compass(deg: Int): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg % 360 + 360) % 360 / 45.0).roundToInt() % 8]
    }
}
