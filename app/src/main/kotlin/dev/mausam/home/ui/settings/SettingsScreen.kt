package dev.mausam.home.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.model.Units
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.micaBase
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s by vm.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    var picking by remember { mutableStateOf<String?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().background(micaBase()).safeDrawingPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
        }
        Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            Section("Personas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Persona.pickable.forEach { p ->
                    val on = p in s.personas
                    FilterChip(selected = on, onClick = {
                        haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        vm.update { it.copy(personas = (if (on) it.personas - p else it.personas + p) + Persona.GENERAL) }
                    }, label = { Text(p.title) })
                }
            }

            Section("Daily briefs")
            TimeRow("Morning brief", s.morningBrief, fmt) { picking = "morning" }
            TimeRow("Evening brief", s.eveningBrief, fmt) { picking = "evening" }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OutlinedButton(onClick = { vm.previewBrief(false) }) { Text("Preview morning brief") }
                OutlinedButton(onClick = { vm.previewBrief(true) }) { Text("Evening") }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            }

            Section("Quiet hours")
            Text("Briefs stay silent in this window. Orange and red alerts still come through.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            TimeRow("Start", s.quietStart, fmt) { picking = "quietStart" }
            TimeRow("End", s.quietEnd, fmt) { picking = "quietEnd" }

            Section("Commute")
            TimeRow("Commute starts", s.commuteStart, fmt) { picking = "commuteStart" }
            TimeRow("Commute ends", s.commuteEnd, fmt) { picking = "commuteEnd" }

            Section("Display")
            SwitchRow("Weather effects", "Ambient rain, rays and fog behind the cards", s.effectsEnabled) { on -> vm.update { it.copy(effectsEnabled = on) } }
            SwitchRow("Large text", "Single column, bigger values, denser glass", s.largeText) { on -> vm.update { it.copy(largeText = on) } }

            Section("Units")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Units.entries.forEachIndexed { i, u ->
                    SegmentedButton(selected = s.units == u, onClick = { vm.update { it.copy(units = u) } }, shape = SegmentedButtonDefaults.itemShape(index = i, count = Units.entries.size)) {
                        Text(if (u == Units.METRIC) "°C · km/h" else "°F · mph")
                    }
                }
            }

            Section("Language")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("en" to "English", "hi" to "हिन्दी").forEachIndexed { i, (code, label) ->
                    SegmentedButton(selected = s.language == code, onClick = { vm.update { it.copy(language = code) } }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)) { Text(label) }
                }
            }
            Text("Hindi copy is on the way; the app stays in English for now.", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)

            Section("Privacy")
            Text("Your usage never leaves this phone. Card ranking, taps and preferences are stored only on this device.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
            Spacer(Modifier.height(Space.s4))
            Text("Data: India Meteorological Department, NDMA SACHET, CPCB via data.gov.in, Weather data by Open-Meteo.com (CC BY 4.0). Icons: Meteocons by Bas Milius (MIT). Font: Roboto Flex (OFL).", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(Space.s12))
        }
    }

    picking?.let { key ->
        val initial = when (key) {
            "morning" -> s.morningBrief; "evening" -> s.eveningBrief; "quietStart" -> s.quietStart; "quietEnd" -> s.quietEnd
            "commuteStart" -> s.commuteStart; else -> s.commuteEnd
        }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    vm.update { it.apply(key, t) }
                    picking = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun UserSettings.apply(key: String, t: LocalTime): UserSettings = when (key) {
    "morning" -> copy(morningBrief = t); "evening" -> copy(eveningBrief = t)
    "quietStart" -> copy(quietStart = t); "quietEnd" -> copy(quietEnd = t)
    "commuteStart" -> copy(commuteStart = t); else -> copy(commuteEnd = t)
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.s4))
}

@Composable
private fun TimeRow(label: String, time: LocalTime, fmt: DateTimeFormatter, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onClick) { Text(fmt.format(time)) }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
