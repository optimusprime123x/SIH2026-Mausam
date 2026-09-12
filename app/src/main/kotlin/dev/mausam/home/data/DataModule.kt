package dev.mausam.home.data

import android.content.Context
import dev.mausam.home.data.fake.InMemoryHomeRepository
import dev.mausam.home.domain.HomeRepository

/** Builds the data layer. The in-memory repository stands in until the sources are wired. */
object DataModule {
    fun repository(context: Context): HomeRepository = InMemoryHomeRepository()
}
