package dev.mausam.home.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One raw upstream payload per (source, location). National payloads use locationId "*". */
@Entity(tableName = "raw_payloads")
data class RawPayloadEntity(
    @PrimaryKey val key: String,
    val source: String,
    val locationId: String,
    val json: String,
    val fetchedAt: Long,
    val fromSnapshot: Boolean,
) {
    companion object {
        fun key(source: String, locationId: String) = "$source|$locationId"
    }
}

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val stationId: String?,
    val district: String?,
    val state: String?,
    val sortOrder: Int,
)

@Entity(tableName = "card_usage")
data class CardUsageEntity(
    @PrimaryKey val cardId: String,
    val score: Double,
    val updatedAt: Long,
)

@Entity(tableName = "card_prefs")
data class CardPrefEntity(
    @PrimaryKey val cardId: String,
    val pinnedAt: Long?,
    val hidden: Boolean,
    val boostUntil: Long?,
    val added: Boolean,
)
