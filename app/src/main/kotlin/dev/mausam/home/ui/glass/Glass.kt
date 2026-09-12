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
 * toolbar and sheet are glass; nothing inside them is. Numbers come from the design notes:
 * tint alpha floors at 0.72 so body text stays above WCAG AA over a sunlit-cloud scene.
 */
enum class GlassTier { CARD, BANNER, TOOLBAR, SHEET }

private data class Recipe(val blur: Dp, val alphaLight: Float, val alphaDark: Float, val noise: Float, val elevation: Dp, val keyAlpha: Float)

private fun recipe(tier: GlassTier) = when (tier) {
    GlassTier.CARD -> Recipe(28.dp, 0.72f, 0.74f, 0.03f, 3.dp, 0.20f)
    GlassTier.BANNER -> Recipe(24.dp, 0.92f, 0.94f, 0.02f, 6.dp, 0.20f)
    GlassTier.TOOLBAR -> Recipe(20.dp, 0.62f, 0.66f, 0.02f, 6.dp, 0.20f)
    GlassTier.SHEET -> Recipe(32.dp, 0.80f, 0.82f, 0.04f, 12.dp, 0.26f)
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
 * The 1 dp specular edge: a diagonal gradient stroke inside the clip, white at 35 % top-left
 * (45 % in dark), a faint 6 % bounce on the far edge. Rotates 20° while pressed.
 */
@Composable
fun Modifier.specularRim(shape: Shape, pressed: Boolean): Modifier {
    val angle by animateFloatAsState(
        targetValue = if (pressed) 20f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rim",
    )
    val top = if (LocalIsDark.current) 0.45f else 0.35f
    return this.drawWithContent {
        drawContent()
        val rad = Math.toRadians(315.0 + angle)
        val dx = cos(rad).toFloat() * size.width
        val dy = sin(rad).toFloat() * size.height
        val brush = Brush.linearGradient(
            0.00f to Color.White.copy(alpha = top),
            0.28f to Color.White.copy(alpha = 0.10f),
            0.55f to Color.Transparent,
            0.85f to Color.White.copy(alpha = 0.06f),
            1.00f to Color.Transparent,
            start = Offset(size.width / 2 - dx / 2, size.height / 2 - dy / 2),
            end = Offset(size.width / 2 + dx / 2, size.height / 2 + dy / 2),
        )
        drawOutline(shape.createOutline(size, layoutDirection, this), brush = brush, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * The glass surface. Blur only where it is cheap and wanted (API 31+, no power saver, no large
 * text, no reduce-transparency); otherwise an opaque-ish scrim with a hairline, which keeps the
 * same geometry so nothing else in the layout changes.
 */
@Composable
fun Modifier.mausamGlass(
    state: HazeState?,
    tier: GlassTier,
    shape: Shape,
    pressed: Boolean = false,
    tint: Color? = null,
    shadow: Boolean = true,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val a11y = LocalMausamA11y.current
    val dark = LocalIsDark.current
    val r = recipe(tier)
    val base = tint ?: if (tier == GlassTier.SHEET) cs.surfaceContainerHigh else cs.surfaceContainer
    var alpha = if (dark) r.alphaDark else r.alphaLight
    if (a11y.largeText) alpha += 0.13f
    if (pressed) alpha += 0.06f
    alpha = alpha.coerceAtMost(0.94f)
    val blur = r.blur - if (a11y.largeText) 8.dp else 0.dp
    val shadowed = if (shadow) this.fluentShadow(tier, shape) else this
    val useBlur = state != null && a11y.glassBlur
    return if (!useBlur) {
        shadowed
            .clip(shape)
            .background(base.copy(alpha = if (a11y.reduceTransparency || a11y.largeText) 1f else 0.94f), shape)
            .border(1.dp, if (a11y.reduceTransparency) cs.outline else cs.outlineVariant, shape)
    } else {
        shadowed
            .clip(shape)
            .hazeEffect(state) {
                blurRadius = blur
                noiseFactor = r.noise
                backgroundColor = base
                tints = listOf(HazeTint(base.copy(alpha = alpha)))
                fallbackTint = HazeTint(base.copy(alpha = 0.92f))
            }
            .specularRim(shape, pressed)
    }
}
