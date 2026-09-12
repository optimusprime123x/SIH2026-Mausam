package dev.mausam.home.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.theme.ImdTiers
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space

/**
 * IMD colour, four channels (colour, icon, word, motion). Near-opaque glass at tier 2; the only
 * surface allowed a saturated container. Orange and red shake the icon once.
 */
@Composable
fun AlertBanner(warning: WeatherWarning, haze: HazeState, onClick: () -> Unit) {
    val tier = ImdTiers.of(warning.severity, LocalIsDark.current)
    val a11y = LocalMausamA11y.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(warning.id) {
        if (!a11y.reduceMotion && warning.severity.pushes) {
            shake.animateTo(0f, keyframes { durationMillis = 500; -12f at 80; 10f at 160; -6f at 240; 3f at 320; 0f at 500 })
        }
    }
    val icon = when (warning.severity) {
        WarningSeverity.YELLOW -> WeatherIcon.ALERT_YELLOW
        WarningSeverity.ORANGE -> WeatherIcon.ALERT
        WarningSeverity.RED -> WeatherIcon.ALERT_RED
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenMargin, vertical = Space.s2)
            .mausamGlass(haze, GlassTier.BANNER, MausamRadius.bannerShape, tint = tier.container)
            .clickable(onClick = onClick)
            .padding(Space.s3)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeteoconIcon(icon, size = 40.dp, modifier = Modifier.graphicsLayer { rotationZ = shake.value })
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(
                "${warning.severity.label} · ${warning.severity.advice}",
                style = MaterialTheme.typography.titleMediumEmphasized, color = tier.onContainer,
            )
            Text(warning.headline, style = MaterialTheme.typography.bodyMedium, color = tier.onContainer, maxLines = 2)
        }
    }
}
