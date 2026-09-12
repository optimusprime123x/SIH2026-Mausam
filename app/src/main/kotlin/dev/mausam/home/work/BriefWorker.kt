package dev.mausam.home.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.mausam.home.domain.i18n.L10n
import dev.mausam.home.domain.i18n.Lang
import dev.mausam.home.MausamApp
import dev.mausam.home.domain.briefs.BriefComposer
import dev.mausam.home.domain.personas.Persona
import java.time.LocalTime

/**
 * One local notification, morning or evening. Refreshes best-effort first so the copy is
 * current, composes persona-shaped text, respects quiet hours, then re-arms itself for tomorrow.
 */
class BriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_EVENING = "evening"
        fun input(evening: Boolean) = workDataOf(KEY_EVENING to evening)
    }

    override suspend fun doWork(): Result {
        val evening = inputData.getBoolean(KEY_EVENING, false)
        val graph = MausamApp.graph(applicationContext)
        val repo = graph.repository
        val settings = repo.currentSettings()
        L10n.lang = Lang.of(settings.language)
        try {
            val primary = repo.primaryLocation() ?: return Result.success()
            val ctx = repo.buildContext(primary, refresh = true) ?: return Result.success()
            val persona = settings.personas.firstOrNull { it != Persona.GENERAL } ?: Persona.GENERAL
            val brief = BriefComposer.compose(persona, ctx, evening)
            val nowLocal = LocalTime.now(primary.zoneId)
            if (!settings.isQuiet(nowLocal)) graph.notifier.postBrief(brief)
        } finally {
            graph.scheduler.scheduleBrief(evening, settings)
        }
        return Result.success()
    }
}
