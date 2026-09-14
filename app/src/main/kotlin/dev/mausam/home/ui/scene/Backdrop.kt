package dev.mausam.home.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
 *
 * The renderer is separate from the composable so a glass surface can paint the same picture
 * into its own bitmap (see `softGlass`): [frame] ticks whenever the picture changes.
 */
class AuroraRenderer(
    private val base: Color,
    private val a: Color,
    private val b: Color,
    private val c: Color,
    private val strength: Float,
) {
    var frame by mutableIntStateOf(0)
        internal set
    internal var t = 0f

    // Radial brushes are built once per width and slid into place with translate(); building
    // three gradients per frame was measurable on a busy scene.
    private var brushWidth = -1f
    private val brushes = arrayOfNulls<Brush>(3)
    private val radii = FloatArray(3)
    private fun brushesFor(w: Float) {
        if (w == brushWidth) return
        brushWidth = w
        val colours = arrayOf(a, b, c); val fr = floatArrayOf(0.75f, 0.70f, 0.65f)
        for (i in 0 until 3) {
            radii[i] = w * fr[i]
            brushes[i] = Brush.radialGradient(
                0f to colours[i].copy(alpha = strength), 0.45f to colours[i].copy(alpha = strength * 0.45f), 1f to colours[i].copy(alpha = 0f),
                center = Offset.Zero, radius = radii[i],
            )
        }
    }

    fun DrawScope.drawAurora(size: Size) {
        drawRect(base, size = size)
        val w = size.width; val h = size.height
        brushesFor(w)
        fun blob(i: Int, cx: Float, cy: Float) {
            translate(cx, cy) { drawCircle(brushes[i]!!, radius = radii[i], center = Offset.Zero) }
        }
        blob(0, w * (0.15f + 0.06f * sin(t * 0.11f)), h * (0.42f + 0.05f * cos(t * 0.09f)))
        blob(1, w * (0.92f - 0.05f * cos(t * 0.13f)), h * (0.62f + 0.06f * sin(t * 0.08f)))
        blob(2, w * (0.45f + 0.08f * sin(t * 0.07f)), h * (0.95f + 0.03f * cos(t * 0.1f)))
    }
}

@Composable
fun rememberAuroraRenderer(animated: Boolean): AuroraRenderer {
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val base = micaBase()
    val renderer = remember(cs, dark, base) {
        AuroraRenderer(
            base = base,
            a = if (dark) lerp(cs.primaryContainer, cs.primary, 0.35f) else lerp(cs.primaryContainer, cs.primary, 0.15f),
            b = if (dark) lerp(cs.tertiaryContainer, cs.tertiary, 0.35f) else lerp(cs.tertiaryContainer, cs.tertiary, 0.15f),
            c = if (dark) lerp(cs.secondaryContainer, cs.secondary, 0.25f) else lerp(cs.secondaryContainer, cs.secondary, 0.10f),
            strength = if (dark) 0.70f else 0.62f,
        )
    }
    LaunchedEffect(renderer, animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        var n = 0
        // The blobs move at about a tenth of a radian per second: a third of the frame rate is plenty.
        while (true) withFrameNanos { now ->
            if (++n % 3 == 0) {
                renderer.t = (now - start) / 1_000_000_000f
                renderer.frame++
            }
        }
    }
    return renderer
}

@Composable
fun AuroraBackdrop(renderer: AuroraRenderer, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        renderer.frame // read so the canvas repaints with the renderer
        with(renderer) { drawAurora(size) }
    }
}

@Composable
fun AuroraBackdrop(animated: Boolean, modifier: Modifier = Modifier) {
    AuroraBackdrop(rememberAuroraRenderer(animated), modifier)
}
