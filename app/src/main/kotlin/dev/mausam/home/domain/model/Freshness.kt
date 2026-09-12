package dev.mausam.home.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Every card shows "as of HH:MM"; past two hours it turns grey with "offline, last updated HH:MM". */
data class Freshness(val fetchedAt: Instant, val isStale: Boolean, val label: String) {
    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(2)
        private val fmt = DateTimeFormatter.ofPattern("HH:mm")

        fun of(fetchedAt: Instant, now: Instant, zone: ZoneId): Freshness {
            val stale = Duration.between(fetchedAt, now) > STALE_AFTER
            val time = fmt.format(fetchedAt.atZone(zone))
            return Freshness(fetchedAt, stale, if (stale) "offline, last updated $time" else "as of $time")
        }
    }
}

/** Wrapper returned by every repository. UI never sees a raw network response. */
data class CachedResult<T>(
    val data: T,
    val fetchedAt: Instant,
    val isStale: Boolean,
    val origin: Origin,
) {
    enum class Origin { NETWORK, CACHE, SNAPSHOT }
}
