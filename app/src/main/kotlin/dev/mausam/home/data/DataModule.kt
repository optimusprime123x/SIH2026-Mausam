package dev.mausam.home.data

import android.content.Context
import dev.mausam.home.data.cache.MausamDatabase
import dev.mausam.home.data.cpcb.CpcbApi
import dev.mausam.home.data.geo.Stations
import dev.mausam.home.data.imd.ImdWfsApi
import dev.mausam.home.data.ndma.SachetApi
import dev.mausam.home.data.net.Http
import dev.mausam.home.data.openmeteo.OpenMeteoApi
import dev.mausam.home.data.prefs.SettingsStore
import dev.mausam.home.data.snapshots.SnapshotSource
import dev.mausam.home.domain.HomeRepository

/** Builds the data layer. Everything is lazy so the splash screen never waits on it. */
object DataModule {
    fun stations(context: Context): Stations = Stations.load(context)

    fun repository(context: Context, stations: Stations = stations(context)): HomeRepository {
        val client = Http.client(context)
        return HomeRepositoryImpl(
            db = MausamDatabase.build(context),
            settingsStore = SettingsStore(context),
            imd = Http.retrofit(client, ImdWfsApi.BASE_URL).create(ImdWfsApi::class.java),
            sachet = Http.retrofit(client, SachetApi.BASE_URL).create(SachetApi::class.java),
            openMeteo = Http.retrofit(client, OpenMeteoApi.BASE_URL).create(OpenMeteoApi::class.java),
            cpcb = Http.retrofit(client, CpcbApi.BASE_URL).create(CpcbApi::class.java),
            snapshots = SnapshotSource(context),
            stations = stations,
        )
    }
}
