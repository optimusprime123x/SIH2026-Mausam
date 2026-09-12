package dev.mausam.home.domain.briefs

import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.Commute
import dev.mausam.home.domain.cards.DayScore
import dev.mausam.home.domain.cards.Packing
import dev.mausam.home.domain.cards.RunScore
import dev.mausam.home.domain.cards.Thermal
import dev.mausam.home.domain.cards.UvEstimate
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.domain.personas.Persona
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
                    val aqi = b.airQuality?.aqi?.let { " · AQI $it (${IndianAqi.category(it).label.lowercase()})" } ?: ""
                    Brief(
                        "Best run window ${f.clock(w.start)} – ${f.clock(w.end)}",
                        listOfNotNull(t?.let { "${f.temp(it)} at the start" }, aqi.trimStart(' ', '·').ifBlank { null })
                            .joinToString(" · ") + uvLine(ctx),
                    )
                } else Brief(
                    "Tomorrow in $name",
                    tomorrow?.let { "${f.temp(it.minC)} – ${f.temp(it.maxC)}, ${it.condition.label().lowercase()}. Lay out kit tonight; early hours will be the coolest." }
                        ?: "Forecast not cached yet.",
                )
            }
            Persona.PARENTS -> {
                val s = ctx.settings
                val r = Commute.rainIn(b.hourly, ctx.now, s.schoolStart, s.schoolEnd, ctx.location.zoneId)
                val window = "${f.clock(ctx.now.with(s.schoolStart).toInstant())} – ${f.clock(ctx.now.with(s.schoolEnd).toInstant())}"
                when {
                    evening -> Brief(
                        "Tomorrow's school run",
                        tomorrow?.let { "${it.condition.label()} with a ${it.precipitationProbabilityPct ?: 0}% chance of rain. High ${f.temp(it.maxC)}." }
                            ?: "Forecast not cached yet.",
                    )
                    r.likely -> Brief("Rain likely $window", "${r.maxProbabilityPct}% chance, ${f.mm(r.totalMm)}. Umbrellas and covered shoes for the school run.")
                    r.possible -> Brief("Rain possible $window", "${r.maxProbabilityPct}% chance. A light rain jacket should do.")
                    else -> Brief("Dry school run", cur?.let { "${f.temp(it.temperatureC)} and ${it.condition.label().lowercase()} now" } ?: "No rain expected $window.")
                }
            }
            Persona.AGRICULTURE -> {
                val week = b.daily.take(7)
                val total = week.sumOf { it.precipitationMm }
                val frost = week.firstOrNull { it.minC < 5 }
                val watering = when {
                    total >= 25 -> "skip watering"
                    total >= 5 -> "water lightly"
                    else -> "water as usual"
                }
                Brief(
                    "${f.mm(total)} rain expected this week",
                    listOfNotNull(
                        "${watering.replaceFirstChar { it.uppercase() }}.",
                        frost?.let { "Frost risk ${dayFmt.format(it.date)} night (${f.temp(it.minC)}), cover seedlings." },
                        b.advisory?.let { "Advisory: ${it.title}." },
                    ).joinToString(" "),
                )
            }
            Persona.TRAVEL -> {
                val dest = ctx.destinations.firstOrNull()
                val destWarning = ctx.destinations.flatMap { d -> d.activeWarnings(ctx.nowInstant).map { d to it } }
                    .maxByOrNull { it.second.severity.rank }
                when {
                    destWarning != null -> Brief(
                        "${destWarning.second.severity.label} alert in ${destWarning.first.location.name}",
                        "${destWarning.second.headline}. ${Packing.tip(destWarning.first.daily) ?: ""}".trim(),
                    )
                    dest != null -> Brief(
                        "${dest.location.name} ${if (evening) "tomorrow" else "today"}",
                        (if (evening) dest.daily.getOrNull(1) else dest.daily.firstOrNull())?.let {
                            "${f.temp(it.minC)} – ${f.temp(it.maxC)}, ${it.condition.label().lowercase()}. ${Packing.tip(dest.daily) ?: ""}".trim()
                        } ?: "Forecast not cached yet.",
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
                        aq.aqi > earlier + 10 -> ", worse than yesterday"
                        aq.aqi < earlier - 10 -> ", better than yesterday"
                        else -> ", same as yesterday"
                    }
                    val advice = when (cat) {
                        IndianAqi.Category.GOOD, IndianAqi.Category.SATISFACTORY -> "Good time to air the house."
                        IndianAqi.Category.MODERATE -> "Sensitive groups should limit long outdoor effort."
                        else -> "Keep windows shut and wear a mask outdoors."
                    }
                    Brief("AQI ${aq.aqi} (${cat.label.lowercase()})$trend", advice + (cur?.humidityPct?.let { " Humidity $it%." } ?: "") + uvLine(ctx))
                } else general(ctx, evening)
            }
            Persona.COMMUTERS -> {
                val s = ctx.settings
                val fog = warnings.firstOrNull { it.event.contains("fog", true) }
                val vis = cur?.visibilityKm
                val r = Commute.rainIn(b.hourly, ctx.now, s.commuteStart, s.commuteEnd, ctx.location.zoneId)
                when {
                    evening -> Brief("Tomorrow's commute", tomorrow?.let { "${it.condition.label()}, ${it.precipitationProbabilityPct ?: 0}% chance of rain. High ${f.temp(it.maxC)}." } ?: "Forecast not cached yet.")
                    fog != null || (vis != null && vis < 1.0) -> Brief(
                        "Fog on the roads",
                        (vis?.let { "Visibility ${f.distance(it)}. " } ?: "") + "Leave 15 minutes earlier, low beams on.",
                    )
                    r.likely -> Brief("Rain on your commute", "${r.maxProbabilityPct}% chance between ${f.clock(r.hours.first().time)} and ${f.clock(r.hours.last().time.plusSeconds(3600))}. Leave 20 minutes earlier.")
                    r.possible -> Brief("Rain possible on your commute", "${r.maxProbabilityPct}% chance. Leave 10 minutes earlier to be safe.")
                    else -> Brief("Clear commute", cur?.let { "${f.temp(it.temperatureC)} and ${it.condition.label().lowercase()}. No rain in your window." } ?: "No rain in your window.")
                }
            }
            Persona.EVENTS -> {
                val best = DayScore.best(b.daily)
                val comfort = cur?.humidityPct?.let { Thermal.comfort(cur.temperatureC, it.toDouble()) }
                Brief(
                    best?.let { "Best day this week: ${dayFmt.format(it.first.date)}" } ?: "This week in $name",
                    listOfNotNull(
                        best?.let { "${f.temp(it.first.maxC)} and ${it.first.precipitationProbabilityPct ?: 0}% rain." },
                        comfort?.let { "Right now feels ${it.label.lowercase()}." },
                    ).joinToString(" "),
                )
            }
            Persona.BEACH -> {
                val m = b.marine
                if (m?.waveHeightM != null) {
                    val h = m.waveHeightM
                    val state = when {
                        h < 0.5 -> "Calm"
                        h < 1.25 -> "Slight"
                        h < 2.5 -> "Moderate"
                        else -> "Rough"
                    }
                    Brief(
                        "$state seas, ${String.format(Locale.ENGLISH, "%.1f", h)} m waves",
                        listOfNotNull(m.seaSurfaceTempC?.let { "Water ${f.temp(it)}." }, if (h >= 2.5) "Stay out of the water." else null).joinToString(" "),
                    )
                } else general(ctx, evening)
            }
            Persona.GENERAL -> general(ctx, evening)
        }
    }

    private fun uvLine(ctx: CardContext): String {
        val b = ctx.bundle
        val peak = b.hourly.filter { it.time.atZone(ctx.location.zoneId).toLocalDate() == ctx.now.toLocalDate() }
            .mapNotNull { h -> (h.uvIndex ?: UvEstimate.estimate(ctx.location.latitude, ctx.location.longitude, h.time, h.cloudCoverPct)).let { h to it } }
            .maxByOrNull { it.second } ?: return ""
        return if (peak.second >= 6) " UV peaks at ${peak.second.roundToInt()} around ${ctx.fmt.clock(peak.first.time)}." else ""
    }

    fun general(ctx: CardContext, evening: Boolean): Brief {
        val b = ctx.bundle
        val f = ctx.fmt
        val cur = b.current
        val d = if (evening) b.daily.getOrNull(1) else b.daily.firstOrNull()
        val rainAt = b.hourlyFrom(ctx.nowInstant, 18).firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        val warning = b.activeWarnings(ctx.nowInstant).firstOrNull()
        val title = when {
            warning != null -> "${warning.severity.label} alert: ${warning.event}"
            evening -> d?.let { "Tomorrow ${f.temp(it.minC)} – ${f.temp(it.maxC)}, ${it.condition.label().lowercase()}" } ?: "Tomorrow in ${ctx.location.name}"
            cur != null -> "${f.temp(cur.temperatureC)} and ${cur.condition.label().lowercase()} in ${ctx.location.name}"
            else -> "Weather in ${ctx.location.name}"
        }
        val body = listOfNotNull(
            warning?.headline,
            if (!evening) d?.let { "High ${f.temp(it.maxC)}, low ${f.temp(it.minC)}." } else null,
            rainAt?.let { "Rain likely from ${f.clock(it.time)}." },
        ).joinToString(" ").ifBlank { "No rain expected." }
        return Brief(title, body)
    }
}

/** Alert push policy: new orange or red only, and only once per warning id. Yellow never pushes. */
object AlertPolicy {
    fun toPush(active: List<WeatherWarning>, alreadyNotified: Set<String>): List<WeatherWarning> =
        active.filter { it.severity.pushes && it.id !in alreadyNotified }

    fun bypassesQuietHours(severity: WarningSeverity): Boolean = severity.pushes
}
