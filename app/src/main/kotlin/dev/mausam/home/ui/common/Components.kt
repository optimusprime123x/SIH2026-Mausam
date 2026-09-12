package dev.mausam.home.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DepartureBoard
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.AccentSet
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.onAccent

/** The Material symbol for an icon key: the real icon for symbol keys, the static stand-in for Meteocons. */
fun symbolFor(icon: WeatherIcon): ImageVector = when (icon) {
    WeatherIcon.CLEAR_DAY, WeatherIcon.SUN_HOT -> Icons.Rounded.WbSunny
    WeatherIcon.CLEAR_NIGHT -> Icons.Rounded.NightsStay
    WeatherIcon.PARTLY_CLOUDY_DAY, WeatherIcon.PARTLY_CLOUDY_NIGHT, WeatherIcon.CLOUDY, WeatherIcon.OVERCAST -> Icons.Rounded.Cloud
    WeatherIcon.FOG, WeatherIcon.HAZE, WeatherIcon.MIST, WeatherIcon.DUST, WeatherIcon.SMOKE -> Icons.Rounded.Visibility
    WeatherIcon.DRIZZLE, WeatherIcon.RAIN, WeatherIcon.RAINDROPS, WeatherIcon.RAINDROP -> Icons.Rounded.WaterDrop
    WeatherIcon.THUNDERSTORMS, WeatherIcon.THUNDERSTORMS_RAIN -> Icons.Rounded.Thunderstorm
    WeatherIcon.SNOW, WeatherIcon.SNOWFLAKE, WeatherIcon.HAIL -> Icons.Rounded.AcUnit
    WeatherIcon.WIND, WeatherIcon.HURRICANE -> Icons.Rounded.Air
    WeatherIcon.HUMIDITY -> Icons.Rounded.Opacity
    WeatherIcon.UV_INDEX -> Icons.Rounded.Flare
    WeatherIcon.SUNRISE, WeatherIcon.SUNSET -> Icons.Rounded.WbTwilight
    WeatherIcon.THERMOMETER, WeatherIcon.THERMOMETER_WARMER, WeatherIcon.THERMOMETER_COLDER -> Icons.Rounded.DeviceThermostat
    WeatherIcon.UMBRELLA -> Icons.Rounded.Umbrella
    WeatherIcon.COMPASS -> Icons.Rounded.Explore
    WeatherIcon.ALERT, WeatherIcon.ALERT_YELLOW, WeatherIcon.ALERT_RED -> Icons.Rounded.ReportProblem
    WeatherIcon.TIDE, WeatherIcon.WAVES, WeatherIcon.TIDES -> Icons.Rounded.Waves
    WeatherIcon.STAR -> Icons.Rounded.Star
    WeatherIcon.CALENDAR -> Icons.Rounded.Event
    WeatherIcon.NOT_AVAILABLE, WeatherIcon.PENDING -> Icons.Rounded.Schedule
    WeatherIcon.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    WeatherIcon.POLLEN -> Icons.Rounded.LocalFlorist
    WeatherIcon.SHIELD -> Icons.Rounded.GppGood
    WeatherIcon.FLIGHT -> Icons.Rounded.Flight
    WeatherIcon.SCHOOL -> Icons.Rounded.School
    WeatherIcon.AGRO -> Icons.Rounded.Agriculture
    WeatherIcon.TRAFFIC -> Icons.Rounded.Traffic
    WeatherIcon.COMMUTE -> Icons.Rounded.DepartureBoard
    WeatherIcon.COMFORT -> Icons.Rounded.Spa
    WeatherIcon.LUGGAGE -> Icons.Rounded.Luggage
    WeatherIcon.AIR -> Icons.Rounded.Air
    WeatherIcon.HEART_BROKEN -> Icons.Rounded.HeartBroken
}

/**
 * Meteocons (MIT) animated icon from assets for weather keys; a Material symbol for activity keys,
 * for previews, and while a Lottie file loads. [tint] colours the symbol only.
 */
@Composable
fun MeteoconIcon(icon: WeatherIcon, size: Dp, modifier: Modifier = Modifier, animated: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val a11y = LocalMausamA11y.current
    val useSymbol = icon.symbol || LocalInspectionMode.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (useSymbol) {
            Icon(symbolFor(icon), contentDescription = null, modifier = Modifier.size(size * 0.62f), tint = tint)
        } else {
            val composition by rememberLottieComposition(LottieCompositionSpec.Asset("meteocons/${icon.assetName}.json"))
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    isPlaying = animated && !a11y.reduceMotion,
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(symbolFor(icon), contentDescription = null, modifier = Modifier.size(size * 0.62f), tint = tint)
            }
        }
    }
}

/** A Meteocon on a soft accent disc: the coloured anchor of every card. */
@Composable
fun AccentIconDisc(icon: WeatherIcon?, accent: AccentSet, size: Dp, modifier: Modifier = Modifier, animated: Boolean = true, shape: Shape = RoundedCornerShape(size * 0.36f)) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.accent.copy(alpha = 0.55f), accent.accent.copy(alpha = 0.22f))))
            .background(accent.container.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) MeteoconIcon(icon, size = size * 0.82f, animated = animated, tint = accent.onContainer)
    }
}

/** A Material symbol on a solid accent disc, for personas and settings sections. */
@Composable
fun AccentSymbolDisc(icon: ImageVector, accent: Color, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(size * 0.36f), contentDescription: String? = null) {
    Box(
        modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.78f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = onAccent(accent), modifier = Modifier.size(size * 0.55f))
    }
}

/** Small tone/status pill: "Good", "Muggy", "Fair". */
@Composable
fun ToneChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = Space.s2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(Space.s1))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/**
 * Value text whose digits roll vertically on change: up for an increase, down for a decrease.
 * Falls back to an instant swap under reduce-motion.
 */
@Composable
fun RollingValue(text: String, numeric: Double?, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val a11y = LocalMausamA11y.current
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text to (numeric ?: 0.0),
        transitionSpec = {
            if (a11y.reduceMotion) fadeIn(effects) togetherWith fadeOut(effects)
            else {
                val up = targetState.second >= initialState.second
                (slideInVertically(spatial) { h -> if (up) h else -h } + fadeIn(effects)) togetherWith
                    (slideOutVertically(spatial) { h -> if (up) -h else h } + fadeOut(effects))
            }
        },
        label = "roll",
        modifier = modifier,
    ) { (t, _) -> Text(t, style = style, color = color, maxLines = 2) }
}

/** Section header with an accent disc: the grouping device on settings, locations and the catalogue. */
@Composable
fun SectionHeader(title: String, icon: ImageVector, accent: Color, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Space.s5, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        AccentSymbolDisc(icon, accent, 32.dp)
        Spacer(Modifier.width(Space.s3))
        Column {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A glass group that holds a few settings rows; rows inside separate with hairlines. */
@Composable
fun GlassGroup(haze: HazeState?, modifier: Modifier = Modifier, wash: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().mausamGlass(haze, GlassTier.CARD, MausamRadius.cardShape, wash = wash).padding(vertical = Space.s1), content = content)
}

@Composable
fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.s4).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

/** Card skeletons with one continuous window-wide shimmer sweep. */
@Composable
fun CardSkeletons(count: Int = 4, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    val a11y = LocalMausamA11y.current
    Column(modifier) {
        repeat(count) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MausamRadius.cardShape)
                    .then(if (a11y.reduceMotion) Modifier else Modifier.shimmer(shimmer))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                    .padding(16.dp),
            ) {
                Box(Modifier.height(16.dp).fillMaxWidth(0.4f).clip(MausamRadius.chipShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.height(32.dp).fillMaxWidth(0.55f).clip(MausamRadius.chipShape).background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
