package dev.mausam.home.domain

import com.google.common.truth.Truth.assertThat
import dev.mausam.home.domain.cards.CardPref
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.CardUsage
import dev.mausam.home.domain.cards.Ranker
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.personas.Persona
import org.junit.Test
import java.time.Duration

class RankerTest {
    private val now = Fixtures.now.toInstant()

    @Test
    fun `usage decays with a seven day half-life`() {
        assertThat(Ranker.decay(8.0, now, now.plus(Duration.ofDays(7)))).isWithin(0.001).of(4.0)
        assertThat(Ranker.decay(8.0, now, now.plus(Duration.ofDays(14)))).isWithin(0.001).of(2.0)
        assertThat(CardUsage("x", 3.0, now).tapped(now).score).isWithin(0.001).of(4.0)
    }

    @Test
    fun `pinned cards lead, hidden cards vanish, gates apply`() {
        val ctx = Fixtures.context(personas = setOf(Persona.HEALTH, Persona.FITNESS))
        val prefs = mapOf(
            "health.uv" to CardPref("health.uv", pinnedAt = now),
            "health.humidity" to CardPref("health.humidity", hidden = true),
        )
        val ranked = Ranker.rank(CardRegistry.all, ctx, emptyMap(), prefs)
        val ids = ranked.map { it.spec.id }
        assertThat(ids.first()).isEqualTo("health.uv")
        assertThat(ids).doesNotContain("health.humidity")
        assertThat(ids).doesNotContain("beach.sea")            // not coastal, and persona not selected
        assertThat(ids).doesNotContain("general.warnings")     // no active warnings → gate closed
        assertThat(ids).contains("general.hourly")             // general always in
        assertThat(ids).doesNotContain("events.bestday")       // persona not selected
    }

    @Test
    fun `move to top boost and usage change order`() {
        val ctx = Fixtures.context(personas = setOf(Persona.EVENTS))
        val base = Ranker.rank(CardRegistry.all, ctx, emptyMap(), emptyMap()).map { it.spec.id }
        assertThat(base.first()).isNotEqualTo("general.week")
        val boosted = Ranker.rank(
            CardRegistry.all, ctx, emptyMap(),
            mapOf("general.week" to CardPref("general.week", boostUntil = now.plusSeconds(3600))),
        ).map { it.spec.id }
        assertThat(boosted.first()).isEqualTo("general.week")
        val used = Ranker.rank(
            CardRegistry.all, ctx, mapOf("general.week" to CardUsage("general.week", 6.0, now)), emptyMap(),
        ).map { it.spec.id }
        assertThat(used.first()).isEqualTo("general.week")
    }

    @Test
    fun `weekday morning boosts fitness and commute, urgency floats warnings`() {
        assertThat(Ranker.timeBoost(Persona.FITNESS, Fixtures.now)).isEqualTo(2.0)
        assertThat(Ranker.timeBoost(Persona.EVENTS, Fixtures.now)).isEqualTo(0.0)
        val weekend = Fixtures.now.plusDays(1) // Saturday
        assertThat(Ranker.timeBoost(Persona.EVENTS, weekend)).isEqualTo(1.5)

        val ctx = Fixtures.context(bundle = Fixtures.bundle(warnings = listOf(Fixtures.warning(Fixtures.now, WarningSeverity.RED))), personas = setOf(Persona.PARENTS))
        val ids = Ranker.rank(CardRegistry.all, ctx, emptyMap(), emptyMap()).map { it.spec.id }
        assertThat(ids.first()).isAnyOf("general.warnings", "parents.severe")
    }

    @Test
    fun `catalogue-added cards show even when persona is not selected`() {
        val ctx = Fixtures.context(personas = setOf(Persona.GENERAL))
        val ids = Ranker.rank(CardRegistry.all, ctx, emptyMap(), mapOf("events.bestday" to CardPref("events.bestday", added = true))).map { it.spec.id }
        assertThat(ids).contains("events.bestday")
        assertThat(ids).doesNotContain("events.comfort")
    }
}
