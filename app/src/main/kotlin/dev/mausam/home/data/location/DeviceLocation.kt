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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Current position without Play Services, tuned for a first fix that feels instant:
 *
 * 1. Any last-known fix younger than ten minutes is used straight away (a district does not move).
 * 2. Otherwise every enabled provider is asked at once and the first answer wins, bounded to a few
 *    seconds; an older last-known fix is the fallback.
 * 3. The name comes from the bundled district table immediately; the platform geocoder only gets
 *    a short window to refine it, because on some devices it blocks for ten seconds or more.
 */
class DeviceLocation(private val context: Context, private val stations: Stations) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(timeoutMs: Long = 5_000): Location? {
        if (!hasPermission()) return null
        val recent = lastKnown()?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS }
        val fix = recent ?: withTimeoutOrNull(timeoutMs) { firstFix() } ?: lastKnown() ?: return null
        return toLocation(fix.latitude, fix.longitude)
    }

    @Suppress("MissingPermission")
    private suspend fun firstFix(): android.location.Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        val result = CompletableDeferred<android.location.Location?>()
        val signals = mutableListOf<CancellationSignal>()
        val executor = ContextCompat.getMainExecutor(context)
        var pending = providers.size
        for (p in providers) {
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal().also(signals::add)
                lm.getCurrentLocation(p, signal, executor) { loc ->
                    if (loc != null) result.complete(loc) else if (--pending == 0) result.complete(null)
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(p, { loc -> result.complete(loc) }, context.mainLooper)
            }
        }
        return try {
            result.await()
        } finally {
            signals.forEach { runCatching { it.cancel() } }
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(): android.location.Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    suspend fun toLocation(lat: Double, lon: Double): Location {
        val resolved = stations.resolve(lat, lon)
        val geocoded = withTimeoutOrNull(GEOCODER_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!Geocoder.isPresent()) return@runCatching null
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
                }.getOrNull()
            }
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

    private companion object {
        const val FRESH_MS = 10 * 60 * 1000L
        const val GEOCODER_MS = 1_500L
    }
}
