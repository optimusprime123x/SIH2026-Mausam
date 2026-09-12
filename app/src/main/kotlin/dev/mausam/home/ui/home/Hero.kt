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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.briefs.SpokenBrief
import dev.mausam.home.ui.common.rememberSpeaker
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.common.RollingValue
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.BackdropSampler
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.glass.mausamSoftGlass
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.robotoFlexAt
import kotlin.math.roundToInt

/**
 * Location, temperature, condition, "as of HH:MM", plus two glass chips (feels like, wind). It sits
 * directly on the illustrated scene with a scrim; collapses from 240 dp to a 76 dp glass bar where
 * the temperature shrinks from 96 sp/200 to 28 sp/500 and slides to the leading edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hero(state: HomeUiState, heightPx: Float, collapse: Float, haze: HazeState, sampler: BackdropSampler) {
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
            .then(if (collapsed) Modifier.mausamSoftGlass(sampler, GlassTier.TOOLBAR, barShape, shadow = collapse > 0.9f) else Modifier)
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
                    Text(cur?.condition?.label()?.tr() ?: "", style = MaterialTheme.typography.labelSmall, color = inkSoft, maxLines = 1)
                }
                if (cur != null) MeteoconIcon(WeatherIcon.forCondition(cur.condition, cur.isDay), size = 40.dp, tint = ink)
            }
        } else {
            // A soft drop shadow keeps white type legible on pale skies (fog, noon) without a heavier scrim.
            val lift = Shadow(Color(0x66081020), Offset(0f, 2f), blurRadius = 10f)
            val speaker = rememberSpeaker()
            Column(Modifier.fillMaxSize().padding(horizontal = Space.screenMargin, vertical = Space.s4), verticalArrangement = Arrangement.Bottom) {
                // City name with the spoken-brief button on the trailing edge.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.location?.name ?: "—",
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(shadow = lift), color = ink,
                        modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    val speaking = speaker?.speaking == true
                    SpeakButton(
                        speaking = speaking, haze = haze,
                        enabled = cur != null && (speaker == null || speaker.available),
                        onClick = {
                            if (speaker == null) return@SpeakButton
                            if (speaking) speaker.stop()
                            else speaker.speak(SpokenBrief.compose(state.location?.name, cur, state.bundle?.airQuality, state.activeWarnings.firstOrNull(), fmt))
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RollingValue(
                            text = cur?.let { fmt.temp(it.temperatureC) } ?: "—",
                            numeric = cur?.temperatureC,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, fontFamily = family, lineHeight = fontSize * 1.05f, shadow = lift),
                            color = ink,
                        )
                        val hiLo = state.bundle?.daily?.firstOrNull()?.let { "H %s  L %s".trf(fmt.temp(it.maxC), fmt.temp(it.minC)) }
                        Text(
                            listOfNotNull(cur?.condition?.label()?.tr(), hiLo).joinToString("  ·  "),
                            style = MaterialTheme.typography.titleMedium.copy(shadow = lift), color = ink, fontWeight = FontWeight.Medium,
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
                // AQI leads, tinted by its CPCB category, so it survives when a narrow screen drops the last chip.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s1), maxLines = 1) {
                    state.bundle?.airQuality?.let { air ->
                        HeroChip("AQI %d".trf(air.aqi), haze, tint = aqiChipTint(air.aqi))
                    }
                    cur?.feelsLikeC?.let { HeroChip("Feels %s".trf(fmt.temp(it)), haze) }
                    cur?.windKph?.let { HeroChip("Wind %s".trf(fmt.speed(it)), haze) }
                    cur?.humidityPct?.let { HeroChip("%d%% humidity".trf(it), haze) }
                }
                Spacer(Modifier.height(Space.s2))
                val stale = state.freshness?.isStale == true || state.refreshFailed
                Text(
                    when {
                        state.freshness == null -> "loading…".tr()
                        state.refreshFailed -> "couldn't refresh · %s".trf(state.freshness.label)
                        else -> state.freshness.label
                    },
                    style = MaterialTheme.typography.labelMedium.copy(shadow = lift),
                    color = if (stale) Color(0xFFFFD27A) else inkSoft,
                )
            }
        }
    }
}

/** Speaker while idle, pause while the brief is being read; the same glass as the hero chips. */
@Composable
private fun SpeakButton(speaking: Boolean, haze: HazeState, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .mausamGlass(haze, GlassTier.TOOLBAR, CircleShape, tint = Color(0xFF1B2A44), shadow = false)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = if (speaking) "Stop reading".tr() else "Read the weather aloud".tr() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (speaking) Icons.Rounded.Pause else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A small glass pill over the scene: toolbar-tier blur with a deep-blue tint so white text reads on any sky. */
@Composable
private fun HeroChip(text: String, haze: HazeState, tint: Color = Color(0xFF1B2A44)) {
    Box(
        Modifier
            .mausamGlass(haze, GlassTier.TOOLBAR, RoundedCornerShape(50), tint = tint, shadow = false)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** CPCB category colours, darkened so white text stays legible on the chip. */
private fun aqiChipTint(aqi: Int): Color = when (IndianAqi.category(aqi)) {
    IndianAqi.Category.GOOD -> Color(0xFF1F6B3A)
    IndianAqi.Category.SATISFACTORY -> Color(0xFF4F6F1E)
    IndianAqi.Category.MODERATE -> Color(0xFF8A6A12)
    IndianAqi.Category.POOR -> Color(0xFF9A4E12)
    IndianAqi.Category.VERY_POOR -> Color(0xFF8C2A2A)
    IndianAqi.Category.SEVERE -> Color(0xFF5E1B3A)
}
