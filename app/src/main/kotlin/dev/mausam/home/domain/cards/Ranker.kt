package dev.mausam.home.domain.cards

import dev.mausam.home.domain.personas.Persona
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.pow

/** Decayed tap count for one card. Counts live in Room; the decay is applied lazily. */
data class CardUsage(val cardId: String, val score: Double, val updatedAt: Instant) {
    fun decayedAt(now: Instant): Double = Ranker.decay(score, updatedAt, now)
    fun tapped(now: Instant): CardUsage = CardUsage(cardId, decayedAt(now) + 1.0, now)
}

/** Per-card user preferences from long press or the catalogue. */
data class CardPref(
    val cardId: String,
    val pinnedAt: Instant? = null,
    val hidden: Boolean = false,
    /** "Move to top": a large, temporary boost so the move is visible but not permanent. */
    val boostUntil: Instant? = null,
    /** Added from the catalogue even though its persona is not selected. */
    val added: Boolean = false,
)

/**
 * On-device ranking, computed on every home open. Never leaves the phone.
 *
 * score = persona match + decayed usage (7-day half-life) + time-of-day boost + urgency.
 * Gates remove cards that make no sense here and now. Pinned cards always lead, hidden never show.
 */
object Ranker {
    const val HALF_LIFE_DAYS = 7.0
    const val PERSONA_MATCH = 10.0
    const val GENERAL_BASE = 6.0
    const val TOP_BOOST = 100.0
    const val PLACEHOLDER_PENALTY = 4.0

    fun decay(score: Double, from: Instant, to: Instant): Double {
        val days = (to.epochSecond - from.epochSecond).coerceAtLeast(0) / 86400.0
        return score * 0.5.pow(days / HALF_LIFE_DAYS)
    }

    /** Fitness and commute 5–9 am weekdays, event and travel weekends, gardening evenings. */
    fun timeBoost(persona: Persona, now: ZonedDateTime): Double {
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        val hour = now.hour
        return when (persona) {
            Persona.FITNESS, Persona.COMMUTERS -> if (!weekend && hour in 5..8) 2.0 else 0.0
            Persona.EVENTS, Persona.TRAVEL -> if (weekend) 1.5 else 0.0
            Persona.AGRICULTURE -> if (hour in 17..20) 1.5 else 0.0
            else -> 0.0
        }
    }

    private fun urgency(value: CardValue): Double = when ((value as? CardValue.Ready)?.tone) {
        Tone.DANGER -> 5.0
        Tone.WARNING -> 3.0
        Tone.CAUTION -> 1.0
        else -> 0.0
    }

    fun rank(
        specs: List<CardSpec>,
        ctx: CardContext,
        usage: Map<String, CardUsage>,
        prefs: Map<String, CardPref>,
    ): List<RenderedCard> {
        val now = ctx.nowInstant
        val selected = ctx.settings.personas
        val scored = specs.mapIndexedNotNull { index, spec ->
            val pref = prefs[spec.id]
            if (pref?.hidden == true) return@mapIndexedNotNull null
            val inSet = spec.persona == Persona.GENERAL || spec.persona in selected || pref?.added == true
            if (!inSet) return@mapIndexedNotNull null
            if (!spec.gate(ctx)) return@mapIndexedNotNull null
            val value = spec.fetch(ctx)
            var score = if (spec.persona == Persona.GENERAL) GENERAL_BASE else PERSONA_MATCH
            score += usage[spec.id]?.decayedAt(now) ?: 0.0
            score += timeBoost(spec.persona, ctx.now)
            score += urgency(value)
            // A card with nothing to show never outranks one with data.
            if (value !is CardValue.Ready) score -= PLACEHOLDER_PENALTY
            if (pref?.boostUntil?.isAfter(now) == true) score += TOP_BOOST
            val pinned = pref?.pinnedAt != null
            Triple(RenderedCard(spec, value, score, pinned), pref?.pinnedAt, index)
        }
        val pinned = scored.filter { it.first.pinned }.sortedByDescending { it.second }
        val rest = scored.filter { !it.first.pinned }
            .sortedWith(compareByDescending<Triple<RenderedCard, Instant?, Int>> { it.first.score }.thenBy { it.third })
        return (pinned + rest).map { it.first }
    }
}
