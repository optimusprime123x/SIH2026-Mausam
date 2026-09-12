package dev.mausam.home.ui.detail

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import dev.mausam.home.ui.common.AccentIconDisc
import dev.mausam.home.ui.theme.Fluent
import dev.mausam.home.ui.theme.accentSet
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.mausam.home.domain.aqi.IndianAqi
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.CardValue
import dev.mausam.home.domain.cards.DetailKind
import dev.mausam.home.domain.cards.HourlyMetric
import dev.mausam.home.domain.cards.RunScore
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.home.HomeUiState
import dev.mausam.home.ui.home.toneColor
import dev.mausam.home.ui.home.placeholderIcon
import dev.mausam.home.ui.theme.ImdTiers
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * In-window detail sheet (a ModalBottomSheet lives in its own window, which would break both the
 * shared-element morph and the glass sampling). Card bounds morph into the sheet, the value text is
 * a shared element, glass thickens from card to sheet. Swipe down or predictive back to dismiss.
 */
@Composable
fun SharedTransitionScope.DetailSheet(
    cardId: String,
    state: HomeUiState,
    haze: HazeState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
) {
    val spec = CardRegistry.byId(cardId)
    val ctx = state.context
    val value = remember(cardId, ctx) { if (spec != null && ctx != null) runCatching { spec.fetch(ctx) }.getOrNull() else null }
    val cs = MaterialTheme.colorScheme
    val accent = accentSet(Fluent.forCard(cardId))
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var backScale by remember { mutableFloatStateOf(1f) }

    PredictiveBackHandler { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { e -> backScale = 1f - 0.08f * e.progress }
            onDismiss()
        } catch (e: CancellationException) {
            backScale = 1f
            throw e
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(cs.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .graphicsLayer { translationY = dragY.value; scaleX = backScale; scaleY = backScale }
                .sharedBounds(
                    rememberSharedContentState(key = "card-$cardId"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                )
                .mausamGlass(haze, GlassTier.SHEET, MausamRadius.sheetShape, wash = accent.wash)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Drag handle + header: the drag-to-dismiss surface.
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy -> scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) } },
                            onDragEnd = {
                                if (dragY.value > 120.dp.toPx()) onDismiss()
                                else scope.launch { dragY.animateTo(0f) }
                            },
                            onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                        )
                    }
                    .padding(horizontal = Space.s6),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = Space.s3, bottom = Space.s2).width(32.dp).height(4.dp).clip(MausamRadius.chipShape).background(cs.outlineVariant))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((spec?.title ?: "Card").tr(), style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface)
                        if (value is CardValue.Ready) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(value.primary, style = MaterialTheme.typography.displaySmall, color = cs.onSurface)
                                value.unit?.let { Spacer(Modifier.width(Space.s2)); Text(it, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp)) }
                            }
                            value.secondary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.width(Space.s3))
                    AccentIconDisc((value as? CardValue.Ready)?.icon ?: placeholderIcon(cardId), accent, 72.dp)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.s6, vertical = Space.s4),
            ) {
                when (value) {
                    null -> Text("Nothing to show yet.".tr(), color = cs.onSurfaceVariant)
                    is CardValue.Pending -> Text(value.reason, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    is CardValue.Unavailable -> Text(value.message, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    is CardValue.Ready -> if (spec != null && ctx != null) DetailBody(spec.detail, ctx, value, state, accent.accent)
                }
                Spacer(Modifier.height(Space.s6))
                val src = spec?.sourceLabel?.takeIf { it.length >= 8 }?.let { l -> state.bundle?.sources?.values?.firstOrNull { it.label.contains(l.take(8), true) }?.label } ?: spec?.sourceLabel
                if (!src.isNullOrBlank()) Text(src.tr(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                state.freshness?.let { Text("Last updated %s".trf(it.fetchedAt.let { t -> state.context?.fmt?.time(t) ?: "" }), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                Spacer(Modifier.height(Space.s8))
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@Composable
private fun DetailBody(kind: DetailKind, ctx: CardContext, value: CardValue.Ready, state: HomeUiState, accentColor: Color) {
    val cs = MaterialTheme.colorScheme
    val fmt = ctx.fmt
    when (kind) {
        is DetailKind.Hourly -> {
            val hours = ctx.bundle.hourlyFrom(ctx.nowInstant, 24)
            val aqi = ctx.bundle.airQuality?.aqi
            Text("Next 24 hours".tr(), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(Space.s2))
            LazyRow { items(hours.size) { i ->
                val h = hours[i]
                Column(Modifier.padding(end = Space.s4), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt.hourLabel(h.time), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    MeteoconIcon(WeatherIcon.forCondition(h.condition, h.isDay), 32.dp, animated = false)
                    val v = when (kind.metric) {
                        HourlyMetric.TEMPERATURE -> fmt.temp(h.temperatureC)
                        HourlyMetric.PRECIPITATION -> "${h.precipitationProbabilityPct ?: 0}%"
                        HourlyMetric.WIND -> h.windKph?.let { "${it.roundToInt()}" } ?: "—"
                        HourlyMetric.UV -> h.uvIndex?.let { "${it.roundToInt()}" } ?: "—"
                        HourlyMetric.HUMIDITY -> h.humidityPct?.let { "$it%" } ?: "—"
                        HourlyMetric.VISIBILITY -> h.visibilityKm?.let { fmt.distance(it) } ?: "—"
                        HourlyMetric.RUN_SCORE -> "${RunScore.score(h, aqi)}"
                    }
                    Text(v, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    val bar = when (kind.metric) {
                        HourlyMetric.PRECIPITATION -> (h.precipitationProbabilityPct ?: 0) / 100f
                        HourlyMetric.RUN_SCORE -> RunScore.score(h, aqi) / 100f
                        HourlyMetric.UV -> ((h.uvIndex ?: 0.0) / 11.0).toFloat()
                        HourlyMetric.HUMIDITY -> (h.humidityPct ?: 0) / 100f
                        else -> ((h.temperatureC - 5) / 40.0).toFloat()
                    }.coerceIn(0.05f, 1f)
                    // Bars grow in from zero when the sheet opens, one after another.
                    var grown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(30L * i); grown = true }
                    val h by animateFloatAsState(if (grown) bar else 0.05f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar")
                    Box(Modifier.padding(top = Space.s1).width(6.dp).height((40 * h).dp).clip(MausamRadius.chipShape).background(accentColor))
                }
            } }
        }
        DetailKind.SevenDay -> {
            Text("7-day forecast".tr(), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            val week = ctx.bundle.daily.take(7)
            val lo = week.minOfOrNull { it.minC } ?: 0.0
            val hi = week.maxOfOrNull { it.maxC } ?: 1.0
            week.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayFmt.format(d.date), style = MaterialTheme.typography.titleSmall, color = cs.onSurface, modifier = Modifier.width(44.dp))
                    MeteoconIcon(WeatherIcon.forCondition(d.condition, true), 32.dp, animated = false)
                    Spacer(Modifier.width(Space.s2))
                    Text("${d.precipitationProbabilityPct ?: 0}%", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp))
                    Text(fmt.temp(d.minC), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(36.dp))
                    Canvas(Modifier.weight(1f).height(8.dp)) {
                        val span = (hi - lo).coerceAtLeast(1.0)
                        val x0 = ((d.minC - lo) / span * size.width).toFloat()
                        val x1 = ((d.maxC - lo) / span * size.width).toFloat()
                        drawRoundRect(cs.surfaceVariant, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        drawRoundRect(accentColor, topLeft = Offset(x0, 0f), size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(6.dp.toPx()), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                    Text(fmt.temp(d.maxC), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.width(40.dp).padding(start = Space.s2))
                }
            }
        }
        DetailKind.AqiTrend -> {
            val aq = ctx.bundle.airQuality
            Text("Past 24 hours".tr(), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            val pts = aq?.history24h ?: emptyList()
            if (pts.size >= 2) {
                Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = Space.s2)) {
                    val minV = pts.minOf { it.aqi }.toFloat(); val maxV = pts.maxOf { it.aqi }.toFloat().coerceAtLeast(minV + 1)
                    val path = Path()
                    pts.forEachIndexed { i, p ->
                        val x = i / (pts.size - 1f) * size.width
                        val y = size.height - (p.aqi - minV) / (maxV - minV) * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, accentColor, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Text("%d to %d AQI over the last day".trf(pts.minOf { it.aqi }, pts.maxOf { it.aqi }), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else Text("No trend history yet.".tr(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            aq?.let {
                Spacer(Modifier.height(Space.s3))
                val cat = IndianAqi.category(it.aqi)
                Text("${cat.label.tr()}: ${cat.advice.tr()}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Row { it.pm25?.let { v -> Text("PM2.5 ${v.roundToInt()} µg/m³   ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }; it.pm10?.let { v -> Text("PM10 ${v.roundToInt()} µg/m³", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) } }
                it.stationName?.let { s -> Text("Station: %s".trf(s), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
            }
        }
        DetailKind.Warnings -> {
            val list = state.activeWarnings
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(dev.mausam.home.R.drawable.spot_all_clear), contentDescription = null, modifier = Modifier.width(220.dp).height(184.dp))
                    Text("All clear".tr(), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("No active IMD warnings for %s.".trf(ctx.location.name), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
            list.forEach { WarningRow(it) }
        }
        DetailKind.Destinations -> {
            if (ctx.destinations.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(dev.mausam.home.R.drawable.spot_destinations), contentDescription = null, modifier = Modifier.width(220.dp).height(184.dp))
                    Text("Add cities in Locations to see them here.".tr(), color = cs.onSurfaceVariant)
                }
            }
            ctx.destinations.forEach { b -> DestinationRow(b, ctx) }
        }
        DetailKind.Text -> Text(value.body ?: value.secondary ?: "", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        DetailKind.None -> value.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface) }
    }
}

@Composable
private fun WarningRow(w: WeatherWarning) {
    val cs = MaterialTheme.colorScheme
    val tier = ImdTiers.of(w.severity, LocalIsDark.current)
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(MausamRadius.chipShape).background(tier.container).padding(horizontal = Space.s2, vertical = 2.dp)) {
                Text("${w.severity.label.tr()} · ${w.severity.advice.tr()}", style = MaterialTheme.typography.labelMedium, color = tier.onContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(Space.s2))
            Text(w.event.tr(), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
        }
        Text(w.area, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Text(w.description.ifBlank { w.headline }.tr(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(w.source.tr(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun DestinationRow(b: WeatherBundle, ctx: CardContext) {
    val cs = MaterialTheme.colorScheme
    val d = b.daily.firstOrNull()
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        d?.let { MeteoconIcon(WeatherIcon.forCondition(it.condition, true), 36.dp, animated = false) }
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(b.location.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            Text(d?.let { "${ctx.fmt.temp(it.minC)} – ${ctx.fmt.temp(it.maxC)} · ${it.condition.label().tr()}" } ?: "No forecast cached".tr(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        val w = b.activeWarnings(ctx.nowInstant).firstOrNull()
        w?.let { toneColor(when (it.severity) { dev.mausam.home.domain.model.WarningSeverity.RED -> dev.mausam.home.domain.cards.Tone.DANGER; dev.mausam.home.domain.model.WarningSeverity.ORANGE -> dev.mausam.home.domain.cards.Tone.WARNING; else -> dev.mausam.home.domain.cards.Tone.CAUTION })?.let { c -> Box(Modifier.width(10.dp).height(10.dp).clip(MausamRadius.chipShape).background(c)) } }
    }
}
