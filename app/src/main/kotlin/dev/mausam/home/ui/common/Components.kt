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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius

/** Meteocons (MIT) animated icon from assets; Material Symbols fallback while it loads or if missing. */
@Composable
fun MeteoconIcon(icon: WeatherIcon, size: Dp, modifier: Modifier = Modifier, animated: Boolean = true) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("meteocons/${icon.assetName}.json"))
    val a11y = LocalMausamA11y.current
    Box(modifier.size(size)) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = animated && !a11y.reduceMotion,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(size * 0.7f).padding(size * 0.15f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Value text whose digits roll vertically on change: up for an increase, down for a decrease.
 * Falls back to an instant swap under reduce-motion.
 */
@Composable
fun RollingValue(text: String, numeric: Double?, style: TextStyle, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
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
