package dev.mausam.home.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.mausam.home.R

/** Roboto Flex, one variable file. Weight and width are axes, so the hero can sit at 200. */
private fun flex(weight: Int, opsz: Float = 14f) = Font(
    resId = R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
        FontVariation.Setting("opsz", opsz),
    ),
)

val RobotoFlex: FontFamily = FontFamily(
    flex(200, 64f), flex(300), flex(400), flex(500), flex(600), flex(700),
)

/** A weight-specific family for animated weight (quantised to 25-unit steps by the caller). */
fun robotoFlexAt(weight: Int, opsz: Float = 14f): FontFamily = FontFamily(flex(weight, opsz))

private fun TextStyle.flex() = copy(fontFamily = RobotoFlex)

fun robotoFlexTypography(base: Typography = Typography()): Typography = base.copy(
    displayLarge = base.displayLarge.flex().copy(fontWeight = FontWeight.W200, fontFeatureSettings = "tnum"),
    displayMedium = base.displayMedium.flex().copy(fontFeatureSettings = "tnum"),
    displaySmall = base.displaySmall.flex().copy(fontFeatureSettings = "tnum"),
    headlineLarge = base.headlineLarge.flex().copy(fontFeatureSettings = "tnum"),
    headlineMedium = base.headlineMedium.flex().copy(fontFeatureSettings = "tnum"),
    headlineSmall = base.headlineSmall.flex().copy(fontFeatureSettings = "tnum"),
    titleLarge = base.titleLarge.flex(), titleMedium = base.titleMedium.flex(), titleSmall = base.titleSmall.flex(),
    bodyLarge = base.bodyLarge.flex(), bodyMedium = base.bodyMedium.flex(), bodySmall = base.bodySmall.flex(),
    labelLarge = base.labelLarge.flex(), labelMedium = base.labelMedium.flex(), labelSmall = base.labelSmall.flex(),
    displayLargeEmphasized = base.displayLargeEmphasized.flex(),
    displayMediumEmphasized = base.displayMediumEmphasized.flex(),
    displaySmallEmphasized = base.displaySmallEmphasized.flex(),
    headlineLargeEmphasized = base.headlineLargeEmphasized.flex(),
    headlineMediumEmphasized = base.headlineMediumEmphasized.flex(),
    headlineSmallEmphasized = base.headlineSmallEmphasized.flex(),
    titleLargeEmphasized = base.titleLargeEmphasized.flex(),
    titleMediumEmphasized = base.titleMediumEmphasized.flex(),
    titleSmallEmphasized = base.titleSmallEmphasized.flex(),
    bodyLargeEmphasized = base.bodyLargeEmphasized.flex(),
    bodyMediumEmphasized = base.bodyMediumEmphasized.flex(),
    bodySmallEmphasized = base.bodySmallEmphasized.flex(),
    labelLargeEmphasized = base.labelLargeEmphasized.flex(),
    labelMediumEmphasized = base.labelMediumEmphasized.flex(),
    labelSmallEmphasized = base.labelSmallEmphasized.flex(),
)
