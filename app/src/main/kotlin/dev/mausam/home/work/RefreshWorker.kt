package dev.mausam.home.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.mausam.home.MausamApp
import dev.mausam.home.domain.briefs.AlertPolicy
import java.time.Instant

/**
 * Every 30 minutes (WorkManager's floor is 15): refresh every saved location, then poll the
 * primary district's warnings and push any NEW orange or red one. Yellow stays banner-only.
 * Warnings bypass quiet hours by design; briefs do not.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = MausamApp.graph(applicationContext)
        val repo = graph.repository
        val locations = repo.currentLocations()
        if (locations.isEmpty()) return Result.success()

        var anyFailed = false
        locations.forEach { loc ->
            runCatching { repo.refresh(loc) }.onFailure { anyFailed = true }
        }

        val primary = repo.primaryLocation() ?: locations.first()
        val bundle = repo.cachedBundle(primary)?.data
        if (bundle != null) {
            val active = bundle.activeWarnings(Instant.now())
            val toPush = AlertPolicy.toPush(active, repo.notifiedWarningIds())
            if (toPush.isNotEmpty()) {
                toPush.forEach { graph.notifier.postAlert(it, primary.name) }
                repo.markWarningsNotified(toPush.map { it.id }.toSet())
            }
        }
        graph.onDataRefreshed()
        return if (anyFailed && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
