package dev.mausam.home.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mausam.home.MainActivity
import dev.mausam.home.R
import dev.mausam.home.domain.briefs.Brief
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.model.WeatherWarning

/** Two channels: alerts (orange/red warnings, high importance) and briefs (twice daily). */
class Notifier(private val context: Context) {
    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_BRIEFS = "briefs"
        const val EXTRA_OPEN = "open"
        const val OPEN_WARNINGS = "warnings"
        const val OPEN_HOME = "home"
        private const val ID_BRIEF = 1001
        private const val ID_ALERT_BASE = 2000
    }

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Weather alerts".tr(), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Orange and red warnings and severe nowcasts for your district".tr()
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEFS, "Daily briefs".tr(), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morning and evening weather briefs shaped to your personas".tr()
            },
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(open: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, open)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun postBrief(brief: Brief) {
        if (!canPost()) return
        ensureChannels()
        val n = NotificationCompat.Builder(context, CHANNEL_BRIEFS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(brief.title)
            .setContentText(brief.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(brief.body))
            .setContentIntent(contentIntent(OPEN_HOME, ID_BRIEF))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BRIEF, n)
    }

    fun postAlert(warning: WeatherWarning, locationName: String) {
        if (!canPost()) return
        ensureChannels()
        val colour = when (warning.severity) {
            WarningSeverity.RED -> 0xFFD32F2F.toInt()
            WarningSeverity.ORANGE -> 0xFFEF6C00.toInt()
            WarningSeverity.YELLOW -> 0xFFF9A825.toInt()
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("%s alert · %s".trf(warning.severity.label.tr(), locationName))
            .setContentText(warning.headline.tr())
            .setStyle(NotificationCompat.BigTextStyle().bigText("${warning.headline.tr()}\n\n${warning.description}".trim()))
            .setColor(colour)
            .setColorized(warning.severity == WarningSeverity.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent(OPEN_WARNINGS, ID_ALERT_BASE + warning.id.hashCode().and(0xFFFF)))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT_BASE + warning.id.hashCode().and(0xFFFF), n)
    }
}
