package dev.mausam.home.domain.i18n

/**
 * Copy composed in the data layer: IMD warning / nowcast code phrases (ImdCodes) and the
 * source labels and warning fallbacks the assembler writes into a WeatherBundle. Wording
 * follows IMD's own Hindi bulletins; agency names stay in Latin letters, digits stay Latin.
 * "Heavy rain" and "Fog" live in HindiCommon.
 */
object HindiData {
    val table: Map<String, String> = mapOf(
        // ImdCodes.warningCategories (district warnings, Day_N codes)
        "No warning" to "कोई चेतावनी नहीं",
        "Heavy snow" to "भारी बर्फ़बारी",
        "Thunderstorm & lightning, squall" to "गरज-चमक के साथ बिजली, आंधी",
        "Hailstorm" to "ओलावृष्टि",
        "Dust storm" to "धूल भरी आंधी",
        "Dust raising winds" to "धूल उड़ाने वाली हवाएँ",
        "Strong surface winds" to "तेज़ सतही हवाएँ",
        "Heat wave" to "लू",
        "Hot day" to "गर्म दिन",
        "Warm night" to "गर्म रात",
        "Cold wave" to "शीत लहर",
        "Cold day" to "ठंडा दिन",
        "Ground frost" to "ज़मीनी पाला",
        "Very heavy rain" to "बहुत भारी बारिश",
        "Extremely heavy rain" to "अत्यंत भारी बारिश",

        // ImdCodes.nowcastCategories (catN flags)
        "Light rain" to "हल्की बारिश",
        "Light snow" to "हल्की बर्फ़बारी",
        "Light thunderstorm" to "हल्की गरज-चमक",
        "Slight dust storm" to "हल्की धूल भरी आंधी",
        "Low lightning probability" to "बिजली गिरने की कम संभावना",
        "Moderate rain" to "मध्यम बारिश",
        "Moderate snow" to "मध्यम बर्फ़बारी",
        "Moderate thunderstorm" to "मध्यम गरज-चमक",
        "Moderate dust storm" to "मध्यम धूल भरी आंधी",
        "Moderate lightning probability" to "बिजली गिरने की मध्यम संभावना",
        "Severe thunderstorm" to "तीव्र गरज-चमक के साथ तूफ़ान",
        "Very severe thunderstorm" to "अति तीव्र गरज-चमक के साथ तूफ़ान",
        "Thunderstorm with hail" to "ओलावृष्टि के साथ गरज-चमक",
        "Severe dust storm" to "तीव्र धूल भरी आंधी",
        "High lightning probability" to "बिजली गिरने की अधिक संभावना",

        // WeatherAssembler: source labels
        "Weather data by Open-Meteo.com" to "Open-Meteo.com से मौसम डेटा",
        "IMD district warnings, IMD nowcast, NDMA SACHET" to "IMD ज़िला चेतावनियाँ, IMD नाउकास्ट, NDMA SACHET",
        "Open-Meteo marine model" to "Open-Meteo समुद्री मॉडल",
        "Open-Meteo air-quality model (CAMS)" to "Open-Meteo वायु गुणवत्ता मॉडल (CAMS)",
        "IMD station 24 h rainfall" to "IMD स्टेशन 24 घंटे की वर्षा",
        "IMD %s observation" to "IMD %s प्रेक्षण",
        "IMD %s METAR" to "IMD %s का METAR",
        "station" to "स्टेशन",
        "airport" to "हवाई अड्डा",
        "IMD district warning" to "IMD ज़िला चेतावनी",
        "IMD nowcast" to "IMD नाउकास्ट",

        // WeatherAssembler: warning fallbacks and headlines
        "Weather warning" to "मौसम चेतावनी",
        "Nowcast" to "नाउकास्ट",
        "Alert" to "अलर्ट",
        "%s on %s" to "%s, %s को",
    )
}
