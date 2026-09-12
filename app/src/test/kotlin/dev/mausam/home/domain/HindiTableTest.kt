package dev.mausam.home.domain

import dev.mausam.home.domain.i18n.Hindi
import dev.mausam.home.domain.i18n.HindiBriefs
import dev.mausam.home.domain.i18n.HindiCards
import dev.mausam.home.domain.i18n.HindiCommon
import dev.mausam.home.domain.i18n.HindiData
import dev.mausam.home.domain.i18n.HindiUi
import dev.mausam.home.domain.i18n.L10n
import dev.mausam.home.domain.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiTableTest {
    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    @Test fun everyEntryIsTranslatedAndKeepsPlaceholders() {
        for ((en, hi) in Hindi.table) {
            assertTrue("blank translation for '$en'", hi.isNotBlank())
            assertEquals("placeholders differ for '$en'", placeholder.findAll(en).count(), placeholder.findAll(hi).count())
            assertTrue("no Devanagari in '$en' -> '$hi'", hi.any { it in '\u0900'..'\u097F' } || hi == en)
        }
    }

    @Test fun noKeyIsDefinedTwice() {
        val parts = listOf(HindiCommon.table, HindiCards.table, HindiBriefs.table, HindiUi.table, HindiData.table)
        val expected = parts.sumOf { it.size }
        val dupes = parts.flatMap { it.keys }.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals("keys defined in more than one table: $dupes", expected, Hindi.table.size)
    }

    @Test fun lookupFallsBackToEnglish() {
        assertEquals("no such key", L10n.tr("no such key", Lang.HI))
        assertEquals("धुंध", L10n.tr("Haze", Lang.HI))
        assertEquals("Haze", L10n.tr("Haze", Lang.EN))
    }
}
