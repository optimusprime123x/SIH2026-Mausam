package dev.mausam.home.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mausam.home.MainActivity
import dev.mausam.home.MausamApp
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.CardValue
import dev.mausam.home.domain.cards.Ranker
import dev.mausam.home.domain.geo.Coastline
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.ui.theme.ImdTiers
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZonedDateTime

/** Temperature, condition, top-ranked card, alert colour strip. Reads the cache only. */
class MausamWidget : GlanceAppWidget() {
    private data class Snapshot(val temp: String, val condition: String, val top: String, val asOf: String, val strip: Color?, val place: String)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = runCatching { load(context) }.getOrNull()
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    snap?.strip?.let { Box(GlanceModifier.fillMaxWidth().height(4.dp).background(ColorProvider(it))) {} }
                    Column(GlanceModifier.padding(12.dp)) {
                        Text(snap?.place ?: "Mausam", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                        Text(snap?.temp ?: "—", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Medium))
                        Text(snap?.condition ?: "Open Mausam to load", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                        Spacer(GlanceModifier.height(6.dp))
                        Text(snap?.top ?: "", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
                        Text(snap?.asOf ?: "", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                    }
                }
            }
        }
    }

    private suspend fun load(context: Context): Snapshot? {
        val graph = MausamApp.graph(context)
        val repo = graph.repository
        val loc = repo.primaryLocation() ?: return null
        val cached = repo.cachedBundle(loc) ?: return null
        val settings = repo.currentSettings()
        val now = ZonedDateTime.now(loc.zoneId)
        val ctx = CardContext(now, loc, cached.data, Coastline.distanceKm(loc.latitude, loc.longitude), settings)
        val ranked = Ranker.rank(CardRegistry.all, ctx, repo.cardUsage.first(), repo.cardPrefs.first())
        val top = ranked.firstOrNull()
        val topText = top?.let { c ->
            when (val v = c.value) {
                is CardValue.Ready -> "${c.spec.title}: ${v.primary}${v.unit?.let { " $it" } ?: ""}"
                is CardValue.Pending -> "${c.spec.title}: ${v.reason}"
                is CardValue.Unavailable -> c.spec.title
            }
        } ?: ""
        val fmt = Formatter(settings.units, loc.zoneId)
        val cur = cached.data.current
        val warning = cached.data.activeWarnings(Instant.now()).firstOrNull()
        return Snapshot(
            temp = cur?.let { fmt.temp(it.temperatureC) } ?: "—",
            condition = cur?.condition?.label() ?: "",
            top = topText,
            asOf = Freshness.of(cached.fetchedAt, Instant.now(), loc.zoneId).label,
            strip = warning?.let { ImdTiers.solid(it.severity) },
            place = loc.name,
        )
    }
}

class MausamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MausamWidget()
}
