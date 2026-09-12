package dev.mausam.home.domain.briefs

import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.i18n.L10n
import dev.mausam.home.domain.i18n.Lang
import dev.mausam.home.domain.model.AirQuality
import dev.mausam.home.domain.model.CurrentConditions
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.domain.model.WeatherCondition
import dev.mausam.home.domain.model.WeatherWarning

/**
 * The one-breath summary the hero reads aloud: place, condition, temperature and feels-like,
 * air quality, and the top active alert if there is one. Plain sentences with no symbols, so a
 * speech engine reads "28 degrees" rather than a degree sign.
 */
object SpokenBrief {
    fun compose(place: String?, current: CurrentConditions?, air: AirQuality?, warning: WeatherWarning?, fmt: Formatter, lang: Lang = L10n.lang): String {
        if (lang == Lang.HI) return composeHindi(place, current, air, warning, fmt)
        val parts = mutableListOf<String>()
        val where = place?.takeIf { it.isNotBlank() }?.let { "In $it, " } ?: ""
        val condition = current?.condition?.let(::spoken)
        parts += if (condition != null) "${where}it is currently $condition." else "${where}here is the weather."
        current?.let { cur ->
            val temp = degrees(fmt.temp(cur.temperatureC))
            val feels = cur.feelsLikeC?.let { degrees(fmt.temp(it)) }
            parts += if (feels != null && feels != temp) "The temperature is $temp, but it feels like $feels." else "The temperature is $temp."
        }
        air?.let { parts += "The air quality index is ${it.aqi}, ${IndianAqi.category(it.aqi).label.lowercase()}." }
        parts += warning?.let { w ->
            val what = w.headline.ifBlank { w.event }.trim().trimEnd('.')
            "There is an active ${w.severity.label.lowercase()} alert: $what."
        } ?: "There are no active weather alerts."
        return parts.joinToString(" ")
    }

    /** The same brief in Hindi. Alert headlines arrive from IMD in English and are read as they are. */
    private fun composeHindi(place: String?, current: CurrentConditions?, air: AirQuality?, warning: WeatherWarning?, fmt: Formatter): String {
        val parts = mutableListOf<String>()
        val where = place?.takeIf { it.isNotBlank() }?.let { "$it में " } ?: ""
        val condition = current?.condition?.let(::spokenHindi)
        parts += if (condition != null) "${where}अभी $condition।" else "${where}मौसम इस प्रकार है।"
        current?.let { cur ->
            val temp = fmt.temp(cur.temperatureC).trimEnd('°')
            val feels = cur.feelsLikeC?.let { fmt.temp(it).trimEnd('°') }
            parts += if (feels != null && feels != temp) "तापमान $temp डिग्री है, लेकिन $feels डिग्री जैसा महसूस हो रहा है।" else "तापमान $temp डिग्री है।"
        }
        air?.let { parts += "वायु गुणवत्ता सूचकांक ${it.aqi} है, ${L10n.tr(IndianAqi.category(it.aqi).label, Lang.HI)}।" }
        parts += warning?.let { w ->
            val what = w.headline.ifBlank { w.event }.trim().trimEnd('.')
            "${L10n.tr(w.severity.label, Lang.HI)} अलर्ट सक्रिय है: ${L10n.tr(what, Lang.HI)}।"
        } ?: "अभी कोई मौसम अलर्ट सक्रिय नहीं है।"
        return parts.joinToString(" ")
    }

    private fun spokenHindi(condition: WeatherCondition): String? = when (condition) {
        WeatherCondition.CLEAR -> "मौसम साफ़ है"
        WeatherCondition.PARTLY_CLOUDY -> "आंशिक रूप से बादल छाए हैं"
        WeatherCondition.CLOUDY -> "बादल छाए हैं"
        WeatherCondition.OVERCAST -> "घने बादल छाए हैं"
        WeatherCondition.FOG -> "कोहरा है"
        WeatherCondition.HAZE -> "धुंध है"
        WeatherCondition.DRIZZLE -> "बूंदाबांदी हो रही है"
        WeatherCondition.RAIN -> "बारिश हो रही है"
        WeatherCondition.HEAVY_RAIN -> "भारी बारिश हो रही है"
        WeatherCondition.THUNDERSTORM -> "गरज-चमक के साथ तूफ़ान है"
        WeatherCondition.SNOW -> "बर्फ़ गिर रही है"
        WeatherCondition.DUST -> "धूल भरी हवा है"
        WeatherCondition.WINDY -> "तेज़ हवा चल रही है"
        WeatherCondition.UNKNOWN -> null
    }

    /** "28°" → "28 degrees"; the unit is implied by the user's setting. */
    private fun degrees(formatted: String): String = formatted.trimEnd('°') + " degrees"

    fun spoken(condition: WeatherCondition): String? = when (condition) {
        WeatherCondition.CLEAR -> "clear"
        WeatherCondition.PARTLY_CLOUDY -> "partly cloudy"
        WeatherCondition.CLOUDY -> "cloudy"
        WeatherCondition.OVERCAST -> "overcast"
        WeatherCondition.FOG -> "foggy"
        WeatherCondition.HAZE -> "hazy"
        WeatherCondition.DRIZZLE -> "drizzling"
        WeatherCondition.RAIN -> "raining"
        WeatherCondition.HEAVY_RAIN -> "raining heavily"
        WeatherCondition.THUNDERSTORM -> "stormy"
        WeatherCondition.SNOW -> "snowing"
        WeatherCondition.DUST -> "dusty"
        WeatherCondition.WINDY -> "windy"
        WeatherCondition.UNKNOWN -> null
    }
}
