package dev.mausam.home.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.mausam.home.domain.model.WarningSeverity

/** IMD blue seed and a hand-derived tonal scheme, used when wallpaper colour is unavailable. */
val ImdBlue = Color(0xFF0B4F9E)

val ImdLightScheme: ColorScheme = lightColorScheme(
    primary = ImdBlue, onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71), onSecondary = Color.White, secondaryContainer = Color(0xFFD9E3F8), onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6F5675), onTertiary = Color.White, tertiaryContainer = Color(0xFFF8D8FF), onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF0F0F7), inversePrimary = Color(0xFFA8C8FF),
    surfaceTint = ImdBlue, scrim = Color.Black,
    surfaceBright = Color(0xFFF9F9FF), surfaceDim = Color(0xFFD9D9E0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3FA), surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE2E2E9),
)

val ImdDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF003062), primaryContainer = Color(0xFF0B4F9E), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF273141), secondaryContainer = Color(0xFF3E4758), onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDCBCE1), onTertiary = Color(0xFF3E2845), tertiaryContainer = Color(0xFF563E5C), onTertiaryContainer = Color(0xFFF8D8FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474F),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2F3036), inversePrimary = ImdBlue,
    surfaceTint = Color(0xFFA8C8FF), scrim = Color.Black,
    surfaceBright = Color(0xFF37393E), surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF1A1B20), surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerHighest = Color(0xFF33353A),
)

/**
 * IMD's regulatory warning tiers. Hard-coded, never derived from dynamic colour, and paired as
 * container / on-container so text passes WCAG AAA on the banner (see design notes).
 */
data class TierColors(val container: Color, val onContainer: Color, val accent: Color)

object ImdTiers {
    private val light = mapOf(
        WarningSeverity.YELLOW to TierColors(Color(0xFFFFE9A6), Color(0xFF3D2E00), Color(0xFFFFD54F)),
        WarningSeverity.ORANGE to TierColors(Color(0xFFFFDCC2), Color(0xFF4A2000), Color(0xFFE06A00)),
        WarningSeverity.RED to TierColors(Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFFA11616)),
    )
    private val dark = mapOf(
        WarningSeverity.YELLOW to TierColors(Color(0xFF3B2D00), Color(0xFFFFDF9E), Color(0xFFFFD54F)),
        WarningSeverity.ORANGE to TierColors(Color(0xFF4A2300), Color(0xFFFFCFA8), Color(0xFFFFB77A)),
        WarningSeverity.RED to TierColors(Color(0xFF690005), Color(0xFFFFB4AB), Color(0xFFFFB4AB)),
    )

    fun of(severity: WarningSeverity, isDark: Boolean): TierColors = (if (isDark) dark else light).getValue(severity)

    /** Solid fills that still pass with white text: used by the widget strip and notifications. */
    fun solid(severity: WarningSeverity): Color = when (severity) {
        WarningSeverity.YELLOW -> Color(0xFFF2B705)
        WarningSeverity.ORANGE -> Color(0xFFE06A00)
        WarningSeverity.RED -> Color(0xFFA11616)
    }
}
