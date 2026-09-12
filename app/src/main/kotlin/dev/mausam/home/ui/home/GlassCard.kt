package dev.mausam.home.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.CardValue
import dev.mausam.home.domain.cards.RenderedCard
import dev.mausam.home.domain.cards.Tone
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.domain.model.SourceInfo
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.ui.common.AccentIconDisc
import dev.mausam.home.ui.common.RollingValue
import dev.mausam.home.ui.common.ToneChip
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.lensEdge
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.Fluent
import dev.mausam.home.ui.theme.ImdTiers
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.accentSet
import kotlinx.coroutines.delay

@Composable
fun toneColor(tone: Tone): Color? {
    val dark = LocalIsDark.current
    return when (tone) {
        Tone.NEUTRAL -> null
        Tone.GOOD -> if (dark) Color(0xFF6FD275) else Color(0xFF1B7F2A)
        Tone.CAUTION -> ImdTiers.of(WarningSeverity.YELLOW, dark).accent
        Tone.WARNING -> ImdTiers.of(WarningSeverity.ORANGE, dark).accent
        Tone.DANGER -> ImdTiers.of(WarningSeverity.RED, dark).accent
    }
}

/** What a card is about, for its pending or empty state. */
fun placeholderIcon(cardId: String): WeatherIcon = when (cardId) {
    "health.aqi" -> WeatherIcon.AIR
    "health.pollen" -> WeatherIcon.POLLEN
    "beach.tides" -> WeatherIcon.TIDES
    "beach.sea" -> WeatherIcon.WAVES
    "travel.destinations" -> WeatherIcon.HEART_BROKEN
    "travel.packing" -> WeatherIcon.LUGGAGE
    "parents.school" -> WeatherIcon.SCHOOL
    "agri.rainfall" -> WeatherIcon.RAINDROP
    "agri.advisory" -> WeatherIcon.AGRO
    "fitness.run" -> WeatherIcon.RUN
    "events.comfort" -> WeatherIcon.COMFORT
    "general.warnings", "parents.severe", "travel.severe" -> WeatherIcon.SHIELD
    else -> WeatherIcon.PENDING
}

private fun toneLabel(tone: Tone) = when (tone) {
    Tone.GOOD -> "Good".tr(); Tone.CAUTION -> "Caution".tr(); Tone.WARNING -> "Watch".tr(); Tone.DANGER -> "Danger".tr(); Tone.NEUTRAL -> ""
}

/**
 * One data point plus one action. Glass card at tier 1 with its own accent wash and icon disc;
 * nothing inside it is glass. Press scales to 0.98 with a light haptic and the rim rotates; long
 * press offers pin, hide, move to top. The card's bounds morph into the detail sheet; the content
 * is scaled rather than remeasured during the morph so text never re-wraps mid-flight.
 */
@Composable
fun SharedTransitionScope.GlassCard(
    card: RenderedCard,
    index: Int,
    haze: HazeState,
    freshness: Freshness?,
    sourceInfo: SourceInfo?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    refract: Boolean,
    onTap: () -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11y = LocalMausamA11y.current
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")

    // Stagger-in: rises 24 dp and fades, 40 ms per card.
    var shown by remember { mutableStateOf(a11y.reduceMotion) }
    LaunchedEffect(Unit) { if (!shown) { delay(40L * index.coerceAtMost(8)); shown = true } }
    val enterAlpha by animateFloatAsState(if (shown) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "a")
    val enterY by animateFloatAsState(if (shown) 0f else 24f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "y")

    val value = card.value
    val stale = freshness?.isStale == true
    val wide = card.spec.wide
    val tone = (value as? CardValue.Ready)?.tone ?: Tone.NEUTRAL
    val toneCol = toneColor(tone)
    val accent = accentSet(toneCol?.takeIf { tone == Tone.DANGER || tone == Tone.WARNING } ?: Fluent.forCard(card.spec.id))
    val description = buildString {
        append(card.spec.title.tr()); append(", ")
        when (value) {
            is CardValue.Ready -> { append(value.primary); value.unit?.let { append(" $it") }; value.secondary?.let { append(", $it") } }
            is CardValue.Pending -> append(value.reason)
            is CardValue.Unavailable -> append(value.message)
        }
        freshness?.let { append(", ${it.label.tr()}") }
        append(", "); append("source %s".trf(card.spec.sourceLabel.tr()))
    }
    val icon = when (value) {
        is CardValue.Ready -> value.icon
        is CardValue.Pending, is CardValue.Unavailable -> placeholderIcon(card.spec.id)
    }

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = enterAlpha; translationY = enterY * density
            }
            .sharedBounds(
                rememberSharedContentState(key = "card-${card.spec.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            )
            .mausamGlass(haze, GlassTier.CARD, MausamRadius.cardShape, pressed = pressed, wash = accent.wash)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .lensEdge(MausamRadius.Card, enabled = refract)
            .pointerInput(card.spec.id) {
                detectTapGestures(
                    // No haptic on press: a finger landing on a card to scroll is not a tap.
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onTap() },
                    onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { onTap(); true }
                customActions = listOf(
                    CustomAccessibilityAction(if (card.pinned) "Unpin".tr() else "Pin to top".tr()) { onPin(); true },
                    CustomAccessibilityAction("Move to top".tr()) { onTop(); true },
                    CustomAccessibilityAction("Hide".tr()) { onHide(); true },
                )
            },
    ) {
        if (wide) WideBody(card, value, icon, accent, toneCol, tone, stale, sourceInfo, freshness)
        else CompactBody(card, value, icon, accent, toneCol, tone, stale, sourceInfo, freshness)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (card.pinned) "Unpin".tr() else "Pin to top".tr()) }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("Move to top".tr()) }, onClick = { menu = false; onTop() })
            DropdownMenuItem(text = { Text("Hide".tr()) }, onClick = { menu = false; haptics.performHapticFeedback(HapticFeedbackType.Reject); onHide() })
        }
    }
}

@Composable
private fun CardTitle(card: RenderedCard, stale: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            card.spec.title.tr(),
            style = if (card.pinned) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
            color = if (stale) cs.onSurfaceVariant else cs.onSurface, maxLines = 1,
        )
        if (card.pinned) {
            Spacer(Modifier.width(Space.s1))
            Icon(Icons.Rounded.PushPin, contentDescription = "Pinned".tr(), modifier = Modifier.width(16.dp), tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourceLine(card: RenderedCard, sourceInfo: SourceInfo?, freshness: Freshness?) {
    Text(
        listOfNotNull((sourceInfo?.label ?: card.spec.sourceLabel).ifBlank { null }?.tr(), freshness?.label?.tr()).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
    )
}

@Composable
private fun CompactBody(
    card: RenderedCard, value: CardValue, icon: WeatherIcon?, accent: dev.mausam.home.ui.theme.AccentSet,
    toneCol: Color?, tone: Tone, stale: Boolean, sourceInfo: SourceInfo?, freshness: Freshness?,
) {
    val cs = MaterialTheme.colorScheme
    val a11y = LocalMausamA11y.current
    Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(Space.cardPadding), verticalAlignment = Alignment.CenterVertically) {
        AccentIconDisc(icon, accent, 60.dp, animated = !stale)
        Spacer(Modifier.width(Space.s4))
        Column(Modifier.weight(1f)) {
            CardTitle(card, stale)
            Spacer(Modifier.height(2.dp))
            when (value) {
                is CardValue.Ready -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingValue(
                            text = value.primary, numeric = value.numeric,
                            style = if (a11y.largeText) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                            color = if (stale) cs.outline else cs.onSurface,
                        )
                        value.unit?.let {
                            Spacer(Modifier.width(Space.s1))
                            Text(it, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
                        }
                        if (toneCol != null && tone != Tone.NEUTRAL) {
                            Spacer(Modifier.width(Space.s2))
                            ToneChip(toneLabel(tone), toneCol, Modifier.padding(bottom = 5.dp))
                        }
                    }
                    value.secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2) }
                }
                is CardValue.Pending -> Text(value.reason, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                is CardValue.Unavailable -> Text(value.message, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.s1))
            SourceLine(card, sourceInfo, freshness)
        }
    }
}

@Composable
private fun WideBody(
    card: RenderedCard, value: CardValue, icon: WeatherIcon?, accent: dev.mausam.home.ui.theme.AccentSet,
    toneCol: Color?, tone: Tone, stale: Boolean, sourceInfo: SourceInfo?, freshness: Freshness?,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(Space.cardPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(card, stale)
                    if (toneCol != null && tone != Tone.NEUTRAL) { Spacer(Modifier.width(Space.s2)); ToneChip(toneLabel(tone), toneCol) }
                }
                Spacer(Modifier.height(Space.s1))
                when (value) {
                    is CardValue.Ready -> {
                        Text(
                            value.primary + (value.unit?.let { " $it" } ?: ""),
                            style = MaterialTheme.typography.headlineSmallEmphasized, color = if (stale) cs.outline else cs.onSurface, maxLines = 3,
                        )
                        value.secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2) }
                    }
                    is CardValue.Pending -> Text(value.reason, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    is CardValue.Unavailable -> Text(value.message, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Space.s3))
            AccentIconDisc(icon, accent, 72.dp, animated = !stale)
        }
        Spacer(Modifier.height(Space.s2))
        SourceLine(card, sourceInfo, freshness)
    }
}
