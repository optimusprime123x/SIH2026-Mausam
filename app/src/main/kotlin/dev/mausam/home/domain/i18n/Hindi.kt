package dev.mausam.home.domain.i18n

/**
 * Hindi copy, keyed by the exact English source string. Split by area so each part stays
 * reviewable; templates keep their `%s` / `%d` placeholders in the same order as the English.
 */
object Hindi {
    val table: Map<String, String> by lazy {
        HindiCards.table + HindiBriefs.table + HindiUi.table + HindiData.table + HindiCommon.table
    }
}

/** Shared vocabulary: conditions, severities, units, freshness, personas. */
object HindiCommon {
    val table: Map<String, String> = mapOf(
        "Clear" to "साफ़",
        "Partly cloudy" to "आंशिक बादल",
        "Cloudy" to "बादल",
        "Overcast" to "घने बादल",
        "Fog" to "कोहरा",
        "Haze" to "धुंध",
        "Drizzle" to "बूंदाबांदी",
        "Rain" to "बारिश",
        "Heavy rain" to "भारी बारिश",
        "Thunderstorm" to "गरज-चमक के साथ तूफ़ान",
        "Snow" to "बर्फ़बारी",
        "Dust" to "धूल",
        "Windy" to "तेज़ हवा",
        "Yellow" to "पीला",
        "Orange" to "नारंगी",
        "Red" to "लाल",
        "Be updated" to "जानकारी रखें",
        "Be prepared" to "तैयार रहें",
        "Take action" to "कार्रवाई करें",
        "as of %s" to "%s तक",
        "offline, last updated %s" to "ऑफ़लाइन, आख़िरी अपडेट %s",
        "loading…" to "लोड हो रहा है…",
        "Good" to "अच्छा",
        "Satisfactory" to "संतोषजनक",
        "Moderate" to "मध्यम",
        "Poor" to "ख़राब",
        "Very poor" to "बहुत ख़राब",
        "Severe" to "गंभीर",
        // Hero
        "H %s  L %s" to "उच्च %s  निम्न %s",
        "AQI %d" to "AQI %d",
        "Feels %s" to "महसूस %s",
        "Wind %s" to "हवा %s",
        "%d%% humidity" to "%d%% आर्द्रता",
        "couldn't refresh · %s" to "रीफ़्रेश नहीं हो सका · %s",
        "Read the weather aloud" to "मौसम सुनें",
        "Stop reading" to "सुनना रोकें",
    )
}
