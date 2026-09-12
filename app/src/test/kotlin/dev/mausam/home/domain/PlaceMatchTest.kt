package dev.mausam.home.domain

import dev.mausam.home.domain.geo.PlaceMatch
import dev.mausam.home.domain.model.SoilCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatchTest {
    @Test fun wholeWordOnly() {
        assertTrue(PlaceMatch.mentions("Purba Medinipur, Digha, Contai", "Digha"))
        assertFalse(PlaceMatch.mentions("Patna (Dighaghat), Danapur", "Digha"))
        assertFalse(PlaceMatch.mentions("Purnia, Katihar", "Puri"))
        assertTrue(PlaceMatch.mentions("NORTH 24 PARGANAS; SOUTH 24 PARGANAS", "South 24 Parganas"))
        assertFalse(PlaceMatch.mentions("South 24 Parganas", "North 24 Parganas"))
        assertFalse(PlaceMatch.mentions("anything", null))
    }

    @Test fun soilBands() {
        assertEquals(SoilCategory.VERY_DRY, SoilCategory.of(6.0))
        assertEquals(SoilCategory.DRY, SoilCategory.of(15.0))
        assertEquals(SoilCategory.ADEQUATE, SoilCategory.of(25.0))
        assertEquals(SoilCategory.WET, SoilCategory.of(36.0))
        assertEquals(SoilCategory.SATURATED, SoilCategory.of(45.0))
    }
}
