package dev.mausam.home

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Configuration
import dev.mausam.home.data.DataModule
import dev.mausam.home.data.geo.Stations
import dev.mausam.home.data.location.DeviceLocation
import dev.mausam.home.domain.HomeRepository
import dev.mausam.home.work.Notifier
import dev.mausam.home.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled object graph. Small enough that a DI framework would cost more than it saves,
 * and IMD can swap any node when dropping the module into Mausam.
 */
class AppGraph(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val stations: Stations by lazy { DataModule.stations(context) }
    val repository: HomeRepository by lazy { DataModule.repository(context, stations) }
    val deviceLocation: DeviceLocation by lazy { DeviceLocation(context, stations) }

    /** Set from a notification tap before the UI is composed; consumed once by the home screen. */
    @Volatile var pendingOpen: String? = null
    val notifier: Notifier by lazy { Notifier(context) }
    val scheduler: WorkScheduler by lazy { WorkScheduler(context) }

    /** Listeners that need to know the cache changed (the Glance widget registers here). */
    private val refreshListeners = mutableListOf<suspend () -> Unit>()

    fun addRefreshListener(listener: suspend () -> Unit) {
        refreshListeners += listener
    }

    fun onDataRefreshed() {
        appScope.launch { refreshListeners.forEach { runCatching { it() } } }
    }
}

class MausamApp : Application(), Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        graph.notifier.ensureChannels()
        graph.scheduler.schedulePeriodicRefresh()
        graph.addRefreshListener { dev.mausam.home.widget.MausamWidget().updateAll(this) }
        graph.appScope.launch {
            graph.scheduler.scheduleBriefs(graph.repository.currentSettings())
        }
    }

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as MausamApp).graph
    }
}
