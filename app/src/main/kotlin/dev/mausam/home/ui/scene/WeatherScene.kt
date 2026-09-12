package dev.mausam.home.ui.scene

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mausam.home.R
import dev.mausam.home.domain.model.SceneKind
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.micaBase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Everything the scene needs from the weather, so the UI layer never touches the bundle here. */
data class SceneSpec(
    val kind: SceneKind = SceneKind.CLEAR_DAY,
    /** 0 at sunrise, 1 at sunset, outside that range at night; NaN when unknown. */
    val sunProgress: Float = Float.NaN,
    val windKph: Float = 0f,
    val intensity: Float = 0f,
)

/** Three gradient stops for the sky, top to horizon. */
private data class Sky(val top: Color, val mid: Color, val horizon: Color, val cloudTint: Color, val landTint: Color)

private fun sky(kind: SceneKind, dark: Boolean): Sky = when (kind) {
    SceneKind.CLEAR_DAY -> Sky(Color(0xFF2B7FE0), Color(0xFF6FB7FF), Color(0xFFBFE3FF), Color.White, Color(0xFF5FA6E8))
    SceneKind.CLEAR_NIGHT -> Sky(Color(0xFF070B22), Color(0xFF15204A), Color(0xFF2A3A6C), Color(0xFF7D88AD), Color(0xFF1B2A55))
    SceneKind.CLOUDY -> if (dark) Sky(Color(0xFF1B2338), Color(0xFF2E3A55), Color(0xFF45536E), Color(0xFF8C97B0), Color(0xFF2C3852))
        else Sky(Color(0xFF5D7EA6), Color(0xFF8FA9C8), Color(0xFFC2D2E3), Color(0xFFF2F6FA), Color(0xFF7F99BC))
    SceneKind.FOG -> if (dark) Sky(Color(0xFF2A3140), Color(0xFF414A5B), Color(0xFF5A6475), Color(0xFF8B94A5), Color(0xFF3E4756))
        else Sky(Color(0xFF8E9BAE), Color(0xFFB5BFCC), Color(0xFFD5DBE3), Color(0xFFEEF1F5), Color(0xFF9AA6B8))
    SceneKind.DRIZZLE -> if (dark) Sky(Color(0xFF1C2534), Color(0xFF2C3A4E), Color(0xFF3E4E63), Color(0xFF7E8CA3), Color(0xFF2A384C))
        else Sky(Color(0xFF55708F), Color(0xFF7F97B3), Color(0xFFAABBCB), Color(0xFFDFE6EE), Color(0xFF6F88A6))
    SceneKind.RAIN -> if (dark) Sky(Color(0xFF141C2B), Color(0xFF232F42), Color(0xFF33425A), Color(0xFF6C7A93), Color(0xFF1F2B3E))
        else Sky(Color(0xFF3A5273), Color(0xFF5E7594), Color(0xFF8A9BB1), Color(0xFFC5CFDB), Color(0xFF55698A))
    SceneKind.THUNDERSTORM -> if (dark) Sky(Color(0xFF12162A), Color(0xFF232A44), Color(0xFF3A415A), Color(0xFF5E6580), Color(0xFF1B2036))
        else Sky(Color(0xFF2B3550), Color(0xFF46516E), Color(0xFF6B7590), Color(0xFF9EA6BC), Color(0xFF3F4A66))
}

private val Dawn = Sky(Color(0xFF3B3F8F), Color(0xFFE4784F), Color(0xFFFFC48A), Color(0xFFFFE0C2), Color(0xFF6E4E7A))

/** Warmth near the horizon: 1 within a few minutes of sunrise or sunset, 0 in the middle of the day. */
private fun warmth(p: Float): Float {
    if (p.isNaN()) return 0f
    val d = minOf(abs(p), abs(p - 1f))
    return (1f - d / 0.14f).coerceIn(0f, 1f)
}

private fun Sky.blend(other: Sky, f: Float) = Sky(
    lerp(top, other.top, f), lerp(mid, other.mid, f), lerp(horizon, other.horizon, f), lerp(cloudTint, other.cloudTint, f), lerp(landTint, other.landTint, f),
)

/** Mutable simulation state shared by every scene kind; one frame int is the only observable. */
private class SceneState(seed: Int) {
    val rnd = Random(seed)
    var frame by mutableIntStateOf(0)
    var t = 0f
    var size = Size.Zero
    var seeded = false
    val n = 160
    val x = FloatArray(n)
    val y = FloatArray(n)
    val v = FloatArray(n)
    val len = FloatArray(n)
    val phase = FloatArray(n) { rnd.nextFloat() * 6.28f }
    var nextFlashAt = 6f + rnd.nextFloat() * 10f
    var flashFrames = 0
    var boltX = 0.5f

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
        if (t >= nextFlashAt) { flashFrames = 9; boltX = 0.15f + rnd.nextFloat() * 0.7f; nextFlashAt = t + 6f + rnd.nextFloat() * 10f }
        frame++
    }
}

/**
 * The hero illustration: a time-of-day sky, a vector sun or moon, drifting vector clouds, hills
 * and an Indian skyline in front, then rain, stars, fog or lightning on top. Parallax comes from
 * [collapse] (0 expanded, 1 collapsed): the sky barely moves, the skyline moves most.
 * Crossfades over 600 ms when the condition changes.
 */
@Composable
fun WeatherScene(spec: SceneSpec, animated: Boolean, collapse: Float, modifier: Modifier = Modifier) {
    Crossfade(targetState = spec.kind, animationSpec = tween(600), label = "scene", modifier = modifier) { k ->
        SceneLayer(spec.copy(kind = k), animated, collapse, Modifier.fillMaxSize().clearAndSetSemantics { })
    }
}

@Composable
private fun SceneLayer(spec: SceneSpec, animated: Boolean, collapse: Float, modifier: Modifier) {
    val dark = LocalIsDark.current
    val base = micaBase()
    val kind = spec.kind
    val state = remember(kind) { SceneState(kind.ordinal * 7919 + 17) }
    val count = when (kind) {
        SceneKind.RAIN -> (120 + 30 * spec.intensity).toInt().coerceIn(120, 150)
        SceneKind.THUNDERSTORM -> 140
        SceneKind.DRIZZLE -> 60
        SceneKind.CLEAR_NIGHT -> 40
        else -> 0
    }
    val windX = (spec.windKph / 3.6f) * 6f
    val palette = remember(kind, dark, spec.sunProgress) {
        val s = sky(kind, dark)
        val w = warmth(spec.sunProgress) * (if (kind == SceneKind.CLEAR_DAY || kind == SceneKind.CLEAR_NIGHT) 1f else 0.5f)
        if (w > 0f) s.blend(Dawn, w) else s
    }
    val isNight = kind == SceneKind.CLEAR_NIGHT || (dark && kind != SceneKind.CLEAR_DAY)

    val cloud1 = painterResource(R.drawable.scene_cloud_1)
    val cloud2 = painterResource(R.drawable.scene_cloud_2)
    val cloud3 = painterResource(R.drawable.scene_cloud_3)
    val hillsFar = painterResource(R.drawable.scene_hills_far)
    val hillsNear = painterResource(R.drawable.scene_hills_near)
    val skyline = painterResource(R.drawable.scene_skyline)
    val bolt = painterResource(R.drawable.scene_bolt)
    val storm = painterResource(R.drawable.scene_cloud_storm)
    val birds = painterResource(R.drawable.scene_birds)
    val sun = painterResource(R.drawable.scene_sun)
    val moon = painterResource(R.drawable.scene_moon)

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

    Canvas(modifier) {
        if (!state.seeded || state.size != size) {
            when (kind) {
                SceneKind.RAIN, SceneKind.THUNDERSTORM -> state.seed(size, count, size.height * 1.2f, size.height * 1.8f, 16f * density, 30f * density)
                SceneKind.DRIZZLE -> state.seed(size, count, size.height * 0.3f, size.height * 0.5f, 5f * density, 9f * density)
                else -> state.seed(size, count, 0f, 0f, 0f, 0f)
            }
        }
        @Suppress("UNUSED_EXPRESSION") state.frame
        val t = state.t
        val w = size.width
        val h = size.height
        val d = density

        // Sky, fading into the mica base at the bottom so the list continues it seamlessly.
        drawRect(Brush.verticalGradient(0f to palette.top, 0.45f to palette.mid, 0.78f to palette.horizon, 1f to base))

        // Sun or moon on an arc from left to right through the day.
        val p = if (spec.sunProgress.isNaN()) (if (isNight) -1f else 0.5f) else spec.sunProgress
        if (kind != SceneKind.THUNDERSTORM && kind != SceneKind.RAIN) {
            if (p in 0f..1f && kind != SceneKind.CLEAR_NIGHT) {
                val sunSize = 150f * d
                val cx = w * (0.14f + 0.72f * p)
                val cy = h * (0.60f - 0.42f * sin(p * PI.toFloat())) - collapse * h * 0.15f
                val glow = Color(0xFFFFD866)
                val breath = 0.50f + 0.10f * sin(t * 0.8f)
                drawCircle(Brush.radialGradient(0f to glow.copy(alpha = breath), 0.5f to glow.copy(alpha = breath * 0.35f), 1f to glow.copy(alpha = 0f), center = Offset(cx, cy), radius = sunSize * 1.6f), sunSize * 1.6f, Offset(cx, cy))
                val dim = if (kind == SceneKind.CLEAR_DAY) 1f else 0.55f
                rotate(t * 2.5f, Offset(cx, cy)) {
                    translate(cx - sunSize / 2, cy - sunSize / 2) { with(sun) { draw(Size(sunSize, sunSize), alpha = dim) } }
                }
            } else if (kind == SceneKind.CLEAR_NIGHT || p !in 0f..1f) {
                val moonSize = 92f * d
                val cx = w * 0.78f
                val cy = h * 0.20f + sin(t * 0.3f) * 3f * d - collapse * h * 0.15f
                val glow = Color(0xFFF4F1E1)
                drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.35f), 1f to glow.copy(alpha = 0f), center = Offset(cx, cy), radius = moonSize * 1.5f), moonSize * 1.5f, Offset(cx, cy))
                translate(cx - moonSize / 2, cy - moonSize / 2) { with(moon) { draw(Size(moonSize, moonSize)) } }
            }
        }
        if (kind == SceneKind.CLEAR_NIGHT) drawStars(state, count, Color(0xFFF4F1E1))

        // Clouds: count and tint by condition; drift with the wind; far layers move less.
        val cloudTint = palette.cloudTint
        val cloudAlpha = when (kind) {
            SceneKind.CLEAR_DAY, SceneKind.CLEAR_NIGHT -> 0.75f
            SceneKind.FOG -> 0.55f
            else -> 0.96f
        }
        fun cloud(painter: Painter, baseX: Float, y: Float, widthFrac: Float, speed: Float, alpha: Float, par: Float) {
            val cw = w * widthFrac
            val ch = cw * (painter.intrinsicSize.height / painter.intrinsicSize.width)
            val span = w + cw
            val cx = ((baseX * w + t * speed * d * (if (windX >= 0) 1f else -1f)) % span + span) % span - cw
            val cy = y * h - collapse * h * par
            translate(cx, cy) { with(painter) { draw(Size(cw, ch), alpha = alpha, colorFilter = ColorFilter.tint(cloudTint, BlendMode.Modulate)) } }
        }
        val drift = 4f + windX / (6f * d) * 2f
        when (kind) {
            SceneKind.CLEAR_DAY -> { cloud(cloud3, 0.55f, 0.16f, 0.42f, drift * 0.6f, cloudAlpha * 0.8f, 0.10f); cloud(cloud2, -0.05f, 0.30f, 0.30f, drift, cloudAlpha, 0.16f) }
            SceneKind.CLEAR_NIGHT -> cloud(cloud3, 0.20f, 0.30f, 0.38f, drift * 0.5f, 0.35f, 0.12f)
            SceneKind.CLOUDY -> {
                cloud(cloud3, 0.60f, 0.12f, 0.50f, drift * 0.5f, cloudAlpha * 0.8f, 0.08f)
                cloud(cloud1, -0.10f, 0.16f, 0.62f, drift * 0.9f, cloudAlpha, 0.14f)
                cloud(cloud2, 0.50f, 0.34f, 0.44f, drift * 1.3f, cloudAlpha, 0.20f)
            }
            SceneKind.FOG -> { cloud(cloud1, 0.10f, 0.42f, 0.70f, drift * 0.4f, cloudAlpha, 0.16f); cloud(cloud3, -0.20f, 0.30f, 0.60f, drift * 0.6f, cloudAlpha, 0.12f) }
            SceneKind.DRIZZLE -> {
                cloud(cloud1, -0.15f, 0.06f, 0.70f, drift * 0.8f, cloudAlpha, 0.10f)
                cloud(cloud2, 0.45f, 0.14f, 0.50f, drift * 1.1f, cloudAlpha, 0.14f)
                cloud(cloud3, 0.15f, 0.30f, 0.48f, drift * 1.4f, cloudAlpha * 0.8f, 0.20f)
            }
            SceneKind.RAIN, SceneKind.THUNDERSTORM -> {
                // Storm clouds carry their own greys: alpha only, no tint.
                fun stormCloud(baseX: Float, y: Float, widthFrac: Float, speed: Float, par: Float) {
                    val cw = w * widthFrac
                    val ch = cw * (storm.intrinsicSize.height / storm.intrinsicSize.width)
                    val span = w + cw
                    val cx = ((baseX * w + t * speed * d) % span + span) % span - cw
                    translate(cx, y * h - collapse * h * par) { with(storm) { draw(Size(cw, ch), alpha = if (dark) 0.85f else 0.95f) } }
                }
                stormCloud(-0.20f, 0.02f, 0.78f, drift * 0.8f, 0.10f)
                stormCloud(0.40f, 0.08f, 0.70f, drift * 1.1f, 0.14f)
                cloud(cloud2, 0.10f, 0.26f, 0.44f, drift * 1.4f, 0.55f, 0.20f)
            }
        }

        // Birds glide across on calm days.
        if ((kind == SceneKind.CLEAR_DAY || kind == SceneKind.CLOUDY) && !dark) {
            val bw = w * 0.22f
            val bh = bw * (birds.intrinsicSize.height / birds.intrinsicSize.width)
            val span = w + bw
            val bx = ((w * 0.7f + t * 9f * d) % span + span) % span - bw
            val by = h * 0.30f + sin(t * 1.3f) * 4f * d - collapse * h * 0.12f
            translate(bx, by) { with(birds) { draw(Size(bw, bh), alpha = 0.55f, colorFilter = ColorFilter.tint(lerp(palette.landTint, base, 0.6f))) } }
        }

        // Land: far hills, near hills, skyline, each darker and moving more than the last.
        val land = palette.landTint
        fun layer(painter: Painter, tint: Color, alpha: Float, par: Float, heightFrac: Float) {
            val lw = w
            val lh = lw * (painter.intrinsicSize.height / painter.intrinsicSize.width) * heightFrac
            val ly = h - lh + collapse * h * par
            translate(0f, ly) { with(painter) { draw(Size(lw, lh), alpha = alpha, colorFilter = ColorFilter.tint(tint)) } }
        }
        layer(hillsFar, lerp(land, base, 0.35f), 0.9f, 0.08f, 1.15f)
        layer(hillsNear, lerp(land, base, 0.55f), 0.95f, 0.14f, 1.0f)
        layer(skyline, lerp(land, base, 0.72f), 1f, 0.22f, 0.95f)

        // Legibility scrim for the hero text, continuous into the base so there is no hard edge.
        val scrim = Color(0xFF0B1220)
        drawRect(Brush.verticalGradient(0.22f to scrim.copy(alpha = 0f), 0.62f to scrim.copy(alpha = 0.42f), 1f to scrim.copy(alpha = 0.42f)))

        // Weather on top.
        when (kind) {
            SceneKind.RAIN, SceneKind.THUNDERSTORM, SceneKind.DRIZZLE -> {
                val alpha = if (kind == SceneKind.DRIZZLE) 0.35f else 0.55f
                val slant = windX / 60f
                val drop = lerp(Color.White, palette.horizon, 0.35f)
                for (i in 0 until count) {
                    val x0 = state.x[i]; val y0 = state.y[i]
                    drawLine(drop.copy(alpha = alpha), Offset(x0, y0), Offset(x0 + slant * state.len[i], y0 + state.len[i]), strokeWidth = if (kind == SceneKind.DRIZZLE) 1.2f * d else 1.8f * d)
                }
                if (kind == SceneKind.THUNDERSTORM && state.flashFrames > 0) {
                    val f = state.flashFrames
                    val a = if (f >= 7) 0.45f else if (f >= 4) 0.18f else 0.08f
                    drawRect(Color.White.copy(alpha = a))
                    val bw = 46f * d; val bh = bw * (bolt.intrinsicSize.height / bolt.intrinsicSize.width)
                    translate(w * state.boltX, h * 0.18f) { with(bolt) { draw(Size(bw, bh), alpha = if (f >= 3) 0.95f else 0.5f, colorFilter = ColorFilter.tint(Color(0xFFFFF1A8))) } }
                }
            }
            SceneKind.FOG -> drawFog(state, palette.horizon)
            else -> Unit
        }
    }
}

private fun DrawScope.drawStars(s: SceneState, count: Int, star: Color) {
    for (i in 0 until count) {
        val a = 0.3f + 0.7f * abs(sin(s.t * (0.6f + (i % 5) * 0.3f) + s.phase[i]))
        val px = (s.phase[i] / 6.28f) * size.width
        val py = ((i * 37) % 100) / 100f * size.height * 0.5f
        drawCircle(star.copy(alpha = a), radius = (0.9f + (i % 3) * 0.6f) * density, center = Offset(px, py))
    }
}

/** Three soft horizontal bands drifting in alternate directions over the land. */
private fun DrawScope.drawFog(s: SceneState, fog: Color) {
    for (i in 0 until 3) {
        val dir = if (i % 2 == 0) 1f else -1f
        val bw = size.width * 1.4f
        val off = ((s.t * 5f * density * dir) % (size.width * 0.4f) + size.width * 0.4f) % (size.width * 0.4f) - size.width * 0.2f
        val bh = (36f + 10f * i) * density
        val by = size.height * (0.62f + 0.11f * i)
        drawRect(
            Brush.horizontalGradient(0f to fog.copy(alpha = 0f), 0.3f to fog.copy(alpha = 0.32f), 0.7f to fog.copy(alpha = 0.32f), 1f to fog.copy(alpha = 0f), startX = off, endX = off + bw),
            topLeft = Offset(off, by), size = Size(bw, bh),
        )
    }
}

/** Sun progress from sunrise/sunset minutes; used by the view model so the scene stays pure. */
fun sunProgress(nowMinutes: Int, sunriseMinutes: Int?, sunsetMinutes: Int?): Float {
    if (sunriseMinutes == null || sunsetMinutes == null || sunsetMinutes <= sunriseMinutes) return Float.NaN
    return (nowMinutes - sunriseMinutes).toFloat() / max(1, sunsetMinutes - sunriseMinutes)
}

/** Top-to-horizon sky colours for a condition, for mini heroes elsewhere in the app. */
fun sceneGradient(kind: SceneKind, dark: Boolean): List<Color> = sky(kind, dark).let { listOf(it.top, it.mid, it.horizon) }
