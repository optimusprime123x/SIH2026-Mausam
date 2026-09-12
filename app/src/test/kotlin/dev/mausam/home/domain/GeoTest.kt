package dev.mausam.home.domain

import dev.mausam.home.domain.geo.Geo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private val square = listOf(listOf(doubleArrayOf(80.0, 25.0), doubleArrayOf(81.0, 25.0), doubleArrayOf(81.0, 26.0), doubleArrayOf(80.0, 26.0), doubleArrayOf(80.0, 25.0)))
    private val hole = listOf(doubleArrayOf(80.4, 25.4), doubleArrayOf(80.6, 25.4), doubleArrayOf(80.6, 25.6), doubleArrayOf(80.4, 25.6), doubleArrayOf(80.4, 25.4))

    @Test fun insideSquare() = assertTrue(Geo.pointInPolygon(25.5, 80.2, square))
    @Test fun outsideSquare() = assertFalse(Geo.pointInPolygon(26.5, 80.2, square))
    @Test fun insideHoleIsOutside() = assertFalse(Geo.pointInPolygon(25.5, 80.5, square + listOf(hole)))
    @Test fun concaveNotch() {
        val notch = listOf(listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(4.0, 0.0), doubleArrayOf(4.0, 4.0), doubleArrayOf(2.0, 1.0), doubleArrayOf(0.0, 4.0), doubleArrayOf(0.0, 0.0)))
        assertTrue(Geo.pointInPolygon(0.5, 1.0, notch))
        assertFalse(Geo.pointInPolygon(3.0, 2.0, notch))
    }
}
