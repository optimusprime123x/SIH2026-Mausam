package dev.mausam.home.domain.briefs

import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.Commute
import dev.mausam.home.domain.cards.DayScore
import dev.mausam.home.domain.cards.Packing
import dev.mausam.home.domain.cards.RunScore
import dev.mausam.home.domain.cards.Thermal
import dev.mausam.home.domain.cards.UvEstimate
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.personas.Persona
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class Brief(val title: String, val body: String)

/**
 * Persona-shaped copy for the two daily local notifications. One sentence of fact, one of
 * advice, always from the same rule engine the cards use so the brief never contradicts the home.
 */
object BriefComposer {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

    /** English weekday name is the translation key, so "Monday" → "सोमवार". */
    private fun dayName(date: LocalDate): String = dayFmt.format(date).tr()

    fun compose(persona: Persona, ctx: CardContext, evening: Boolean): Brief {
        val b = ctx.bundle
        val f = ctx.fmt
        val cur = b.current
        val today = b.daily.firstOrNull()
        val tomorrow = b.daily.getOrNull(1)
        val name = ctx.location.name
        val warnings = b.activeWarnings(ctx.nowInstant)

        return when (persona) {
            Persona.FITNESS -> {
                val w = RunScore.bestWindow(b.hourly, b.airQuality?.aqi, ctx.now)
                if (!evening && w != null) {
                    val t = b.hourly.firstOrNull { it.time == w.start }?.temperatureC
                    val aqi = b.airQuality?.aqi?.let { "AQI %d (%s)".trf(it, IndianAqi.category(it).label.lowercase()) }
                    Brief(
                        "Best run window %s".trf(f.clockRange(w.start, w.end)),
                        listOfNotNull(
                            listOfNotNull(t?.let { "%s at the start".trf(f.temp(it)) }, aqi).joinToString(" · "),
                            uvLine(ctx),
                        ).joinToString(" "),
                    )
                } else Brief(
                    "Tomorrow in %s".trf(name),
                    tomorrow?.let {
                        "%s – %s, %s. Lay out kit tonight; early hours will be the coolest."
                            .trf(f.temp(it.minC), f.temp(it.maxC), it.condition.label().lowercase())
                    } ?: "Forecast not cached yet.".tr(),
                )
            }
            Persona.PARENTS -> {
                val s = ctx.settings
                val r = Commute.rainIn(b.hourly, ctx.now, s.schoolStart, s.schoolEnd, ctx.location.zoneId)
                val window = f.clockRange(ctx.now.with(s.schoolStart).toInstant(), ctx.now.with(s.schoolEnd).toInstant())
                when {
                    evening -> Brief(
                        "Tomorrow's school run".tr(),
                        tomorrow?.let {
                            "%s with a %d%% chance of rain. High %s.".trf(it.condition.label(), it.precipitationProbabilityPct ?: 0, f.temp(it.maxC))
                        } ?: "Forecast not cached yet.".tr(),
                    )
                    r.likely -> Brief(
                        "Rain likely %s".trf(window),
                        "%d%% chance, %s. Umbrellas and covered shoes for the school run.".trf(r.maxProbabilityPct, f.mm(r.totalMm)),
                    )
                    r.possible -> Brief(
                        "Rain possible %s".trf(window),
                        "%d%% chance. A light rain jacket should do.".trf(r.maxProbabilityPct),
                    )
                    else -> Brief(
                        "Dry school run".tr(),
                        cur?.let { "%s and %s now".trf(f.temp(it.temperatureC), it.condition.label().lowercase()) }
                            ?: "No rain expected %s.".trf(window),
                    )
                }
            }
            Persona.AGRICULTURE -> {
                val week = b.daily.take(7)
                val total = week.sumOf { it.precipitationMm }
                val frost = week.firstOrNull { it.minC < 5 }
                val watering = when {
                    total >= 25 -> "Skip watering."
                    total >= 5 -> "Water lightly."
                    else -> "Water as usual."
                }
                Brief(
                    "%s rain expected this week".trf(f.mm(total)),
                    listOfNotNull(
                        watering.tr(),
                        frost?.let { "Frost risk %s night (%s), cover seedlings.".trf(dayName(it.date), f.temp(it.minC)) },
                        b.advisory?.let { "Advisory: %s.".trf(it.title) },
                    ).joinToString(" "),
                )
            }
            Persona.TRAVEL -> {
                val dest = ctx.destinations.firstOrNull()
                val destWarning = ctx.destinations.flatMap { d -> d.activeWarnings(ctx.nowInstant).map { d to it } }
                    .maxByOrNull { it.second.severity.rank }
                when {
                    destWarning != null -> Brief(
                        "%s alert in %s".trf(destWarning.second.severity.label, destWarning.first.location.name),
                        "%s. %s".trf(destWarning.second.headline, Packing.tip(destWarning.first.daily) ?: "").trim(),
                    )
                    dest != null -> Brief(
                        (if (evening) "%s tomorrow" else "%s today").trf(dest.location.name),
                        (if (evening) dest.daily.getOrNull(1) else dest.daily.firstOrNull())?.let {
                            "%s – %s, %s. %s".trf(f.temp(it.minC), f.temp(it.maxC), it.condition.label().lowercase(), Packing.tip(dest.daily) ?: "").trim()
                        } ?: "Forecast not cached yet.".tr(),
                    )
                    else -> general(ctx, evening)
                }
            }
            Persona.HEALTH -> {
                val aq = b.airQuality
                if (aq != null) {
                    val cat = IndianAqi.category(aq.aqi)
                    val earlier = aq.history24h.firstOrNull()?.aqi
                    val trend = when {
                        earlier == null -> ""
                        aq.aqi > earlier + 10 -> ", worse than yesterday".tr()
                        aq.aqi < earlier - 10 -> ", better than yesterday".tr()
                        else -> ", same as yesterday".tr()
                    }
                    val advice = when (cat) {
                        IndianAqi.Category.GOOD, IndianAqi.Category.SATISFACTORY -> "Good time to air the house."
                        IndianAqi.Category.MODERATE -> "Sensitive groups should limit long outdoor effort."
                        else -> "Keep windows shut and wear a mask outdoors."
                    }
                    Brief(
                        "AQI %d (%s)%s".trf(aq.aqi, cat.label.lowercase(), trend),
                        listOfNotNull(advice.tr(), cur?.humidityPct?.let { "Humidity %d%%.".trf(it) }, uvLine(ctx)).joinToString(" "),
                    )
                } else general(ctx, evening)
            }
            Persona.COMMUTERS -> {
                val s = ctx.settings
                val fog = warnings.firstOrNull { it.event.contains("fog", true) }
                val vis = cur?.visibilityKm
                val r = Commute.rainIn(b.hourly, ctx.now, s.commuteStart, s.commuteEnd, ctx.location.zoneId)
                when {
                    evening -> Brief(
                        "Tomorrow's commute".tr(),
                        tomorrow?.let {
                            "%s, %d%% chance of rain. High %s.".trf(it.condition.label(), it.precipitationProbabilityPct ?: 0, f.temp(it.maxC))
                        } ?: "Forecast not cached yet.".tr(),
                    )
                    fog != null || (vis != null && vis < 1.0) -> Brief(
                        "Fog on the roads".tr(),
                        listOfNotNull(vis?.let { "Visibility %s.".trf(f.distance(it)) }, "Leave 15 minutes earlier, low beams on.".tr()).joinToString(" "),
                    )
                    r.likely -> Brief(
                        "Rain on your commute".tr(),
                        "%d%% chance %s. Leave 20 minutes earlier."
                            .trf(r.maxProbabilityPct, f.clockRange(r.hours.first().time, r.hours.last().time.plusSeconds(3600))),
                    )
                    r.possible -> Brief(
                        "Rain possible on your commute".tr(),
                        "%d%% chance. Leave 10 minutes earlier to be safe.".trf(r.maxProbabilityPct),
                    )
                    else -> Brief(
                        "Clear commute".tr(),
                        cur?.let { "%s and %s. No rain in your window.".trf(f.temp(it.temperatureC), it.condition.label().lowercase()) }
                            ?: "No rain in your window.".tr(),
                    )
                }
            }
            Persona.EVENTS -> {
                val best = DayScore.best(b.daily)
                val comfort = cur?.humidityPct?.let { Thermal.comfort(cur.temperatureC, it.toDouble()) }
                Brief(
                    best?.let { "Best day this week: %s".trf(dayName(it.first.date)) } ?: "This week in %s".trf(name),
                    listOfNotNull(
                        best?.let { "%s and %d%% rain.".trf(f.temp(it.first.maxC), it.first.precipitationProbabilityPct ?: 0) },
                        comfort?.let { "Right now feels %s.".trf(it.label.lowercase()) },
                    ).joinToString(" "),
                )
            }
            Persona.BEACH -> {
                val m = b.marine
                if (m?.waveHeightM != null) {
                    val h = m.waveHeightM
                    val state = when {
                        h < 0.5 -> "Calm seas"
                        h < 1.25 -> "Slight seas"
                        h < 2.5 -> "Moderate seas"
                        else -> "Rough seas"
                    }
                    Brief(
                        "%s, %.1f m waves".trf(state.tr(), h),
                        listOfNotNull(
                            m.seaSurfaceTempC?.let { "Water %s.".trf(f.temp(it)) },
                            if (h >= 2.5) "Stay out of the water.".tr() else null,
                        ).joinToString(" "),
                    )
                } else general(ctx, evening)
            }
            Persona.GENERAL -> general(ctx, evening)
        }
    }

    /** One sentence when today's UV peak is high, else null. */
    private fun uvLine(ctx: CardContext): String? {
        val b = ctx.bundle
        val peak = b.hourly.filter { it.time.atZone(ctx.location.zoneId).toLocalDate() == ctx.now.toLocalDate() }
            .mapNotNull { h -> (h.uvIndex ?: UvEstimate.estimate(ctx.location.latitude, ctx.location.longitude, h.time, h.cloudCoverPct)).let { h to it } }
            .maxByOrNull { it.second } ?: return null
        return if (peak.second >= 6) "UV peaks at %d around %s.".trf(peak.second.roundToInt(), ctx.fmt.clock(peak.first.time)) else null
    }

    fun general(ctx: CardContext, evening: Boolean): Brief {
        val b = ctx.bundle
        val f = ctx.fmt
        val cur = b.current
        val d = if (evening) b.daily.getOrNull(1) else b.daily.firstOrNull()
        val rainAt = b.hourlyFrom(ctx.nowInstant, 18).firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        val warning = b.activeWarnings(ctx.nowInstant).firstOrNull()
        val title = when {
            warning != null -> "%s alert: %s".trf(warning.severity.label, warning.event)
            evening -> d?.let { "Tomorrow %s – %s, %s".trf(f.temp(it.minC), f.temp(it.maxC), it.condition.label().lowercase()) }
                ?: "Tomorrow in %s".trf(ctx.location.name)
            cur != null -> "%s and %s in %s".trf(f.temp(cur.temperatureC), cur.condition.label().lowercase(), ctx.location.name)
            else -> "Weather in %s".trf(ctx.location.name)
        }
        val body = listOfNotNull(
            warning?.headline,
            if (!evening) d?.let { "High %s, low %s.".trf(f.temp(it.maxC), f.temp(it.minC)) } else null,
            rainAt?.let { "Rain likely from %s.".trf(f.clock(it.time)) },
        ).joinToString(" ").ifBlank { "No rain expected.".tr() }
        return Brief(title, body)
    }
}

/** Alert push policy: new orange or red only, and only once per warning id. Yellow never pushes. */
object AlertPolicy {
    fun toPush(active: List<WeatherWarning>, alreadyNotified: Set<String>): List<WeatherWarning> =
        active.filter { it.severity.pushes && it.id !in alreadyNotified }

    fun bypassesQuietHours(severity: WarningSeverity): Boolean = severity.pushes
}
