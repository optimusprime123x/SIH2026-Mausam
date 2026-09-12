package dev.mausam.home.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.mausam.home.MausamApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Periodic work survives reboots, but the one-shot brief timers are re-armed here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val graph = MausamApp.graph(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                graph.scheduler.schedulePeriodicRefresh()
                graph.scheduler.scheduleBriefs(graph.repository.currentSettings())
            } finally {
                pending.finish()
            }
        }
    }
}
