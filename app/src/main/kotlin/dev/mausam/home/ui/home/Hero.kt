package dev.mausam.home.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.robotoFlexAt

/**
 * Location, temperature, condition, "as of HH:MM". No container of its own: it sits on the scene
 * with a scrim. Collapses from 220 dp to a 72 dp glass bar; the temperature shrinks from
 * displayLarge/200 to headlineMedium/400 and slides to the leading edge.
 */
@Composable
fun Hero(state: HomeUiState, heightPx: Float, collapse: Float, haze: HazeState) {
    val density = LocalDensity.current
    val heightDp = with(density) { heightPx.toDp() }
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val cur = state.bundle?.current
    val fmt = state.context?.fmt ?: Formatter()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryScale by animateFloatAsState(if (entered) 1f else 0.95f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "heroEntry")

    val fontSize = lerp(57.sp, 28.sp, collapse)
    val weight = (200 + (200 * collapse).toInt()).let { (it / 25) * 25 }
    val family = remember(weight) { robotoFlexAt(weight, opsz = 64f) }
    val collapsed = collapse > 0.6f
    val barShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

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
        if (!collapsed) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to (if (dark) Color.Black else cs.scrim).copy(alpha = 0.28f)),
                ),
            )
        }
        val textColor = if (collapsed) cs.onSurface else if (dark) Color.White else cs.onSurface
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.screenMargin),
            verticalAlignment = if (collapsed) Alignment.CenterVertically else Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f).padding(bottom = if (collapsed) 0.dp else Space.s3)) {
                if (!collapsed) {
                    Text(state.location?.name ?: "—", style = MaterialTheme.typography.titleLarge, color = textColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollingValue(
                        text = cur?.let { fmt.temp(it.temperatureC) } ?: "—",
                        numeric = cur?.temperatureC,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, fontFamily = family, lineHeight = fontSize * 1.1f),
                        color = textColor,
                    )
                    if (collapsed) {
                        Spacer(Modifier.width(Space.s3))
                        Column {
                            Text(state.location?.name ?: "", style = MaterialTheme.typography.titleSmall, color = textColor, maxLines = 1)
                            Text(cur?.condition?.label() ?: "", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (!collapsed) {
                    val hiLo = state.bundle?.daily?.firstOrNull()?.let { "H ${fmt.temp(it.maxC)}  L ${fmt.temp(it.minC)}" }
                    Text(
                        listOfNotNull(cur?.condition?.label(), hiLo).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = textColor,
                    )
                    Text(
                        state.freshness?.label ?: "loading…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.freshness?.isStale == true) cs.outline else textColor.copy(alpha = 0.92f),
                    )
                }
            }
            if (!collapsed && cur != null) {
                MeteoconIcon(WeatherIcon.forCondition(cur.condition, cur.isDay), size = 96.dp, modifier = Modifier.padding(bottom = Space.s3))
            }
        }
    }
}
