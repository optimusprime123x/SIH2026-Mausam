package dev.mausam.home.domain.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }
}

/**
 * Coarse polyline of the Indian coastline (mainland, plus island capitals), used only to gate
 * beach cards to places within ~30 km of the sea. Spacing is 30 to 60 km, which is plenty for
 * a yes/no gate and costs nothing to ship or evaluate.
 */
object Coastline {
    val points: List<Pair<Double, Double>> = listOf(
        // Gujarat: Kutch, Saurashtra, Gulf of Khambhat, south Gujarat
        23.22 to 68.62, 22.83 to 69.35, 22.82 to 69.72, 23.03 to 70.22, 22.97 to 70.45,
        22.47 to 69.07, 22.24 to 68.97, 21.64 to 69.61, 20.91 to 70.37, 20.71 to 70.98,
        20.87 to 71.37, 21.09 to 71.77, 21.77 to 72.15, 22.31 to 72.62, 21.70 to 72.58,
        21.10 to 72.63, 20.40 to 72.83, 19.97 to 72.72,
        // Maharashtra, Goa
        19.68 to 72.73, 19.45 to 72.78, 19.08 to 72.80, 18.64 to 72.87, 18.32 to 72.96,
        18.04 to 73.01, 17.81 to 73.09, 17.48 to 73.19, 16.99 to 73.30, 16.37 to 73.38,
        16.06 to 73.47, 15.86 to 73.63, 15.50 to 73.82, 15.28 to 73.91,
        // Karnataka, Kerala
        14.81 to 74.13, 14.55 to 74.31, 14.28 to 74.44, 13.97 to 74.55, 13.63 to 74.69,
        13.35 to 74.70, 12.87 to 74.84, 12.50 to 74.98, 11.87 to 75.36, 11.25 to 75.77,
        10.77 to 75.92, 9.97 to 76.24, 9.49 to 76.32, 8.89 to 76.58, 8.48 to 76.92, 8.08 to 77.55,
        // Tamil Nadu, Puducherry
        8.76 to 78.13, 9.29 to 79.31, 9.74 to 79.02, 10.37 to 79.85, 10.77 to 79.85,
        10.92 to 79.84, 11.75 to 79.77, 11.93 to 79.83, 12.62 to 80.19, 13.08 to 80.29, 13.42 to 80.32,
        // Andhra Pradesh, Odisha
        14.28 to 80.12, 15.50 to 80.15, 16.17 to 81.14, 16.95 to 82.25, 17.69 to 83.30,
        18.33 to 84.13, 19.26 to 84.92, 19.80 to 85.83, 19.89 to 86.10, 20.32 to 86.62,
        20.79 to 86.98, 21.45 to 87.03,
        // West Bengal
        21.63 to 87.51, 21.56 to 88.26, 21.65 to 88.08, 21.60 to 88.80,
        // Islands
        11.62 to 92.73, 10.57 to 72.64,
    )

    fun distanceKm(latitude: Double, longitude: Double): Double =
        points.minOf { (lat, lon) -> Geo.haversineKm(latitude, longitude, lat, lon) }

    fun isCoastal(latitude: Double, longitude: Double, withinKm: Double = 30.0): Boolean =
        distanceKm(latitude, longitude) <= withinKm
}
