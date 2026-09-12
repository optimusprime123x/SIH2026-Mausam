package dev.mausam.home.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What lies behind a glass surface, in root coordinates: [draw] paints the whole backdrop as it
 * appears on screen, and [frame] changes whenever that picture changes.
 */
class BackdropSampler(val frame: () -> Int, val draw: DrawScope.() -> Unit)

/**
 * Glass without a RenderEffect: the backdrop is painted one more time into a small software
 * bitmap at one sixth resolution, box-blurred three times on the CPU (a close Gaussian), and
 * drawn back under the surface with the tint on top. It costs a few hundred microseconds per
 * frame for a bar and works on any renderer, which is why the collapsed hero bar and the toolbar
 * use it instead of [mausamGlass].
 */
@Composable
fun Modifier.softGlass(
    sampler: BackdropSampler,
    shape: Shape,
    tint: Color,
    tintAlpha: Float,
    downscale: Int = 6,
    blurPx: Int = 4,
    everyNthFrame: Int = 1,
): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cache = remember { SoftBlurCache() }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawWithContent {
            val frame = sampler.frame()
            val w = max(1, (size.width / downscale).roundToInt())
            val h = max(1, (size.height / downscale).roundToInt())
            val margin = blurPx * 2
            val bw = w + margin * 2
            val bh = h + margin * 2
            if (cache.bitmap == null || cache.bitmap!!.width != bw || cache.bitmap!!.height != bh || cache.frame / everyNthFrame != frame / everyNthFrame || cache.origin != origin) {
                cache.render(bw, bh, downscale, margin, origin, this, layoutDirection, sampler)
                cache.blur(blurPx)
                cache.frame = frame
                cache.origin = origin
            }
            cache.bitmap?.let { bmp ->
                drawImage(
                    bmp.asImageBitmap(),
                    srcOffset = IntOffset(margin, margin), srcSize = IntSize(w, h),
                    dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
            drawRect(tint.copy(alpha = tintAlpha))
            drawContent()
        }
}

private class SoftBlurCache {
    var bitmap: Bitmap? = null
    var frame = Int.MIN_VALUE
    var origin = Offset.Unspecified
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private val drawScope = CanvasDrawScope()

    fun render(bw: Int, bh: Int, downscale: Int, margin: Int, origin: Offset, density: androidx.compose.ui.unit.Density, ld: androidx.compose.ui.unit.LayoutDirection, sampler: BackdropSampler) {
        val bmp = bitmap?.takeIf { it.width == bw && it.height == bh } ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bitmap = it }
        val canvas = Canvas(android.graphics.Canvas(bmp))
        drawScope.draw(density, ld, canvas, Size(bw.toFloat(), bh.toFloat())) {
            scale(1f / downscale, pivot = Offset.Zero) {
                translate(-origin.x + margin * downscale, -origin.y + margin * downscale) {
                    with(sampler) { draw() }
                }
            }
        }
    }

    /** Three separable box passes over premultiplied-opaque pixels; the backdrop is opaque. */
    fun blur(radius: Int) {
        val bmp = bitmap ?: return
        val w = bmp.width; val h = bmp.height
        if (pixels.size != w * h) { pixels = IntArray(w * h); scratch = IntArray(w * h) }
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            boxPass(pixels, scratch, w, h, radius, horizontal = true)
            boxPass(scratch, pixels, w, h, radius, horizontal = false)
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun idx(i: Int) = if (horizontal) line * w + i else i * w + line
            for (i in -r..r) {
                val c = src[idx(i.coerceIn(0, len - 1))]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val out = src[idx((i - r).coerceIn(0, len - 1))]
                val inn = src[idx((i + r + 1).coerceIn(0, len - 1))]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
