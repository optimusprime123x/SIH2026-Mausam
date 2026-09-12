package dev.mausam.home.ui.scene

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mausam.home.domain.model.SceneKind
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.micaBase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ambient layer: always alive when effects are on, painted on one Canvas behind the hero and
 * the first cards. Particle state lives in plain arrays; one `frame` int is the only observable,
 * read in the draw phase, so the loop never recomposes anything. Crossfades over 600 ms when the
 * condition changes (the one legal tween).
 */
@Composable
fun WeatherScene(
    kind: SceneKind,
    windKph: Float,
    intensity: Float,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(targetState = kind, animationSpec = tween(600), label = "scene", modifier = modifier) { k ->
        SceneLayer(k, windKph, intensity, animated, Modifier.clearAndSetSemantics { })
    }
}

/** Mutable simulation state shared by every scene kind. */
private class SceneState(seed: Int) {
    val rnd = Random(seed)
    var frame by mutableIntStateOf(0)
    var t = 0f // seconds
    var size = Size.Zero
    var seeded = false
    val n = 160
    val x = FloatArray(n)
    val y = FloatArray(n)
    val v = FloatArray(n)
    val len = FloatArray(n)
    val phase = FloatArray(n) { rnd.nextFloat() * 6.28f }
    var nextFlashAt = 8f + rnd.nextFloat() * 12f
    var flashFrames = 0

    fun seed(size: Size, count: Int, speedMin: Float, speedMax: Float, lenMin: Float, lenMax: Float) {
        this.size = size
        for (i in 0 until count) {
            x[i] = rnd.nextFloat() * size.width
            y[i] = rnd.nextFloat() * size.height
            v[i] = speedMin + rnd.nextFloat() * (speedMax - speedMin)
            len[i] = lenMin + rnd.nextFloat() * (lenMax - lenMin)
        }
        seeded = true
    }

    fun step(dt: Float, count: Int, windX: Float) {
        t += dt
        for (i in 0 until count) {
            y[i] += v[i] * dt
            x[i] += windX * dt
            if (y[i] > size.height + len[i]) { y[i] = -len[i]; x[i] = rnd.nextFloat() * size.width }
            if (x[i] > size.width + 20) x[i] = -20f else if (x[i] < -20) x[i] = size.width + 20f
        }
        if (flashFrames > 0) flashFrames--
        if (t >= nextFlashAt) { flashFrames = 4; nextFlashAt = t + 8f + rnd.nextFloat() * 12f }
        frame++
    }
}

@Composable
private fun SceneLayer(kind: SceneKind, windKph: Float, intensity: Float, animated: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val base = micaBase()
    val state = remember(kind) { SceneState(kind.ordinal * 7919 + 17) }
    val count = when (kind) {
        SceneKind.RAIN -> (120 + 30 * intensity).toInt().coerceIn(120, 150)
        SceneKind.THUNDERSTORM -> 140
        SceneKind.DRIZZLE -> 60
        SceneKind.CLEAR_NIGHT -> 20
        SceneKind.FOG -> 5
        SceneKind.CLOUDY -> 2
        SceneKind.CLEAR_DAY -> 0
    }
    val windX = (windKph / 3.6f) * 6f * (if (kind == SceneKind.FOG) 0.15f else 1f)

    LaunchedEffect(kind, animated) {
        if (!animated) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                last = now
                if (state.seeded) state.step(dt, count, windX)
            }
        }
    }

    val skyTop: Color
    val skyBottom: Color
    val particle: Color
    when (kind) {
        SceneKind.CLEAR_DAY -> { skyTop = lerp(Color(0xFF63B3F7), cs.primaryContainer, 0.35f); skyBottom = base; particle = Color(0xFFFFE08A) }
        SceneKind.CLEAR_NIGHT -> { skyTop = lerp(Color(0xFF0B1220), cs.primary, 0.12f); skyBottom = lerp(Color(0xFF1B2540), base, 0.4f); particle = Color(0xFFF4F1E1) }
        SceneKind.CLOUDY -> { skyTop = lerp(if (dark) Color(0xFF2E3742) else Color(0xFFB9C6D6), cs.primaryContainer, 0.25f); skyBottom = base; particle = if (dark) Color(0xFF5B6675) else Color.White }
        SceneKind.FOG -> { skyTop = lerp(if (dark) Color(0xFF353B44) else Color(0xFFC9CFD8), cs.surfaceVariant, 0.4f); skyBottom = base; particle = if (dark) Color(0xFF8C95A1) else Color.White }
        SceneKind.DRIZZLE -> { skyTop = lerp(if (dark) Color(0xFF2B3440) else Color(0xFF8FA1B5), cs.primaryContainer, 0.25f); skyBottom = base; particle = lerp(Color.White, cs.primary, 0.25f) }
        SceneKind.RAIN -> { skyTop = lerp(if (dark) Color(0xFF232B36) else Color(0xFF5A6472), cs.primaryContainer, 0.2f); skyBottom = base; particle = lerp(Color.White, cs.primary, 0.3f) }
        SceneKind.THUNDERSTORM -> { skyTop = lerp(Color(0xFF1E232C), cs.primary, 0.1f); skyBottom = lerp(Color(0xFF2F3640), base, 0.5f); particle = lerp(Color.White, cs.primary, 0.3f) }
    }

    Canvas(modifier.then(Modifier)) {
        if (!state.seeded || state.size != size) {
            when (kind) {
                SceneKind.RAIN, SceneKind.THUNDERSTORM -> state.seed(size, count, size.height * 0.9f, size.height * 1.4f, 14f * density, 28f * density)
                SceneKind.DRIZZLE -> state.seed(size, count, size.height * 0.25f, size.height * 0.4f, 5f * density, 9f * density)
                else -> state.seed(size, count, 0f, 0f, 0f, 0f)
            }
        }
        @Suppress("UNUSED_EXPRESSION") state.frame
        drawRect(Brush.verticalGradient(0f to skyTop, 0.55f to lerp(skyTop, skyBottom, 0.6f), 1f to skyBottom))
        when (kind) {
            SceneKind.RAIN, SceneKind.THUNDERSTORM, SceneKind.DRIZZLE -> {
                val alpha = if (kind == SceneKind.DRIZZLE) 0.28f else 0.5f
                val slant = windX / 60f
                for (i in 0 until count) {
                    val x0 = state.x[i]; val y0 = state.y[i]
                    drawLine(particle.copy(alpha = alpha), Offset(x0, y0), Offset(x0 + slant * state.len[i], y0 + state.len[i]), strokeWidth = if (kind == SceneKind.DRIZZLE) 1f else 1.6f)
                }
                if (kind == SceneKind.THUNDERSTORM && state.flashFrames > 0) {
                    val a = if (state.flashFrames >= 3) 0.55f else 0.22f
                    drawRect(Color.White.copy(alpha = a))
                }
            }
            SceneKind.CLEAR_DAY -> drawSunRays(state.t, particle)
            SceneKind.CLEAR_NIGHT -> drawStars(state, count, particle)
            SceneKind.FOG -> drawFog(state, count, particle)
            SceneKind.CLOUDY -> drawClouds(state, particle)
        }
    }
}

/** Two rotating gradient wedges at 0.5 rpm (3°/s) around a soft sun disk, top-right. */
private fun DrawScope.drawSunRays(t: Float, sun: Color) {
    val pivot = Offset(size.width * 0.82f, size.height * 0.14f)
    val r = max(size.width, size.height) * 1.2f
    val angle = (t * 3f) % 360f
    val wedge = Brush.sweepGradient(
        0.00f to Color.Transparent, 0.06f to sun.copy(alpha = 0.16f), 0.12f to Color.Transparent,
        0.48f to Color.Transparent, 0.55f to sun.copy(alpha = 0.12f), 0.62f to Color.Transparent, 1f to Color.Transparent,
        center = pivot,
    )
    rotate(angle, pivot) { drawCircle(wedge, r, pivot) }
    rotate(-angle * 0.6f + 40f, pivot) { drawCircle(wedge, r, pivot) }
    drawCircle(Brush.radialGradient(0f to sun.copy(alpha = 0.9f), 0.35f to sun.copy(alpha = 0.35f), 1f to Color.Transparent, center = pivot, radius = 140f * density), 140f * density, pivot)
}

private fun DrawScope.drawStars(s: SceneState, count: Int, star: Color) {
    for (i in 0 until count) {
        val a = 0.35f + 0.65f * abs(sin(s.t * (0.8f + (i % 5) * 0.3f) + s.phase[i]))
        val px = (s.phase[i] / 6.28f) * size.width
        val py = ((i * 37) % 100) / 100f * size.height * 0.55f
        drawCircle(star.copy(alpha = a), radius = (1.2f + (i % 3)) * density, center = Offset(px, py))
    }
    val moon = Offset(size.width * 0.8f, size.height * 0.16f)
    drawCircle(Brush.radialGradient(0f to star.copy(alpha = 0.7f), 0.3f to star.copy(alpha = 0.25f), 1f to Color.Transparent, center = moon, radius = 90f * density), 90f * density, moon)
}

/** Drifting soft ellipses at 15 % over the sky, moving 4 dp per second. */
private fun DrawScope.drawFog(s: SceneState, count: Int, fog: Color) {
    for (i in 0 until count) {
        val drift = (s.t * 4f * density * (if (i % 2 == 0) 1f else -0.7f))
        val cx = ((s.phase[i] / 6.28f) * size.width + drift).mod(size.width + 400f * density) - 200f * density
        val cy = size.height * (0.2f + 0.16f * i)
        val rad = (160f + 40f * (i % 3)) * density
        drawCircle(Brush.radialGradient(0f to fog.copy(alpha = 0.15f), 1f to Color.Transparent, center = Offset(cx, cy), radius = rad), rad, Offset(cx, cy))
    }
}

/** Two soft blurred blobs drifting in opposite directions. */
private fun DrawScope.drawClouds(s: SceneState, cloud: Color) {
    val speed = 6f * density
    for (b in 0 until 2) {
        val dir = if (b == 0) 1f else -1f
        val cx = (size.width * (0.25f + 0.5f * b) + s.t * speed * dir).mod(size.width + 500f * density) - 250f * density
        val cy = size.height * (0.14f + 0.12f * b)
        for (k in 0 until 3) {
            val rad = (110f - 20f * k) * density
            val off = Offset(cx + (k - 1) * 90f * density, cy + (if (k == 1) -30f else 10f) * density)
            drawCircle(Brush.radialGradient(0f to cloud.copy(alpha = 0.28f), 0.7f to cloud.copy(alpha = 0.10f), 1f to Color.Transparent, center = off, radius = rad), rad, off)
        }
    }
}
