package dev.mausam.home.ui.home

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.common.RollingValue
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.robotoFlexAt
import kotlin.math.roundToInt

/**
 * Location, temperature, condition, "as of HH:MM", plus two glass chips (feels like, wind). It sits
 * directly on the illustrated scene with a scrim; collapses from 240 dp to a 76 dp glass bar where
 * the temperature shrinks from 96 sp/200 to 28 sp/500 and slides to the leading edge.
 */
@Composable
fun Hero(state: HomeUiState, heightPx: Float, collapse: Float, haze: HazeState) {
    val density = LocalDensity.current
    val heightDp = with(density) { heightPx.toDp() }
    val cs = MaterialTheme.colorScheme
    val cur = state.bundle?.current
    val fmt = state.context?.fmt ?: Formatter()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryScale by animateFloatAsState(if (entered) 1f else 0.95f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "heroEntry")

    val fontSize = lerp(88.sp, 28.sp, collapse)
    val weight = (200 + (300 * collapse).toInt()).let { (it / 25) * 25 }
    val family = remember(weight) { robotoFlexAt(weight, opsz = 64f) }
    val collapsed = collapse > 0.6f
    // The condition icon floats: a slow 4 dp bob, off under reduce-motion.
    val a11y = LocalMausamA11y.current
    val bob = if (a11y.sceneAnimated) {
        val t = rememberInfiniteTransition(label = "bob")
        t.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse), label = "bobY").value
    } else 0f
    val barShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val ink = if (collapsed) cs.onSurface else Color.White
    val inkSoft = if (collapsed) cs.onSurfaceVariant else Color.White.copy(alpha = 0.86f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .then(if (collapsed) Modifier.mausamGlass(haze, GlassTier.TOOLBAR, barShape, shadow = collapse > 0.9f) else Modifier)
            .graphicsLayer { scaleX = entryScale; scaleY = entryScale }
            .semantics {
                contentDescription = buildString {
                    append(state.location?.name ?: "")
                    cur?.let { append(", ${fmt.temp(it.temperatureC, true)}, ${it.condition.label()}") }
                    state.freshness?.let { append(", ${it.label}") }
                }
            },
    ) {
        if (collapsed) {
            Row(Modifier.fillMaxSize().padding(horizontal = Space.screenMargin), verticalAlignment = Alignment.CenterVertically) {
                RollingValue(
                    text = cur?.let { fmt.temp(it.temperatureC) } ?: "—",
                    numeric = cur?.temperatureC,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, fontFamily = family, lineHeight = fontSize * 1.05f),
                    color = ink,
                )
                Spacer(Modifier.width(Space.s3))
                Column(Modifier.weight(1f)) {
                    Text(state.location?.name ?: "", style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                    Text(cur?.condition?.label() ?: "", style = MaterialTheme.typography.labelSmall, color = inkSoft, maxLines = 1)
                }
                if (cur != null) MeteoconIcon(WeatherIcon.forCondition(cur.condition, cur.isDay), size = 40.dp, tint = ink)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = Space.screenMargin, vertical = Space.s4), verticalArrangement = Arrangement.Bottom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.location?.name ?: "—", style = MaterialTheme.typography.titleLargeEmphasized, color = ink)
                        RollingValue(
                            text = cur?.let { fmt.temp(it.temperatureC) } ?: "—",
                            numeric = cur?.temperatureC,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, fontFamily = family, lineHeight = fontSize * 1.05f),
                            color = ink,
                        )
                        val hiLo = state.bundle?.daily?.firstOrNull()?.let { "H ${fmt.temp(it.maxC)}  L ${fmt.temp(it.minC)}" }
                        Text(
                            listOfNotNull(cur?.condition?.label(), hiLo).joinToString("  ·  "),
                            style = MaterialTheme.typography.titleMedium, color = ink, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (cur != null) {
                        MeteoconIcon(
                            icon = WeatherIcon.forCondition(cur.condition, cur.isDay), size = 112.dp, tint = Color.White,
                            modifier = Modifier.graphicsLayer { this.translationY = bob * this.density },
                        )
                    }
                }
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    cur?.feelsLikeC?.let { HeroChip("Feels ${fmt.temp(it)}", haze) }
                    cur?.windKph?.let { HeroChip("Wind ${it.roundToInt()} km/h", haze) }
                    cur?.humidityPct?.let { HeroChip("$it% humidity", haze) }
                }
                Spacer(Modifier.height(Space.s2))
                Text(
                    state.freshness?.label ?: "loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.freshness?.isStale == true) Color(0xFFFFD27A) else inkSoft,
                )
            }
        }
    }
}

/** A small glass pill over the scene: toolbar-tier blur with a deep-blue tint so white text reads on any sky. */
@Composable
private fun HeroChip(text: String, haze: HazeState) {
    Box(
        Modifier
            .mausamGlass(haze, GlassTier.TOOLBAR, RoundedCornerShape(50), tint = Color(0xFF1B2A44), shadow = false)
            .padding(horizontal = Space.s3, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, softWrap = false)
    }
}
