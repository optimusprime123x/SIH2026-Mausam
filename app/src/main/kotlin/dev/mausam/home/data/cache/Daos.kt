package dev.mausam.home.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RawPayloadDao {
    @Upsert suspend fun upsert(entity: RawPayloadEntity)

    @Query("SELECT * FROM raw_payloads WHERE locationId = :locationId OR locationId = '*'")
    fun observeFor(locationId: String): Flow<List<RawPayloadEntity>>

    @Query("SELECT * FROM raw_payloads WHERE locationId = :locationId OR locationId = '*'")
    suspend fun getFor(locationId: String): List<RawPayloadEntity>

    @Query("SELECT * FROM raw_payloads WHERE key = :key")
    suspend fun get(key: String): RawPayloadEntity?

    @Query("DELETE FROM raw_payloads WHERE locationId = :locationId")
    suspend fun deleteFor(locationId: String)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY sortOrder ASC")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun get(id: String): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id") suspend fun delete(id: String)

    @Query("UPDATE locations SET sortOrder = :order WHERE id = :id") suspend fun setOrder(id: String, order: Int)

    @Query("UPDATE locations SET district = :district WHERE id = :id") suspend fun setDistrict(id: String, district: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM locations") suspend fun nextOrder(): Int
}

@Dao
interface CardUsageDao {
    @Query("SELECT * FROM card_usage") fun observeAll(): Flow<List<CardUsageEntity>>
    @Query("SELECT * FROM card_usage WHERE cardId = :id") suspend fun get(id: String): CardUsageEntity?
    @Upsert suspend fun upsert(entity: CardUsageEntity)
}

@Dao
interface CardPrefDao {
    @Query("SELECT * FROM card_prefs") fun observeAll(): Flow<List<CardPrefEntity>>
    @Query("SELECT * FROM card_prefs WHERE cardId = :id") suspend fun get(id: String): CardPrefEntity?
    @Upsert suspend fun upsert(entity: CardPrefEntity)
}
