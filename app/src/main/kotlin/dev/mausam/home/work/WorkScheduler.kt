package dev.mausam.home.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.mausam.home.domain.cards.UserSettings
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class WorkScheduler(private val context: Context) {
    companion object {
        const val REFRESH = "mausam.refresh"
        const val BRIEF_MORNING = "mausam.brief.morning"
        const val BRIEF_EVENING = "mausam.brief.evening"
    }

    private val wm get() = WorkManager.getInstance(context)

    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(REFRESH, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow() {
        wm.enqueueUniqueWork(
            "$REFRESH.now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun scheduleBriefs(settings: UserSettings) {
        scheduleBrief(evening = false, settings = settings)
        scheduleBrief(evening = true, settings = settings)
    }

    fun scheduleBrief(evening: Boolean, settings: UserSettings) {
        val at = if (evening) settings.eveningBrief else settings.morningBrief
        val delay = delayUntil(at)
        val request = OneTimeWorkRequestBuilder<BriefWorker>()
            .setInputData(BriefWorker.input(evening))
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(if (evening) BRIEF_EVENING else BRIEF_MORNING, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelBriefs() {
        wm.cancelUniqueWork(BRIEF_MORNING)
        wm.cancelUniqueWork(BRIEF_EVENING)
    }

    private fun delayUntil(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): Duration {
        var next = now.with(time)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
