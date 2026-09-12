package dev.mausam.home.domain.i18n

/**
 * Chrome copy: settings, onboarding, locations, home, detail sheet, catalogue, widget and
 * notification channels. Keys are the exact English source strings; templates keep `%s` / `%d`
 * in the English order. Proper nouns (IMD, CPCB, NDMA SACHET, Open-Meteo, Material You) stay Latin.
 */
object HindiUi {
    val table: Map<String, String> = mapOf(
        // Shared chrome
        "Back" to "वापस",
        "Settings" to "सेटिंग्स",
        "Locations" to "स्थान",
        "Add cards" to "कार्ड जोड़ें",
        "Refresh" to "ताज़ा करें",
        "Refreshing" to "ताज़ा हो रहा है",
        "Set" to "सेट करें",
        "Cancel" to "रद्द करें",
        "Skip" to "छोड़ें",
        "Continue" to "आगे",

        // Settings: sections and hints
        "Personas" to "पर्सोना",
        "Cards on your home page come from these" to "आपके होम पेज के कार्ड इन्हीं से बनते हैं",
        "Appearance" to "दिखावट",
        "Wallpaper colours" to "वॉलपेपर के रंग",
        "Needs Android 12 or newer; using the IMD blue palette" to "Android 12 या नया चाहिए; IMD नीला पैलेट इस्तेमाल हो रहा है",
        "Material You palette from your wallpaper" to "आपके वॉलपेपर से Material You पैलेट",
        "Off: IMD blue palette" to "बंद: IMD नीला पैलेट",
        "Current palette" to "मौजूदा पैलेट",
        "Weather effects" to "मौसम इफ़ेक्ट",
        "Animated sky, clouds and rain · glass blur %s" to "एनिमेटेड आसमान, बादल और बारिश · ग्लास ब्लर %s",
        "off (%s)" to "बंद (%s)",
        "on" to "चालू",
        "needs Android 12 or newer" to "Android 12 या नया चाहिए",
        "reduce transparency is on" to "पारदर्शिता कम करना चालू है",
        "off in large-text mode" to "बड़े टेक्स्ट मोड में बंद",
        "Large text" to "बड़ा टेक्स्ट",
        "Single column, bigger values, denser glass" to "एक कॉलम, बड़े मान, गाढ़ा ग्लास",
        "Daily briefs" to "दैनिक ब्रीफ़",
        "A morning and evening note for your persona" to "आपके पर्सोना के लिए सुबह और शाम की सूचना",
        "Morning brief" to "सुबह की ब्रीफ़",
        "Evening brief" to "शाम की ब्रीफ़",
        "Preview morning" to "सुबह देखें",
        "Preview evening" to "शाम देखें",
        "Allow notifications" to "सूचनाएँ चालू करें",
        "Quiet hours" to "शांत घंटे",
        "Briefs stay silent; orange and red alerts still come through" to "ब्रीफ़ चुप रहती हैं; नारंगी और लाल अलर्ट फिर भी आते हैं",
        "Start" to "शुरू",
        "End" to "समाप्त",
        "Commute" to "आना-जाना",
        "Leave-earlier nudges use this window" to "जल्दी निकलने की सलाह इसी समय पर आधारित है",
        "Commute starts" to "आना-जाना शुरू",
        "Commute ends" to "आना-जाना समाप्त",
        "Units" to "इकाइयाँ",
        "Language" to "भाषा",
        "Privacy & credits" to "निजता और श्रेय",
        "Your usage never leaves this phone. Card ranking, taps and preferences are stored only on this device." to
            "आपका उपयोग डेटा इस फ़ोन से बाहर कभी नहीं जाता। कार्ड रैंकिंग, टैप और पसंद सिर्फ़ इसी डिवाइस पर रहती हैं।",
        "Data: India Meteorological Department, NDMA SACHET, CPCB via data.gov.in, Weather data by Open-Meteo.com (CC BY 4.0). Icons: Meteocons by Bas Milius (MIT). Font: Roboto Flex (OFL)." to
            "डेटा: भारत मौसम विज्ञान विभाग (IMD), NDMA SACHET, CPCB (data.gov.in के ज़रिए), मौसम डेटा Open-Meteo.com से (CC BY 4.0)। आइकन: Meteocons, Bas Milius (MIT)। फ़ॉन्ट: Roboto Flex (OFL)।",

        // Onboarding
        "Weather for the way you live." to "आपकी ज़िंदगी के हिसाब से मौसम।",
        "Mausam uses your location to show IMD warnings and nowcasts for your district." to
            "Mausam आपके ज़िले की IMD चेतावनियाँ और नाउकास्ट दिखाने के लिए आपका स्थान इस्तेमाल करता है।",
        "Use my location" to "मेरा स्थान लें",
        "Couldn't get a fix. Search for your city instead." to "स्थान नहीं मिल सका। अपना शहर खोजें।",
        "Or search for a city" to "या कोई शहर खोजें",
        "City or district" to "शहर या ज़िला",
        "Your usage never leaves this phone." to "आपका उपयोग डेटा इस फ़ोन से बाहर कभी नहीं जाता।",
        "What matters to you?" to "आपके लिए क्या मायने रखता है?",
        "Pick any. Your home page is built from these." to "कोई भी चुनें। इन्हीं से आपका होम पेज बनता है।",

        // Locations
        "Use current location" to "मौजूदा स्थान लें",
        "Add a city or district" to "शहर या ज़िला जोड़ें",
        "No place called \"%s\"" to "\"%s\" नाम की कोई जगह नहीं",
        "Try a district or a nearby city." to "कोई ज़िला या पास का शहर आज़माएँ।",
        "No saved places yet" to "अभी कोई जगह सहेजी नहीं",
        "Home location" to "होम स्थान",
        "Move up" to "ऊपर करें",
        "Move down" to "नीचे करें",
        "Remove" to "हटाएँ",

        // Home, cards and banner
        "No cards yet" to "अभी कोई कार्ड नहीं",
        "Add some from the catalogue." to "कैटलॉग से कुछ जोड़ें।",
        "Showing bundled sample data until the network answers" to "नेटवर्क जवाब देने तक सैंपल डेटा दिखाया जा रहा है",
        "Weather data by Open-Meteo.com · IMD · NDMA SACHET · CPCB" to "मौसम डेटा Open-Meteo.com · IMD · NDMA SACHET · CPCB से",
        "Caution" to "सावधान",
        "Watch" to "नज़र रखें",
        "Danger" to "ख़तरा",
        "source %s" to "स्रोत %s",
        "Unpin" to "अनपिन करें",
        "Pin to top" to "ऊपर पिन करें",
        "Move to top" to "ऊपर ले जाएँ",
        "Hide" to "छिपाएँ",
        "Pinned" to "पिन किया",
        "Close alert" to "अलर्ट बंद करें",

        // Detail sheet
        "Card" to "कार्ड",
        "Nothing to show yet." to "अभी दिखाने को कुछ नहीं।",
        "Last updated %s" to "आख़िरी अपडेट %s",
        "Past 24 hours" to "पिछले 24 घंटे",
        "%d to %d AQI over the last day" to "पिछले एक दिन में AQI %d से %d",
        "No trend history yet." to "अभी कोई ट्रेंड इतिहास नहीं।",
        "Station: %s" to "स्टेशन: %s",
        "All clear" to "सब ठीक है",
        "No active IMD warnings for %s." to "%s के लिए कोई सक्रिय IMD चेतावनी नहीं।",
        "Add cities in Locations to see them here." to "यहाँ देखने के लिए स्थान में शहर जोड़ें।",

        // Widget
        "Open Mausam to load" to "लोड करने के लिए Mausam खोलें",

        // Notification channels and alert title
        "Weather alerts" to "मौसम अलर्ट",
        "Orange and red warnings and severe nowcasts for your district" to "आपके ज़िले की नारंगी और लाल चेतावनियाँ और गंभीर नाउकास्ट",
        "Morning and evening weather briefs shaped to your personas" to "आपके पर्सोना के हिसाब से सुबह और शाम की मौसम ब्रीफ़",
        "%s alert · %s" to "%s अलर्ट · %s",
    )
}
