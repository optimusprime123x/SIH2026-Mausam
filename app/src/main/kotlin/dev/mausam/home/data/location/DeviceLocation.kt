package dev.mausam.home.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dev.mausam.home.data.geo.Stations
import dev.mausam.home.domain.model.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Current position without Play Services: LocationManager (coarse is enough for a district),
 * then a best-effort reverse geocode with the bundled station/district table as the offline name.
 */
class DeviceLocation(private val context: Context, private val stations: Stations) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(timeoutMs: Long = 12_000): Location? {
        if (!hasPermission()) return null
        val fix = withTimeoutOrNull(timeoutMs) { rawFix() } ?: lastKnown() ?: return null
        return toLocation(fix.latitude, fix.longitude)
    }

    @Suppress("MissingPermission")
    private suspend fun rawFix(): android.location.Location? = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc -> if (cont.isActive) cont.resume(loc) }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, { loc -> if (cont.isActive) cont.resume(loc) }, context.mainLooper)
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(): android.location.Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    suspend fun toLocation(lat: Double, lon: Double): Location {
        val resolved = stations.resolve(lat, lon)
        val geocoded = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()
        }
        val name = geocoded?.locality ?: geocoded?.subAdminArea ?: resolved.district?.name?.let(::title) ?: resolved.station?.name?.substringBefore('-') ?: "Current location"
        val region = geocoded?.adminArea ?: resolved.district?.state?.let(::title)
        return stations.enrich(
            Location(
                id = "here", name = name, latitude = lat, longitude = lon, region = region,
                imdStationId = resolved.station?.id, district = resolved.district?.name, state = resolved.district?.state,
            ),
        )
    }

    private fun title(s: String) = s.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
