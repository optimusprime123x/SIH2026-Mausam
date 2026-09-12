package dev.mausam.home.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.LocalMausamA11y
import kotlin.math.cos
import kotlin.math.sin

/**
 * Four glass tiers, one recipe each (blur, tint alpha, noise, rim, shadow). Cards, banner,
 * toolbar and sheet are glass; nothing inside them is. The backdrop behind the glass is a soft
 * aurora of theme colours, so tints can sit lower than over a raw photo while text stays legible.
 */
enum class GlassTier { CARD, BANNER, TOOLBAR, SHEET }

private data class Recipe(val blur: Dp, val alphaLight: Float, val alphaDark: Float, val noise: Float, val elevation: Dp, val keyAlpha: Float)

private fun recipe(tier: GlassTier) = when (tier) {
    GlassTier.CARD -> Recipe(30.dp, 0.60f, 0.52f, 0.04f, 4.dp, 0.22f)
    GlassTier.BANNER -> Recipe(24.dp, 0.90f, 0.90f, 0.02f, 8.dp, 0.24f)
    GlassTier.TOOLBAR -> Recipe(24.dp, 0.58f, 0.50f, 0.03f, 8.dp, 0.24f)
    GlassTier.SHEET -> Recipe(36.dp, 0.80f, 0.78f, 0.04f, 14.dp, 0.28f)
}

/** Fluent depth: soft, surface-tinted shadows, never black. Dark theme doubles the key opacity. */
@Composable
fun Modifier.fluentShadow(tier: GlassTier, shape: Shape): Modifier {
    val r = recipe(tier)
    val tint = MaterialTheme.colorScheme.surfaceTint
    val dark = LocalIsDark.current
    val key = if (dark) r.keyAlpha * 2f else r.keyAlpha
    val ambient = if (dark) 0.24f else 0.12f
    return this.shadow(
        elevation = r.elevation, shape = shape, clip = false,
        ambientColor = tint.copy(alpha = ambient), spotColor = tint.copy(alpha = key),
    )
}

/**
 * The 1 dp specular edge: a diagonal gradient stroke inside the clip, white at 16 % top-left
 * (20 % in dark), a faint 4 % bounce on the far edge. Subtle on purpose: it should read as a
 * catch-light on the glass, never as a border. Rotates 20° while pressed.
 */
@Composable
fun Modifier.specularRim(shape: Shape, pressed: Boolean): Modifier {
    val angle by animateFloatAsState(
        targetValue = if (pressed) 20f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rim",
    )
    val top = if (LocalIsDark.current) 0.20f else 0.16f
    val clear = Color.White.copy(alpha = 0f)
    return this.drawWithContent {
        drawContent()
        val rad = Math.toRadians(315.0 + angle)
        val dx = cos(rad).toFloat() * size.width
        val dy = sin(rad).toFloat() * size.height
        val brush = Brush.linearGradient(
            0.00f to Color.White.copy(alpha = top),
            0.28f to Color.White.copy(alpha = 0.06f),
            0.55f to clear,
            0.85f to Color.White.copy(alpha = 0.04f),
            1.00f to clear,
            start = Offset(size.width / 2 - dx / 2, size.height / 2 - dy / 2),
            end = Offset(size.width / 2 + dx / 2, size.height / 2 + dy / 2),
        )
        drawOutline(shape.createOutline(size, layoutDirection, this), brush = brush, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * The glass surface. Blur only where it is cheap and wanted (API 31+, no power saver, no large
 * text, no reduce-transparency); otherwise a translucent scrim with a hairline, which keeps the
 * same geometry so nothing else in the layout changes. [wash] adds an accent gradient over the
 * top-left corner so each card carries its own colour.
 */
@Composable
fun Modifier.mausamGlass(
    state: HazeState?,
    tier: GlassTier,
    shape: Shape,
    pressed: Boolean = false,
    tint: Color? = null,
    wash: Color? = null,
    shadow: Boolean = true,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val a11y = LocalMausamA11y.current
    val dark = LocalIsDark.current
    val r = recipe(tier)
    val base = tint ?: if (tier == GlassTier.SHEET) cs.surfaceContainerHigh else cs.surfaceContainer
    var alpha = if (dark) r.alphaDark else r.alphaLight
    if (a11y.largeText) alpha += 0.20f
    if (pressed) alpha += 0.06f
    alpha = alpha.coerceAtMost(0.94f)
    val blur = r.blur - if (a11y.largeText) 8.dp else 0.dp
    val shadowed = if (shadow) this.fluentShadow(tier, shape) else this
    val useBlur = state != null && a11y.glassBlur
    val washed: Modifier.() -> Modifier = {
        if (wash == null) this else drawWithContent {
            drawRect(
                Brush.linearGradient(
                    0f to wash, 0.55f to wash.copy(alpha = wash.alpha * 0.25f), 1f to wash.copy(alpha = 0f),
                    start = Offset.Zero, end = Offset(size.width, size.height * 0.9f),
                ),
            )
            drawContent()
        }
    }
    return if (!useBlur) {
        shadowed
            .clip(shape)
            .background(base.copy(alpha = if (a11y.reduceTransparency || a11y.largeText) 1f else 0.90f), shape)
            .washed()
            .border(1.dp, if (a11y.reduceTransparency) cs.outline else cs.outlineVariant.copy(alpha = 0.6f), shape)
    } else {
        shadowed
            .clip(shape)
            .hazeEffect(state) {
                blurRadius = blur
                noiseFactor = r.noise
                backgroundColor = base
                tints = listOf(HazeTint(base.copy(alpha = alpha)))
                fallbackTint = HazeTint(base.copy(alpha = 0.90f))
            }
            .washed()
            .specularRim(shape, pressed)
    }
}
