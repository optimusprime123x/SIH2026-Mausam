package dev.mausam.home.domain.cards

import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.domain.solar.Solar
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Every card the home can show. Adding a card is adding one spec here.
 * Ordering within this list is the tie-breaker after ranking.
 */
object CardRegistry {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

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
        primary = w.severity.label,
        secondary = w.headline,
        tone = toneForSeverity(w.severity),
        icon = WeatherIcon.ALERT,
        body = w.description,
    )

    private fun CardContext.aqiNow(): Int? = bundle.airQuality?.aqi

    // ---------------------------------------------------------------- Health-conscious
    val aqi: CardSpec = CardSpec(
        id = "health.aqi", persona = Persona.HEALTH, title = "Air quality",
        sourceLabel = "CPCB via data.gov.in", detail = DetailKind.AqiTrend, wide = true,
    ) { ctx ->
        val aq = ctx.bundle.airQuality ?: return@CardSpec CardValue.Pending("Air quality data source being added")
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
            delta == null -> "24 h trend unavailable"
            delta > 0 -> "up $delta since yesterday"
            delta < 0 -> "down ${-delta} since yesterday"
            else -> "unchanged since yesterday"
        }
        CardValue.Ready(
            primary = aq.aqi.toString(), unit = "AQI",
            secondary = "${cat.label} · $deltaText",
            trend = trend, tone = tone, icon = WeatherIcon.AIR, numeric = aq.aqi.toDouble(),
            body = cat.advice,
        )
    }

    val humidity: CardSpec = CardSpec(
        id = "health.humidity", persona = Persona.HEALTH, title = "Humidity",
        sourceLabel = "Current observation", detail = DetailKind.Hourly(HourlyMetric.HUMIDITY),
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation")
        val h = cur.humidityPct ?: return@CardSpec CardValue.Unavailable("Humidity not reported")
        val dew = Thermal.dewPointC(cur.temperatureC, h.toDouble())
        val label = when {
            h >= 80 -> "Muggy"
            h >= 60 -> "Humid"
            h >= 30 -> "Comfortable"
            else -> "Dry"
        }
        CardValue.Ready(
            primary = h.toString(), unit = "%", secondary = "$label · dew point ${ctx.fmt.temp(dew)}",
            tone = if (h >= 80 || h < 20) Tone.CAUTION else Tone.GOOD, icon = WeatherIcon.HUMIDITY, numeric = h.toDouble(),
        )
    }

    val uv: CardSpec = CardSpec(
        id = "health.uv", persona = Persona.HEALTH, title = "UV index",
        sourceLabel = "Estimated from sun and cloud cover", detail = DetailKind.Hourly(HourlyMetric.UV),
    ) { ctx ->
        val reported = ctx.bundle.current?.uvIndex
        val cloud = ctx.bundle.current?.cloudCoverPct
        val uvi = reported ?: UvEstimate.estimate(ctx.location.latitude, ctx.location.longitude, ctx.nowInstant, cloud)
        val (label, tone) = UvEstimate.label(uvi)
        val suffix = if (reported == null) " · estimate" else ""
        CardValue.Ready(
            primary = uvi.roundToInt().toString(), secondary = "$label$suffix",
            tone = tone, icon = WeatherIcon.UV_INDEX, numeric = uvi,
            body = "Estimated from the sun's height and today's cloud cover, not measured at a station.",
        )
    }

    val pollen: CardSpec = CardSpec(
        id = "health.pollen", persona = Persona.HEALTH, title = "Pollen",
        sourceLabel = "Coming soon", detail = DetailKind.None,
    ) { CardValue.Pending("Pollen data source being added") }

    // ---------------------------------------------------------------- Outdoor fitness
    val sun: CardSpec = CardSpec(
        id = "fitness.sun", persona = Persona.FITNESS, title = "Sunrise & sunset",
        sourceLabel = "", detail = DetailKind.Text,
    ) { ctx ->
        val times = Solar.sunTimes(ctx.location.latitude, ctx.location.longitude, ctx.now.toLocalDate(), ctx.location.zoneId)
        val rise = times.sunrise?.let(ctx.fmt::time) ?: "—"
        val set = times.sunset?.let(ctx.fmt::time) ?: "—"
        val isDay = times.isDay(ctx.nowInstant)
        val next = if (isDay) "Sunset $set" else "Sunrise $rise"
        val dayLen = if (times.sunrise != null && times.sunset != null) {
            val mins = (times.sunset.epochSecond - times.sunrise.epochSecond) / 60
            "${mins / 60} h ${mins % 60} min of daylight"
        } else null
        CardValue.Ready(
            primary = if (isDay) set else rise, secondary = listOfNotNull(next, dayLen).joinToString(" · "),
            icon = if (isDay) WeatherIcon.SUNSET else WeatherIcon.SUNRISE,
            body = "Sunrise $rise · Sunset $set. NOAA solar position algorithm, computed on device.",
        )
    }

    val runWindow: CardSpec = CardSpec(
        id = "fitness.run", persona = Persona.FITNESS, title = "Best running hours",
        sourceLabel = "Based on temperature, humidity, UV and air quality", detail = DetailKind.Hourly(HourlyMetric.RUN_SCORE), wide = true,
    ) { ctx ->
        val w = RunScore.bestWindow(ctx.bundle.hourly, ctx.aqiNow(), ctx.now)
            ?: return@CardSpec CardValue.Unavailable("No more daylight hours today")
        val tone = when {
            w.score >= 75 -> Tone.GOOD
            w.score >= 60 -> Tone.NEUTRAL
            else -> Tone.CAUTION
        }
        val quality = when {
            w.score >= 75 -> "Great"
            w.score >= 60 -> "Good"
            w.score >= 40 -> "Fair"
            else -> "Poor"
        }
        CardValue.Ready(
            primary = "${ctx.fmt.clock(w.start)} – ${ctx.fmt.clock(w.end)}",
            secondary = "$quality conditions",
            tone = tone, icon = WeatherIcon.RUN, numeric = w.score.toDouble(),
        )
    }

    val wind: CardSpec = CardSpec(
        id = "fitness.wind", persona = Persona.FITNESS, title = "Wind",
        sourceLabel = "Current observation", detail = DetailKind.Hourly(HourlyMetric.WIND),
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation")
        val kph = cur.windKph ?: return@CardSpec CardValue.Unavailable("Wind not reported")
        val dir = cur.windDirectionDeg?.let { compass(it) }
        val gust = cur.gustKph?.let { "gusts ${ctx.fmt.speed(it)}" }
        CardValue.Ready(
            primary = ctx.fmt.speed(kph).substringBefore(' '), unit = ctx.fmt.speed(kph).substringAfter(' '),
            secondary = listOfNotNull(dir?.let { "from $it" }, gust).joinToString(" · ").ifEmpty { null },
            tone = if (kph >= 40) Tone.CAUTION else Tone.NEUTRAL, icon = WeatherIcon.WIND, numeric = kph,
        )
    }

    val heatAlert: CardSpec = CardSpec(
        id = "fitness.heat", persona = Persona.FITNESS, title = "Heat alert",
        sourceLabel = "District warnings", detail = DetailKind.Warnings,
        gate = { ctx -> warningsMatching(ctx, "heat").isNotEmpty() || (ctx.bundle.daily.firstOrNull()?.maxC ?: 0.0) >= 40 },
    ) { ctx ->
        val w = warningsMatching(ctx, "heat").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val max = ctx.bundle.daily.first().maxC
            CardValue.Ready(
                primary = ctx.fmt.temp(max), secondary = "Forecast high, avoid midday exertion",
                tone = Tone.WARNING, icon = WeatherIcon.THERMOMETER_WARMER, numeric = max,
            )
        }
    }

    // ---------------------------------------------------------------- Beach
    val seaState: CardSpec = CardSpec(
        id = "beach.sea", persona = Persona.BEACH, title = "Sea state",
        sourceLabel = "Ocean state forecast", detail = DetailKind.Text, wide = true,
        gate = { it.distanceToCoastKm <= 30.0 },
    ) { ctx ->
        val m = ctx.bundle.marine ?: return@CardSpec CardValue.Pending("Ocean state forecast being added")
        val h = m.waveHeightM ?: return@CardSpec CardValue.Pending("Wave height not available")
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
            m.wavePeriodS?.let { "${it.roundToInt()} s period" },
            m.seaSurfaceTempC?.let { "water ${ctx.fmt.temp(it)}" },
        ).joinToString(" · ")
        CardValue.Ready(
            primary = String.format(Locale.ENGLISH, "%.1f", h), unit = "m", secondary = "$state · $extras",
            tone = tone, icon = WeatherIcon.WAVES, numeric = h,
        )
    }

    val tides: CardSpec = CardSpec(
        id = "beach.tides", persona = Persona.BEACH, title = "Tides",
        sourceLabel = "Coming soon", detail = DetailKind.None,
        gate = { it.distanceToCoastKm <= 30.0 },
    ) { CardValue.Pending("Tide predictions being added") }

    // ---------------------------------------------------------------- Travellers
    val destinations: CardSpec = CardSpec(
        id = "travel.destinations", persona = Persona.TRAVEL, title = "Saved destinations",
        sourceLabel = "7-day city forecast", detail = DetailKind.Destinations, wide = true,
    ) { ctx ->
        if (ctx.destinations.isEmpty()) return@CardSpec CardValue.Unavailable("No saved destinations yet, add a city in Locations!")
        val first = ctx.destinations.first()
        val today = first.daily.firstOrNull()
        val more = ctx.destinations.size - 1
        val brief = today?.let { "${ctx.fmt.temp(it.minC)} – ${ctx.fmt.temp(it.maxC)} · ${it.condition.label()}" } ?: "No forecast cached"
        CardValue.Ready(
            primary = first.location.name,
            secondary = if (more > 0) "$brief · +$more more saved ${if (more == 1) "destination" else "destinations"}" else brief,
            icon = today?.let { WeatherIcon.forCondition(it.condition, true) },
            body = ctx.destinations.joinToString("\n") { b ->
                val d = b.daily.firstOrNull()
                "${b.location.name}: " + (d?.let { "${ctx.fmt.temp(it.minC)}–${ctx.fmt.temp(it.maxC)}, ${it.condition.label()}" } ?: "—")
            },
        )
    }

    val destinationSevere: CardSpec = CardSpec(
        id = "travel.severe", persona = Persona.TRAVEL, title = "Severe weather at destination",
        sourceLabel = "District warnings", detail = DetailKind.Warnings,
        gate = { ctx -> ctx.destinations.any { it.activeWarnings(ctx.nowInstant).isNotEmpty() } },
    ) { ctx ->
        val (bundle, w) = ctx.destinations.flatMap { b -> b.activeWarnings(ctx.nowInstant).map { b to it } }
            .maxBy { it.second.severity.rank }
        CardValue.Ready(
            primary = w.severity.label, secondary = "${bundle.location.name}: ${w.headline}",
            tone = toneForSeverity(w.severity), icon = WeatherIcon.ALERT, body = w.description,
        )
    }

    val packing: CardSpec = CardSpec(
        id = "travel.packing", persona = Persona.TRAVEL, title = "Packing tip",
        sourceLabel = "Based on the 7-day forecast", detail = DetailKind.SevenDay,
    ) { ctx ->
        val target = ctx.destinations.firstOrNull() ?: ctx.bundle
        val tip = Packing.tip(target.daily) ?: return@CardSpec CardValue.Unavailable("No forecast cached")
        CardValue.Ready(primary = tip, secondary = "For ${target.location.name}", icon = WeatherIcon.LUGGAGE)
    }

    // ---------------------------------------------------------------- Parents
    val schoolRun: CardSpec = CardSpec(
        id = "parents.school", persona = Persona.PARENTS, title = "School commute",
        sourceLabel = "Hourly forecast, 7 to 9 am", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION),
    ) { ctx ->
        val s = ctx.settings
        val r = Commute.rainIn(ctx.bundle.hourly, ctx.now, s.schoolStart, s.schoolEnd, ctx.location.zoneId)
        if (r.hours.isEmpty()) return@CardSpec CardValue.Unavailable("Window has passed for today")
        val temp = r.hours.first().temperatureC
        when {
            r.likely -> CardValue.Ready("Rain likely", secondary = "${r.maxProbabilityPct}% chance, ${ctx.fmt.mm(r.totalMm)} · umbrellas", tone = Tone.WARNING, icon = WeatherIcon.UMBRELLA, numeric = r.maxProbabilityPct.toDouble())
            r.possible -> CardValue.Ready("Rain possible", secondary = "${r.maxProbabilityPct}% chance · ${ctx.fmt.temp(temp)}", tone = Tone.CAUTION, icon = WeatherIcon.RAINDROPS, numeric = r.maxProbabilityPct.toDouble())
            else -> CardValue.Ready("Dry", secondary = "${ctx.fmt.temp(temp)} at drop-off", tone = Tone.GOOD, icon = WeatherIcon.forCondition(r.hours.first().condition, true), numeric = r.maxProbabilityPct.toDouble())
        }
    }

    val rainNext3h: CardSpec = CardSpec(
        id = "parents.rain3h", persona = Persona.PARENTS, title = "Rain next 3 hours",
        sourceLabel = "Hourly forecast", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION),
    ) { ctx ->
        val r = Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3)
        if (r.hours.isEmpty()) return@CardSpec CardValue.Unavailable("No hourly forecast cached")
        val tone = when {
            r.likely -> Tone.WARNING
            r.possible -> Tone.CAUTION
            else -> Tone.GOOD
        }
        CardValue.Ready(
            primary = "${r.maxProbabilityPct}", unit = "%",
            secondary = if (r.totalMm > 0) "${ctx.fmt.mm(r.totalMm)} expected" else "No rain expected",
            tone = tone, icon = WeatherIcon.RAINDROPS, numeric = r.maxProbabilityPct.toDouble(),
        )
    }

    val severeWarnings: CardSpec = CardSpec(
        id = "parents.severe", persona = Persona.PARENTS, title = "Severe warnings",
        sourceLabel = "District warnings", detail = DetailKind.Warnings, wide = true,
        gate = { it.bundle.activeWarnings(it.nowInstant).isNotEmpty() },
    ) { ctx -> warningCard(ctx.bundle.activeWarnings(ctx.nowInstant).first(), ctx) }

    // ---------------------------------------------------------------- Agriculture
    val rainfall: CardSpec = CardSpec(
        id = "agri.rainfall", persona = Persona.AGRICULTURE, title = "Rainfall",
        sourceLabel = "District rainfall", detail = DetailKind.Text,
    ) { ctx ->
        val r = ctx.bundle.rainfall ?: return@CardSpec CardValue.Pending("District rainfall source being added")
        val today = r.todayMm ?: return@CardSpec CardValue.Pending("District rainfall source being added")
        val week = r.past7DaysMm?.let { "${ctx.fmt.mm(it)} past week" }
        val normal = r.normalPast7DaysMm?.let { n -> r.past7DaysMm?.let { w -> if (n > 0) "${((w - n) / n * 100).roundToInt()}% vs normal" else null } }
        CardValue.Ready(
            primary = today.roundToInt().toString(), unit = "mm today",
            secondary = listOfNotNull(week, normal).joinToString(" · ").ifEmpty { null },
            icon = WeatherIcon.RAINDROP, numeric = today,
        )
    }

    val rainOutlook: CardSpec = CardSpec(
        id = "agri.outlook", persona = Persona.AGRICULTURE, title = "7-day rain outlook",
        sourceLabel = "City forecast", detail = DetailKind.SevenDay, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached")
        val total = week.sumOf { it.precipitationMm }
        val wet = week.filter { (it.precipitationProbabilityPct ?: 0) >= 40 || it.precipitationMm >= 2 }
        val advice = when {
            total >= 25 -> "skip watering"
            total >= 5 -> "light watering only"
            else -> "water as usual"
        }
        CardValue.Ready(
            primary = ctx.fmt.mm(total), secondary = "${wet.size} wet day${if (wet.size == 1) "" else "s"} · $advice",
            tone = if (total >= 50) Tone.CAUTION else Tone.NEUTRAL, icon = WeatherIcon.RAIN, numeric = total,
        )
    }

    val frost: CardSpec = CardSpec(
        id = "agri.frost", persona = Persona.AGRICULTURE, title = "Frost & cold wave",
        sourceLabel = "District warnings and forecast minimum", detail = DetailKind.Warnings,
        gate = { ctx -> warningsMatching(ctx, "cold", "frost").isNotEmpty() || ctx.bundle.daily.take(3).any { it.minC < 5 } },
    ) { ctx ->
        val w = warningsMatching(ctx, "cold", "frost").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val d = ctx.bundle.daily.take(3).minBy { it.minC }
            CardValue.Ready(
                primary = ctx.fmt.temp(d.minC), secondary = "Low on ${dayFmt.format(d.date)} night · cover seedlings",
                tone = Tone.WARNING, icon = WeatherIcon.THERMOMETER_COLDER, numeric = d.minC,
            )
        }
    }

    val agromet: CardSpec = CardSpec(
        id = "agri.advisory", persona = Persona.AGRICULTURE, title = "Agromet advisory",
        sourceLabel = "IMD agromet bulletin", detail = DetailKind.Text, wide = true,
    ) { ctx ->
        val a = ctx.bundle.advisory ?: return@CardSpec CardValue.Pending("Agromet advisory source being added")
        CardValue.Ready(primary = a.title, secondary = a.body.take(120), icon = WeatherIcon.AGRO, body = a.body)
    }

    // ---------------------------------------------------------------- Commuters
    val visibility: CardSpec = CardSpec(
        id = "commute.visibility", persona = Persona.COMMUTERS, title = "Visibility & fog",
        sourceLabel = "Current observation and warnings", detail = DetailKind.Hourly(HourlyMetric.VISIBILITY),
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
        }
        CardValue.Ready(
            primary = km?.let(ctx.fmt::distance) ?: label,
            secondary = w?.headline ?: "$label · use low beams, keep distance",
            tone = if ((km ?: 1.0) < 1.0 || w?.severity == WarningSeverity.ORANGE || w?.severity == WarningSeverity.RED) Tone.WARNING else Tone.CAUTION,
            icon = WeatherIcon.FOG, numeric = km, body = w?.description,
        )
    }

    val stormAlert: CardSpec = CardSpec(
        id = "commute.storm", persona = Persona.COMMUTERS, title = "Storm alert",
        sourceLabel = "Nowcast and district warnings", detail = DetailKind.Warnings,
        gate = { ctx ->
            warningsMatching(ctx, "thunder", "storm", "squall", "lightning").isNotEmpty() ||
                Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3).hours.any { it.condition == WeatherCondition.THUNDERSTORM }
        },
    ) { ctx ->
        val w = warningsMatching(ctx, "thunder", "storm", "squall", "lightning").firstOrNull()
        if (w != null) warningCard(w, ctx)
        else {
            val h = Commute.nextHours(ctx.bundle.hourly, ctx.nowInstant, 3).hours.first { it.condition == WeatherCondition.THUNDERSTORM }
            CardValue.Ready("Thunderstorm", secondary = "Expected around ${ctx.fmt.clock(h.time)}", tone = Tone.WARNING, icon = WeatherIcon.THUNDERSTORMS_RAIN)
        }
    }

    val leaveEarlier: CardSpec = CardSpec(
        id = "commute.leave", persona = Persona.COMMUTERS, title = "Leave earlier",
        sourceLabel = "Rain overlapping your commute", detail = DetailKind.Hourly(HourlyMetric.PRECIPITATION),
        gate = { ctx ->
            !ctx.isWeekend && Commute.rainIn(ctx.bundle.hourly, ctx.now, ctx.settings.commuteStart, ctx.settings.commuteEnd, ctx.location.zoneId).possible
        },
    ) { ctx ->
        val r = Commute.rainIn(ctx.bundle.hourly, ctx.now, ctx.settings.commuteStart, ctx.settings.commuteEnd, ctx.location.zoneId)
        val minutes = if (r.likely) 20 else 10
        CardValue.Ready(
            primary = "+$minutes min", secondary = "${r.maxProbabilityPct}% rain chance ${ctx.fmt.clock(r.hours.first().time)} – ${ctx.fmt.clock(r.hours.last().time.plusSeconds(3600))}",
            tone = if (r.likely) Tone.WARNING else Tone.CAUTION, icon = WeatherIcon.COMMUTE, numeric = minutes.toDouble(),
        )
    }

    val traffic: CardSpec = CardSpec(
        id = "commute.traffic", persona = Persona.COMMUTERS, title = "Traffic",
        sourceLabel = "Opens Maps", detail = DetailKind.None,
        action = CardAction.DeepLink("geo:0,0?q=traffic", "Open in Maps"),
    ) { ctx ->
        CardValue.Ready(primary = "Live traffic", secondary = "Open Maps for ${ctx.location.name}", icon = WeatherIcon.TRAFFIC)
    }

    // ---------------------------------------------------------------- Event planners
    val outlook7d: CardSpec = CardSpec(
        id = "events.outlook", persona = Persona.EVENTS, title = "7-day outlook",
        sourceLabel = "City forecast", detail = DetailKind.SevenDay, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached")
        val wettest = week.maxBy { it.precipitationProbabilityPct ?: 0 }
        val driest = week.minBy { it.precipitationProbabilityPct ?: 0 }
        CardValue.Ready(
            primary = "${ctx.fmt.temp(week.minOf { it.minC })} – ${ctx.fmt.temp(week.maxOf { it.maxC })}",
            secondary = "Wettest ${dayFmt.format(wettest.date)} ${wettest.precipitationProbabilityPct ?: 0}% · driest ${dayFmt.format(driest.date)}",
            icon = WeatherIcon.forCondition(week.first().condition, true),
        )
    }

    val comfort: CardSpec = CardSpec(
        id = "events.comfort", persona = Persona.EVENTS, title = "Comfort index",
        sourceLabel = "Heat index and humidex", detail = DetailKind.Hourly(HourlyMetric.TEMPERATURE),
    ) { ctx ->
        val cur = ctx.bundle.current ?: return@CardSpec CardValue.Unavailable("No current observation")
        val h = cur.humidityPct ?: return@CardSpec CardValue.Unavailable("Humidity not reported")
        val c = Thermal.comfort(cur.temperatureC, h.toDouble())
        val feels = maxOf(Thermal.heatIndexC(cur.temperatureC, h.toDouble()), Thermal.humidexC(cur.temperatureC, h.toDouble()))
        CardValue.Ready(
            primary = c.label, secondary = "Feels like ${ctx.fmt.temp(feels)}",
            tone = c.tone, icon = WeatherIcon.COMFORT, numeric = feels,
        )
    }

    val bestDay: CardSpec = CardSpec(
        id = "events.bestday", persona = Persona.EVENTS, title = "Best day this week",
        sourceLabel = "Based on rain, heat and wind", detail = DetailKind.SevenDay,
    ) { ctx ->
        val (d, s) = DayScore.best(ctx.bundle.daily) ?: return@CardSpec CardValue.Unavailable("No forecast cached")
        CardValue.Ready(
            primary = dateFmt.format(d.date),
            secondary = "${ctx.fmt.temp(d.maxC)} · ${d.precipitationProbabilityPct ?: 0}% rain",
            tone = if (s >= 70) Tone.GOOD else Tone.NEUTRAL, icon = WeatherIcon.STAR, numeric = s.toDouble(),
        )
    }

    // ---------------------------------------------------------------- General
    val hourly: CardSpec = CardSpec(
        id = "general.hourly", persona = Persona.GENERAL, title = "Next 24 hours",
        sourceLabel = "Hourly forecast", detail = DetailKind.Hourly(HourlyMetric.TEMPERATURE), wide = true,
    ) { ctx ->
        val next = ctx.bundle.hourlyFrom(ctx.nowInstant, 24)
        if (next.isEmpty()) return@CardSpec CardValue.Unavailable("No hourly forecast cached")
        val hi = next.maxBy { it.temperatureC }
        val lo = next.minBy { it.temperatureC }
        val rainAt = next.firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        CardValue.Ready(
            primary = "${ctx.fmt.temp(lo.temperatureC)} – ${ctx.fmt.temp(hi.temperatureC)}",
            secondary = rainAt?.let { "Rain likely from ${ctx.fmt.clock(it.time)}" } ?: "No rain expected",
            icon = WeatherIcon.forCondition(next.first().condition, next.first().isDay),
        )
    }

    val sevenDay: CardSpec = CardSpec(
        id = "general.week", persona = Persona.GENERAL, title = "7-day forecast",
        sourceLabel = "City forecast", detail = DetailKind.SevenDay, wide = true,
    ) { ctx ->
        val week = ctx.bundle.daily.take(7)
        if (week.isEmpty()) return@CardSpec CardValue.Unavailable("No forecast cached")
        val tomorrow = week.getOrNull(1) ?: week.first()
        CardValue.Ready(
            primary = "${ctx.fmt.temp(tomorrow.minC)} – ${ctx.fmt.temp(tomorrow.maxC)}",
            secondary = "Tomorrow · ${tomorrow.condition.label()} · ${tomorrow.precipitationProbabilityPct ?: 0}% rain",
            icon = WeatherIcon.forCondition(tomorrow.condition, true),
        )
    }

    val warnings: CardSpec = CardSpec(
        id = "general.warnings", persona = Persona.GENERAL, title = "Warnings",
        sourceLabel = "District warnings", detail = DetailKind.Warnings, wide = true,
        gate = { it.bundle.activeWarnings(it.nowInstant).isNotEmpty() },
    ) { ctx -> warningCard(ctx.bundle.activeWarnings(ctx.nowInstant).first(), ctx) }

    val all: List<CardSpec> = listOf(
        warnings, severeWarnings,
        aqi, humidity, uv, pollen,
        sun, runWindow, wind, heatAlert,
        seaState, tides,
        destinations, destinationSevere, packing,
        schoolRun, rainNext3h,
        rainfall, rainOutlook, frost, agromet,
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
