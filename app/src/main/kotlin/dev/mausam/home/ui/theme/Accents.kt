package dev.mausam.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.mausam.home.domain.personas.Persona

/**
 * Fluent-inspired accent palette. Every card and persona owns one hue so the home page reads as a
 * colourful, organised set rather than a wall of grey. Accents are nudged 12 % toward the theme
 * primary so a wallpaper-derived scheme still ties the page together.
 */
object Fluent {
    val SkyBlue = Color(0xFF4CC2FF)
    val DeepBlue = Color(0xFF0078D4)
    val Indigo = Color(0xFF6B7BFF)
    val Coral = Color(0xFFFF7A59)
    val Orange = Color(0xFFF7630C)
    val Amber = Color(0xFFFFC83D)
    val Teal = Color(0xFF29D3C3)
    val Violet = Color(0xFFA78BFA)
    val Mint = Color(0xFF7CE0A9)
    val Green = Color(0xFF2FB36A)
    val Rose = Color(0xFFFF6B9A)
    val Red = Color(0xFFE74856)
    val Slate = Color(0xFF8FA3BF)
    val Magenta = Color(0xFFE3008C)

    fun forPersona(p: Persona): Color = when (p) {
        Persona.HEALTH -> Magenta
        Persona.FITNESS -> Orange
        Persona.BEACH -> Teal
        Persona.TRAVEL -> DeepBlue
        Persona.PARENTS -> Amber
        Persona.AGRICULTURE -> Green
        Persona.COMMUTERS -> Violet
        Persona.EVENTS -> Rose
        Persona.GENERAL -> SkyBlue
    }

    /** Accent by card id; ids are stable strings owned by the domain registry. */
    fun forCard(id: String): Color = when (id) {
        "health.aqi" -> Teal
        "health.humidity" -> SkyBlue
        "health.uv" -> Amber
        "health.pollen" -> Mint
        "fitness.sun" -> Coral
        "fitness.run" -> Orange
        "fitness.wind" -> Teal
        "fitness.heat" -> Rose
        "beach.sea" -> DeepBlue
        "beach.tides" -> Indigo
        "travel.destinations" -> Violet
        "travel.severe" -> Red
        "travel.packing" -> Violet
        "parents.school" -> Amber
        "parents.rain3h" -> SkyBlue
        "parents.severe" -> Red
        "agri.rainfall" -> DeepBlue
        "agri.outlook" -> Mint
        "agri.frost" -> SkyBlue
        "agri.advisory" -> Green
        "commute.visibility" -> Slate
        "commute.storm" -> Red
        "commute.leave" -> Orange
        "commute.traffic" -> Amber
        "events.outlook" -> Indigo
        "events.comfort" -> Mint
        "events.bestday" -> Coral
        "general.hourly" -> SkyBlue
        "general.week" -> Indigo
        "general.warnings" -> Red
        else -> SkyBlue
    }
}

/** An accent harmonised with the running scheme plus the on-colour and container derived from it. */
data class AccentSet(val accent: Color, val container: Color, val onContainer: Color, val wash: Color)

@Composable
fun accentSet(base: Color): AccentSet {
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val accent = lerp(base, cs.primary, 0.12f)
    val container = if (dark) lerp(accent, cs.surfaceContainerHigh, 0.62f) else lerp(accent, Color.White, 0.72f)
    val onContainer = if (dark) lerp(accent, Color.White, 0.55f) else lerp(accent, Color.Black, 0.55f)
    val wash = accent.copy(alpha = if (dark) 0.22f else 0.18f)
    return AccentSet(accent, container, onContainer, wash)
}

/** Readable ink over an arbitrary accent fill. */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.45f) Color(0xFF14181F) else Color.White
