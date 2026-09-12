package dev.mausam.home.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.micaBase
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Fluent "mica" ground: an opaque wallpaper-tinted base with three large, slow aurora blobs in
 * the theme's primary, tertiary and secondary containers. It is what the glass blurs, so cards
 * pick up colour from the wallpaper palette wherever they sit. Gradient stops never fade to
 * `Color.Transparent` (black at zero alpha), which would leave dark halos; they fade to the same
 * hue at zero alpha instead.
 */
@Composable
fun AuroraBackdrop(animated: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val base = micaBase()
    val a = if (dark) lerp(cs.primaryContainer, cs.primary, 0.35f) else lerp(cs.primaryContainer, cs.primary, 0.15f)
    val b = if (dark) lerp(cs.tertiaryContainer, cs.tertiary, 0.35f) else lerp(cs.tertiaryContainer, cs.tertiary, 0.15f)
    val c = if (dark) lerp(cs.secondaryContainer, cs.secondary, 0.25f) else lerp(cs.secondaryContainer, cs.secondary, 0.10f)
    val strength = if (dark) 0.55f else 0.62f
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t = ((now - start) / 1_000_000_000f) }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        drawRect(base)
        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            val center = Offset(cx, cy)
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = strength), 0.45f to color.copy(alpha = strength * 0.45f), 1f to color.copy(alpha = 0f),
                    center = center, radius = r,
                ),
                radius = r, center = center,
            )
        }
        val w = size.width; val h = size.height
        blob(a, w * (0.15f + 0.06f * sin(t * 0.11f)), h * (0.42f + 0.05f * cos(t * 0.09f)), w * 0.75f)
        blob(b, w * (0.92f - 0.05f * cos(t * 0.13f)), h * (0.62f + 0.06f * sin(t * 0.08f)), w * 0.70f)
        blob(c, w * (0.45f + 0.08f * sin(t * 0.07f)), h * (0.95f + 0.03f * cos(t * 0.1f)), w * 0.65f)
    }
}
