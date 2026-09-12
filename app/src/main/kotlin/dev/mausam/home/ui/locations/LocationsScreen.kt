package dev.mausam.home.ui.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mausam.home.domain.cards.WeatherIcon
import dev.mausam.home.domain.model.Formatter
import dev.mausam.home.ui.common.MeteoconIcon
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.micaBase

/** Saved cities, each row a mini hero. Tap sets the home; arrows reorder; bin removes. */
@Composable
fun LocationsScreen(vm: LocationsViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val fmt = Formatter()
    Column(Modifier.fillMaxSize().background(micaBase()).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Locations", style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::addDeviceLocation, enabled = !locating) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.MyLocation, contentDescription = "Use current location")
            }
        }
        OutlinedTextField(
            value = query, onValueChange = vm::onQuery, singleLine = true, placeholder = { Text("Add a city or district") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4),
        )
        LazyColumn(contentPadding = PaddingValues(Space.s4)) {
            if (results.isNotEmpty()) {
                items(results.size) { i ->
                    val loc = results[i]
                    Column(Modifier.fillMaxWidth().clickable { vm.add(loc) }.padding(vertical = Space.s3)) {
                        Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        Text(loc.region ?: "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            } else {
                items(rows.size, key = { rows[it].location.id }) { i ->
                    val row = rows[i]
                    val cur = row.bundle?.data?.current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.cardGap)
                            .clip(MausamRadius.cardShape)
                            .background(if (row.isPrimary) cs.secondaryContainer else cs.surfaceContainer)
                            .clickable { vm.setPrimary(row.location.id) }
                            .padding(Space.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = row.isPrimary, onClick = { vm.setPrimary(row.location.id) })
                        Column(Modifier.weight(1f)) {
                            Text(row.location.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(listOfNotNull(row.location.region, cur?.condition?.label()).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        cur?.let {
                            Text(fmt.temp(it.temperatureC), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                            Spacer(Modifier.width(Space.s2))
                            MeteoconIcon(WeatherIcon.forCondition(it.condition, it.isDay), 40.dp, animated = false)
                        }
                        Column {
                            IconButton(onClick = { vm.move(row.location.id, -1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(onClick = { vm.move(row.location.id, 1) }, enabled = i < rows.size - 1, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down") }
                        }
                        IconButton(onClick = { vm.remove(row.location.id) }, enabled = rows.size > 1) { Icon(Icons.Rounded.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }
}
