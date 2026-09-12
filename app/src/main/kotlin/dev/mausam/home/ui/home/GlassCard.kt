package dev.mausam.home.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.CardValue
import dev.mausam.home.domain.cards.RenderedCard
import dev.mausam.home.domain.cards.Tone
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.domain.model.SourceInfo
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.common.RollingValue
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.lensEdge
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.ImdTiers
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun toneColor(tone: Tone): Color? {
    val dark = LocalIsDark.current
    return when (tone) {
        Tone.NEUTRAL -> null
        Tone.GOOD -> if (dark) Color(0xFF6FD275) else Color(0xFF1B5E20)
        Tone.CAUTION -> ImdTiers.of(WarningSeverity.YELLOW, dark).accent
        Tone.WARNING -> ImdTiers.of(WarningSeverity.ORANGE, dark).accent
        Tone.DANGER -> ImdTiers.of(WarningSeverity.RED, dark).accent
    }
}

/**
 * One data point plus one action. Glass card at tier 1; nothing inside it is glass. Press scales
 * to 0.98 with a light haptic and the rim rotates; long press offers pin, hide, move to top.
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
    val accent = toneColor(tone)
    val description = buildString {
        append(card.spec.title); append(", ")
        when (value) {
            is CardValue.Ready -> { append(value.primary); value.unit?.let { append(" $it") }; value.secondary?.let { append(", $it") } }
            is CardValue.Pending -> append(value.reason)
            is CardValue.Unavailable -> append(value.message)
        }
        freshness?.let { append(", ${it.label}") }
        append(", source ${card.spec.sourceLabel}")
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
            )
            .mausamGlass(haze, GlassTier.CARD, MausamRadius.cardShape, pressed = pressed)
            .lensEdge(MausamRadius.Card, enabled = refract)
            .pointerInput(card.spec.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() },
                    onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
            }
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (wide) 132.dp else 96.dp)
                .padding(Space.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accent != null) {
                Box(Modifier.width(4.dp).fillMaxHeight().heightIn(min = 48.dp).clip(MausamRadius.chipShape).background(accent))
                Spacer(Modifier.width(Space.s3))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.spec.title,
                        style = if (card.pinned) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                        color = if (stale) cs.onSurfaceVariant else cs.onSurface,
                    )
                    if (card.pinned) {
                        Spacer(Modifier.width(Space.s1))
                        Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", modifier = Modifier.width(16.dp), tint = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.padding(2.dp))
                when (value) {
                    is CardValue.Ready -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingValue(
                                text = value.primary, numeric = value.numeric,
                                style = if (a11y.largeText) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                                color = if (stale) cs.outline else cs.onSurface,
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "value-${card.spec.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                            )
                            value.unit?.let {
                                Spacer(Modifier.width(Space.s1))
                                Text(it, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        value.secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2) }
                    }
                    is CardValue.Pending -> Text(value.reason, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    is CardValue.Unavailable -> Text(value.message, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.padding(2.dp))
                Text(
                    listOfNotNull("Source: ${sourceInfo?.label ?: card.spec.sourceLabel}", freshness?.label).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1,
                )
            }
            val icon = when (value) {
                is CardValue.Ready -> value.icon
                is CardValue.Pending -> WeatherIcon.NOT_AVAILABLE
                is CardValue.Unavailable -> null
            }
            if (icon != null) {
                Spacer(Modifier.width(Space.s3))
                MeteoconIcon(icon, size = if (wide) 64.dp else 48.dp, animated = !stale)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (card.pinned) "Unpin" else "Pin to top") }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("Move to top") }, onClick = { menu = false; onTop() })
            DropdownMenuItem(text = { Text("Hide") }, onClick = { menu = false; haptics.performHapticFeedback(HapticFeedbackType.Reject); onHide() })
        }
    }
}
