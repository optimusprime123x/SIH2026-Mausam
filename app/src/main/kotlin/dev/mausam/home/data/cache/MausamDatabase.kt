package dev.mausam.home.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RawPayloadEntity::class, LocationEntity::class, CardUsageEntity::class, CardPrefEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MausamDatabase : RoomDatabase() {
    abstract fun rawPayloads(): RawPayloadDao
    abstract fun locations(): LocationDao
    abstract fun cardUsage(): CardUsageDao
    abstract fun cardPrefs(): CardPrefDao

    companion object {
        fun build(context: Context): MausamDatabase =
            Room.databaseBuilder(context, MausamDatabase::class.java, "mausam-home.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
