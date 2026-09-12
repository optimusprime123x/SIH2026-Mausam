package dev.mausam.home.ui.theme

import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import dev.mausam.home.domain.cards.UserSettings

/** The corner ramp. Never hand-write a radius: inner = outer − padding. */
object MausamRadius {
    val Card = 28.dp
    val Inner = 20.dp
    val Chip = 12.dp
    val SheetTop = 28.dp
    val cardShape = RoundedCornerShape(Card)
    val innerShape = RoundedCornerShape(Inner)
    val chipShape = RoundedCornerShape(Chip)
    val sheetShape = RoundedCornerShape(topStart = SheetTop, topEnd = SheetTop)
    val bannerShape = RoundedCornerShape(Inner)
}

/** 4 dp grid, 2/6/10 banned. */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp
    val screenMargin = s4
    val cardGap = s3
    val cardPadding = s4
    val listBottomPadding = 112.dp
    /** Leaves the skyline visible between the hero and the first card. */
    val listTopPadding = 64.dp
}

/** Accessibility and effect switches resolved once per composition. */
@Immutable
data class MausamA11y(
    val largeText: Boolean,
    val reduceMotion: Boolean,
    val reduceTransparency: Boolean,
    val effectsEnabled: Boolean,
    val powerSave: Boolean,
) {
    val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= 31
    /** Glass blur is on only where it is cheap and wanted. */
    val glassBlur: Boolean get() = blurSupported && !reduceTransparency && !powerSave && !largeText
    /** The ambient scene runs only when nothing asks it to stop. */
    val sceneAnimated: Boolean get() = effectsEnabled && !reduceMotion && !powerSave && !largeText
    val refraction: Boolean get() = Build.VERSION.SDK_INT >= 33 && sceneAnimated && glassBlur
}

val LocalMausamA11y = compositionLocalOf { MausamA11y(false, false, false, true, false) }
val LocalIsDark = compositionLocalOf { false }
/** True when the running scheme came from the wallpaper rather than the IMD fallback. */
val LocalIsDynamic = compositionLocalOf { false }

@Composable
fun rememberA11y(settings: UserSettings?): MausamA11y {
    val context = LocalContext.current
    val fontScale = LocalConfiguration.current.fontScale
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val powerSave = remember { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }
    val large = (settings?.largeText == true) || fontScale >= 1.3f
    // Preview and screenshot renderers have no RenderEffect: draw the opaque fallback there.
    val inspecting = LocalInspectionMode.current
    return MausamA11y(
        largeText = large,
        reduceMotion = reduceMotion || inspecting,
        reduceTransparency = inspecting,
        effectsEnabled = settings?.effectsEnabled ?: true,
        powerSave = powerSave,
    )
}

/** The mica base: wallpaper-derived tint, opaque, sampled once. */
@Composable
fun micaBase(): Color {
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    return if (dark) lerp(cs.surfaceContainerLowest, cs.primaryContainer, 0.22f)
    else lerp(cs.surfaceBright, cs.primaryContainer, 0.30f)
}

fun supportsDynamicColour(): Boolean = Build.VERSION.SDK_INT >= 31

@Composable
fun MausamTheme(
    settings: UserSettings? = null,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val wantDynamic = settings?.wallpaperColours ?: true
    val dynamic = wantDynamic && supportsDynamicColour()
    val scheme: ColorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> ImdDarkScheme
        else -> ImdLightScheme
    }
    val a11y = rememberA11y(settings)
    val typography = remember { robotoFlexTypography() }
    CompositionLocalProvider(LocalMausamA11y provides a11y, LocalIsDark provides dark, LocalIsDynamic provides dynamic) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            content = content,
        )
    }
}
