package dev.mausam.home.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.ui.common.AccentIconDisc
import dev.mausam.home.ui.common.SectionHeader
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.home.HomeUiState
import dev.mausam.home.ui.onboarding.personaIcon
import dev.mausam.home.ui.theme.Fluent
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.accentSet

/** The card catalogue grouped by persona, each card with its accent disc. A switch shows or hides it. */
@Composable
fun CardCatalogue(state: HomeUiState, haze: HazeState, onToggle: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.32f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .mausamGlass(haze, GlassTier.SHEET, MausamRadius.sheetShape)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = Space.s3).height(4.dp).width(32.dp).background(cs.outlineVariant, MausamRadius.chipShape))
            Text("Add cards", style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface, modifier = Modifier.padding(horizontal = Space.s6, vertical = Space.s4))
            LazyColumn(contentPadding = PaddingValues(horizontal = Space.s5, vertical = Space.s2)) {
                Persona.entries.forEach { persona ->
                    val cards = CardRegistry.forPersona(persona)
                    item(key = "h-${persona.key}") {
                        SectionHeader(persona.title, personaIcon(persona), Fluent.forPersona(persona), persona.tagline)
                    }
                    cards.forEach { spec ->
                        item(key = spec.id) {
                            val pref = state.prefs[spec.id]
                            val shown = pref?.hidden != true && (persona == Persona.GENERAL || persona in state.settings.personas || pref?.added == true)
                            val accent = accentSet(Fluent.forCard(spec.id))
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                                AccentIconDisc(null, accent, 36.dp, animated = false)
                                Spacer(Modifier.width(Space.s3))
                                Column(Modifier.weight(1f)) {
                                    Text(spec.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                                    Text(spec.sourceLabel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                                Spacer(Modifier.padding(Space.s1))
                                Switch(checked = shown, onCheckedChange = { onToggle(spec.id, it) })
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.s8)) }
            }
        }
    }
}
