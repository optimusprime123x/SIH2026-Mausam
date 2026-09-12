package dev.mausam.home.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mausam.home.ui.theme.LocalMausamA11y

/**
 * Optional refraction on API 33+: the backdrop is bent inward in the outer 12 dp of the rounded
 * rectangle using Kyant's rounded-rect SDF shader (vendored, Apache-2.0). Text sits 16 dp inside
 * the edge so it is never distorted. Off below 33 and whenever effects are off: blur plus the
 * specular rim already reads as glass.
 */
@Composable
fun Modifier.lensEdge(cornerRadius: Dp, enabled: Boolean = true): Modifier {
    val a11y = LocalMausamA11y.current
    if (!enabled || !a11y.refraction || Build.VERSION.SDK_INT < 33) return this
    return lensEdge33(cornerRadius)
}

@RequiresApi(33)
@Composable
private fun Modifier.lensEdge33(cornerRadius: Dp): Modifier {
    val density = LocalDensity.current
    val shader = remember { RuntimeShader(RoundedRectRefractionShaderString) }
    val heightPx = with(density) { 12.dp.toPx() }
    val amountPx = with(density) { 7.dp.toPx() }
    val radiusPx = with(density) { cornerRadius.toPx() }
    return graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadii", radiusPx, radiusPx, radiusPx, radiusPx)
        shader.setFloatUniform("refractionHeight", heightPx)
        shader.setFloatUniform("refractionAmount", -amountPx)
        shader.setFloatUniform("depthEffect", 0f)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        clip = true
    }
}
