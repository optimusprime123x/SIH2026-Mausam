package dev.mausam.home.ui.locations

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.mausam.home.R
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import androidx.compose.material3.minimumInteractiveComponentSize
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.domain.model.SceneKind
import dev.mausam.home.ui.common.GlassGroup
import dev.mausam.home.ui.common.HairlineDivider
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.scene.AuroraBackdrop
import dev.mausam.home.ui.scene.sceneGradient
import dev.mausam.home.ui.theme.LocalIsDark
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space

/** Saved cities, each row a mini hero painted in its own sky. Tap sets the home; arrows reorder; bin removes. */
@Composable
fun LocationsScreen(vm: LocationsViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    LocationsContent(
        rows = rows, query = query, results = results, locating = locating, units = units, onBack = onBack,
        onQuery = vm::onQuery, onAdd = { vm.add(it) }, onDeviceLocation = { vm.addDeviceLocation() },
        onSetPrimary = { vm.setPrimary(it) }, onMove = { id, d -> vm.move(id, d) }, onRemove = { vm.remove(it) },
    )
}

@Composable
fun LocationsContent(
    rows: List<LocationRow>, query: String, results: List<dev.mausam.home.domain.model.Location>, locating: Boolean,
    units: dev.mausam.home.domain.model.Units = dev.mausam.home.domain.model.Units.METRIC, onBack: () -> Unit,
    onQuery: (String) -> Unit, onAdd: (dev.mausam.home.domain.model.Location) -> Unit, onDeviceLocation: () -> Unit,
    onSetPrimary: (String) -> Unit, onMove: (String, Int) -> Unit, onRemove: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val a11y = LocalMausamA11y.current
    val dark = LocalIsDark.current
    val fmt = remember(units) { Formatter(units) }
    val haze = remember { HazeState() }
    haze.blurEnabled = a11y.glassBlur

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(animated = a11y.sceneAnimated, modifier = Modifier.fillMaxSize().hazeSource(haze))
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back".tr()) }
                Text("Locations".tr(), style = MaterialTheme.typography.headlineSmallEmphasized, color = cs.onSurface, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onDeviceLocation, enabled = !locating) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.MyLocation, contentDescription = "Use current location".tr())
                }
            }
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true, placeholder = { Text("Add a city or district".tr()) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = MausamRadius.innerShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4),
            )
            LazyColumn(contentPadding = PaddingValues(Space.s4)) {
                if (query.length >= 2) {
                    if (results.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = Space.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(R.drawable.spot_search), contentDescription = null, modifier = Modifier.size(200.dp))
                                Text("No place called \"%s\"".trf(query), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                                Text("Try a district or a nearby city.".tr(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    } else {
                        item {
                            GlassGroup(haze) {
                                results.forEachIndexed { i, loc ->
                                    Row(Modifier.fillMaxWidth().clickable { onAdd(loc) }.padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Place, contentDescription = null, tint = cs.primary)
                                        Spacer(Modifier.width(Space.s3))
                                        Column {
                                            Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                                            Text(loc.region ?: "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                        }
                                    }
                                    if (i < results.lastIndex) HairlineDivider()
                                }
                            }
                        }
                    }
                } else {
                    if (rows.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = Space.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(R.drawable.spot_destinations), contentDescription = null, modifier = Modifier.size(220.dp))
                                Text("No saved places yet".tr(), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            }
                        }
                    }
                    items(rows.size, key = { rows[it].location.id }) { i ->
                        val row = rows[i]
                        val cur = row.bundle?.data?.current
                        val kind = cur?.let { SceneKind.from(it.condition, it.isDay) } ?: SceneKind.CLOUDY
                        val sky = sceneGradient(kind, dark)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Space.cardGap)
                                .mausamGlass(haze, GlassTier.CARD, MausamRadius.cardShape, tint = sky[1], wash = sky[0].copy(alpha = 0.5f))
                                .clickable { onSetPrimary(row.location.id) },
                        ) {
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1220).copy(alpha = 0f), Color(0xFF0B1220).copy(alpha = 0.45f)))))
                            Row(Modifier.fillMaxWidth().padding(Space.s4), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (row.isPrimary) {
                                            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Check, contentDescription = "Home location".tr(), tint = Color(0xFF0B1220), modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(Modifier.width(Space.s2))
                                        }
                                        Text(row.location.name, style = MaterialTheme.typography.titleLargeEmphasized, color = Color.White)
                                    }
                                    Text(listOfNotNull(row.location.region, cur?.condition?.label()?.tr()).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                                    Spacer(Modifier.height(Space.s2))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                                        SmallAction(Icons.Rounded.KeyboardArrowUp, "Move up".tr(), enabled = i > 0) { onMove(row.location.id, -1) }
                                        SmallAction(Icons.Rounded.KeyboardArrowDown, "Move down".tr(), enabled = i < rows.size - 1) { onMove(row.location.id, 1) }
                                        SmallAction(Icons.Rounded.Delete, "Remove".tr(), enabled = rows.size > 1) { onRemove(row.location.id) }
                                    }
                                }
                                cur?.let {
                                    Text(fmt.temp(it.temperatureC), style = MaterialTheme.typography.displaySmall, color = Color.White)
                                    Spacer(Modifier.width(Space.s2))
                                    MeteoconIcon(WeatherIcon.forCondition(it.condition, it.isDay), 64.dp, animated = false, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
    }
}
