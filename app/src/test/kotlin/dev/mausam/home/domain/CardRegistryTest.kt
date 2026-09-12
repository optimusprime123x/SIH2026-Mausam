package dev.mausam.home.domain

import com.google.common.truth.Truth.assertThat
import dev.mausam.home.domain.briefs.AlertPolicy
import dev.mausam.home.domain.briefs.BriefComposer
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.CardValue
import dev.mausam.home.domain.cards.RunScore
import dev.mausam.home.domain.model.MarineState
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.personas.Persona
import org.junit.Test

class CardRegistryTest {
    @Test
    fun `card ids are unique and every persona has cards`() {
        val ids = CardRegistry.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
        Persona.entries.forEach { p -> assertThat(CardRegistry.forPersona(p)).isNotEmpty() }
    }

    @Test
    fun `every card survives an empty bundle without throwing`() {
        val empty = WeatherBundle.empty(Fixtures.delhi, Fixtures.now.toInstant())
        val ctx = Fixtures.context(bundle = empty, distanceToCoastKm = 5.0)
        CardRegistry.all.forEach { spec ->
            if (spec.gate(ctx)) {
                val v = spec.fetch(ctx)
                assertThat(v).isNotNull()
            }
        }
    }

    @Test
    fun `every card renders on a full bundle and pending cards never fake numbers`() {
        val b = Fixtures.bundle(
            warnings = listOf(Fixtures.warning(Fixtures.now), Fixtures.warning(Fixtures.now, WarningSeverity.YELLOW, "Dense fog")),
            rainFrom = 7, rainTo = 9,
            marine = MarineState(Fixtures.now.toInstant(), 1.4, 8.0, 220, 1.1, 28.5),
        )
        val ctx = Fixtures.context(bundle = b, distanceToCoastKm = 3.0, destinations = listOf(Fixtures.bundle(location = Fixtures.mumbai, warnings = listOf(Fixtures.warning(Fixtures.now, WarningSeverity.RED, "Very heavy rain")))))
        val values = CardRegistry.all.filter { it.gate(ctx) }.associate { it.id to it.fetch(ctx) }
        assertThat(values["health.pollen"]).isInstanceOf(CardValue.Pending::class.java)
        assertThat(values["beach.tides"]).isInstanceOf(CardValue.Pending::class.java)
        assertThat(values["agri.rainfall"]).isInstanceOf(CardValue.Pending::class.java)
        assertThat((values["parents.school"] as CardValue.Ready).primary).isEqualTo("Rain likely")
        assertThat((values["commute.leave"] as CardValue.Ready).primary).startsWith("+")
        assertThat((values["beach.sea"] as CardValue.Ready).unit).isEqualTo("m")
        assertThat((values["travel.severe"] as CardValue.Ready).primary).isEqualTo("Red")
        assertThat((values["health.aqi"] as CardValue.Ready).primary).isEqualTo("120")
        assertThat(values).containsKey("commute.visibility") // fog warning opens the gate
    }

    @Test
    fun `best run window lands in the cool morning`() {
        val ctx = Fixtures.context()
        val w = RunScore.bestWindow(ctx.bundle.hourly, 120, ctx.now)!!
        assertThat(w.start.atZone(Fixtures.zone).hour).isAtMost(8)
        assertThat(w.score).isAtLeast(60)
    }

    @Test
    fun `briefs are persona shaped and alert policy pushes orange and red once`() {
        val b = Fixtures.bundle(rainFrom = 7, rainTo = 9)
        val ctx = Fixtures.context(bundle = b)
        val parents = BriefComposer.compose(Persona.PARENTS, ctx, evening = false)
        assertThat(parents.title).contains("Rain likely")
        assertThat(parents.body).contains("Umbrellas")
        val fitness = BriefComposer.compose(Persona.FITNESS, ctx, evening = false)
        assertThat(fitness.title).startsWith("Best run window")
        val gardener = BriefComposer.compose(Persona.AGRICULTURE, ctx, evening = false)
        assertThat(gardener.title).contains("rain expected this week")

        val warnings = listOf(
            Fixtures.warning(Fixtures.now, WarningSeverity.YELLOW, "Light rain"),
            Fixtures.warning(Fixtures.now, WarningSeverity.ORANGE, "Heavy rain"),
            Fixtures.warning(Fixtures.now, WarningSeverity.RED, "Cyclone"),
        )
        val toPush = AlertPolicy.toPush(warnings, alreadyNotified = setOf("w-cyclone"))
        assertThat(toPush.map { it.event }).containsExactly("Heavy rain")
    }
}
