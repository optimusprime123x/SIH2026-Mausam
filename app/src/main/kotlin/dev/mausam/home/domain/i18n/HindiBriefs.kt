package dev.mausam.home.domain.i18n

/**
 * Hindi for the morning / evening notification briefs, the persona picker, AQI health advice,
 * weekday names and the Formatter's time words. Keyed by the exact English source string;
 * `%s` / `%d` / `%%` stay in the English order. Digits stay Latin, units and agency names too.
 */
object HindiBriefs {
    val table: Map<String, String> = mapOf(
        // Formatter: IMD-style day halves, "6 am" → "पूर्वाह्न 6 बजे"

        // Weekdays (BriefComposer.dayName)
        "Monday" to "सोमवार",
        "Tuesday" to "मंगलवार",
        "Wednesday" to "बुधवार",
        "Thursday" to "गुरुवार",
        "Friday" to "शुक्रवार",
        "Saturday" to "शनिवार",
        "Sunday" to "रविवार",

        // Persona titles (tile-sized) and taglines
        "Health-conscious" to "स्वास्थ्य-सजग",
        "Air quality, humidity and UV" to "वायु गुणवत्ता, आर्द्रता और UV",
        "Outdoor fitness" to "आउटडोर फ़िटनेस",
        "Best hours to run or ride" to "दौड़ने या साइकिल के बेहतरीन घंटे",
        "Beachgoers & surfers" to "बीच और सर्फ़िंग",
        "Sea state, waves and tides" to "समुद्र की स्थिति, लहरें और ज्वार",
        "Travellers" to "यात्री",
        "Weather where you are going" to "जहाँ जा रहे हैं वहाँ का मौसम",
        "Parents & families" to "अभिभावक व परिवार",
        "School run and afternoon rain" to "स्कूल आना-जाना और दोपहर की बारिश",
        "Agriculture & gardeners" to "खेती व बागवानी",
        "Rainfall, frost and advisories" to "वर्षा, पाला और कृषि सलाह",
        "Commuters" to "दैनिक यात्री",
        "Fog, storms and leave-early nudges" to "कोहरा, तूफ़ान और जल्दी निकलने की सलाह",
        "Event planners" to "इवेंट प्लानर",
        "Comfort and the best day this week" to "आराम और इस हफ़्ते का सबसे अच्छा दिन",
        "General" to "सामान्य",
        "Hourly, 7-day and warnings" to "घंटेवार, 7-दिन और चेतावनियाँ",

        // CPCB AQI health advice (category labels live in HindiCommon)

        // Shared brief fragments
        "Forecast not cached yet." to "पूर्वानुमान अभी सहेजा नहीं गया है।",
        "Tomorrow in %s" to "कल %s में",
        "AQI %d (%s)" to "AQI %d (%s)",
        "UV peaks at %d around %s." to "पराबैंगनी सूचकांक अधिकतम %d रहेगा, लगभग %s पर।",

        // Fitness
        "Best run window %s" to "दौड़ने का सबसे अच्छा समय %s",
        "%s at the start" to "शुरुआत में %s",
        "%s – %s, %s. Lay out kit tonight; early hours will be the coolest." to "%s – %s, %s। किट आज रात ही तैयार रखें; सुबह के शुरुआती घंटे सबसे ठंडे रहेंगे।",

        // Parents
        "Tomorrow's school run" to "कल का स्कूल आना-जाना",
        "%s with a %d%% chance of rain. High %s." to "%s, बारिश की %d%% संभावना। अधिकतम %s।",
        "Rain likely %s" to "%s बारिश की संभावना",
        "%d%% chance, %s. Umbrellas and covered shoes for the school run." to "%d%% संभावना, %s। स्कूल जाते समय छाता और बंद जूते रखें।",
        "Rain possible %s" to "%s बारिश हो सकती है",
        "%d%% chance. A light rain jacket should do." to "%d%% संभावना। हल्का रेनकोट काफ़ी होगा।",
        "Dry school run" to "स्कूल के समय बारिश नहीं",
        "%s and %s now" to "अभी %s और %s",
        "No rain expected %s." to "%s बारिश की संभावना नहीं।",

        // Agriculture
        "Skip watering." to "आज सिंचाई न करें।",
        "Water lightly." to "हल्की सिंचाई करें।",
        "Water as usual." to "सामान्य रूप से सिंचाई करें।",
        "%s rain expected this week" to "इस हफ़्ते %s बारिश की संभावना",
        "Frost risk %s night (%s), cover seedlings." to "%s रात पाले का ख़तरा (%s), पौध ढकें।",
        "Advisory: %s." to "कृषि सलाह: %s।",

        // Travel
        "%s alert in %s" to "%s अलर्ट: %s",
        "%s. %s" to "%s। %s",
        "%s tomorrow" to "कल %s में",
        "%s today" to "आज %s में",
        "%s – %s, %s. %s" to "%s – %s, %s। %s",

        // Health
        ", worse than yesterday" to ", कल से ख़राब",
        ", better than yesterday" to ", कल से बेहतर",
        ", same as yesterday" to ", कल जैसा ही",
        "Good time to air the house." to "घर की खिड़कियाँ खोलने का अच्छा समय है।",
        "Sensitive groups should limit long outdoor effort." to "संवेदनशील लोग बाहर लंबी मेहनत से बचें।",
        "Keep windows shut and wear a mask outdoors." to "खिड़कियाँ बंद रखें और बाहर मास्क पहनें।",
        "AQI %d (%s)%s" to "AQI %d (%s)%s",
        "Humidity %d%%." to "आर्द्रता %d%%।",

        // Commuters
        "Tomorrow's commute" to "कल का सफ़र",
        "%s, %d%% chance of rain. High %s." to "%s, बारिश की %d%% संभावना। अधिकतम %s।",
        "Fog on the roads" to "सड़कों पर कोहरा",
        "Visibility %s." to "दृश्यता %s।",
        "Leave 15 minutes earlier, low beams on." to "15 मिनट पहले निकलें, गाड़ी की लो बीम जलाएँ।",
        "Rain on your commute" to "सफ़र के दौरान बारिश",
        "%d%% chance %s. Leave 20 minutes earlier." to "%2\$s %1\$d%% संभावना। 20 मिनट पहले निकलें।",
        "Rain possible on your commute" to "सफ़र के दौरान बारिश हो सकती है",
        "%d%% chance. Leave 10 minutes earlier to be safe." to "%d%% संभावना। सुरक्षित रहने के लिए 10 मिनट पहले निकलें।",
        "Clear commute" to "सफ़र साफ़ रहेगा",
        "%s and %s. No rain in your window." to "%s और %s। आपके समय में बारिश नहीं।",
        "No rain in your window." to "आपके समय में बारिश नहीं।",

        // Events
        "Best day this week: %s" to "इस हफ़्ते का सबसे अच्छा दिन: %s",
        "This week in %s" to "इस हफ़्ते %s में",
        "%s and %d%% rain." to "%s और %d%% बारिश।",
        "Right now feels %s." to "अभी मौसम %s लग रहा है।",

        // Beach
        "Calm seas" to "शांत समुद्र",
        "Slight seas" to "हल्की लहरों वाला समुद्र",
        "Moderate seas" to "मध्यम लहरों वाला समुद्र",
        "Rough seas" to "उग्र समुद्र",
        "%s, %.1f m waves" to "%s, %.1f m की लहरें",
        "Water %s." to "पानी का तापमान %s।",
        "Stay out of the water." to "पानी में न उतरें।",

        // General
        "%s alert: %s" to "%s अलर्ट: %s",
        "Tomorrow %s – %s, %s" to "कल %s – %s, %s",
        "%s and %s in %s" to "%s और %s, %s में",
        "Weather in %s" to "%s का मौसम",
        "High %s, low %s." to "अधिकतम %s, न्यूनतम %s।",
        "Rain likely from %s." to "%s से बारिश की संभावना।",
        "No rain expected." to "बारिश की संभावना नहीं।",
    )
}
