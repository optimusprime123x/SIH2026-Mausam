package dev.mausam.home.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import dev.mausam.home.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.domain.i18n.trf
import dev.mausam.home.domain.model.Units
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.ui.common.GlassGroup
import dev.mausam.home.ui.common.HairlineDivider
import dev.mausam.home.ui.common.SectionHeader
import dev.mausam.home.ui.onboarding.personaIcon
import dev.mausam.home.ui.scene.AuroraBackdrop
import dev.mausam.home.ui.theme.Fluent
import dev.mausam.home.ui.theme.LocalIsDynamic
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.supportsDynamicColour
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsContent(s = s, update = { vm.update(it) }, previewBrief = { vm.previewBrief(it) }, onBack = onBack)
}

@Composable
fun SettingsContent(s: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit, previewBrief: (Boolean) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val a11y = LocalMausamA11y.current
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    var picking by remember { mutableStateOf<String?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val haze = remember { HazeState() }
    haze.blurEnabled = a11y.glassBlur

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(animated = a11y.sceneAnimated, modifier = Modifier.fillMaxSize().hazeSource(haze))
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back".tr()) }
                Text("Settings".tr(), style = MaterialTheme.typography.headlineSmallEmphasized, color = cs.onSurface)
            }
            Column(Modifier.padding(horizontal = Space.s4)) {
                SectionHeader("Personas".tr(), Icons.Rounded.Explore, Fluent.SkyBlue, "Cards on your home page come from these".tr())
                GlassGroup(haze, wash = Fluent.SkyBlue.copy(alpha = 0.16f)) {
                    FlowRow(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Persona.pickable.forEach { p ->
                            val on = p in s.personas
                            val accent = Fluent.forPersona(p)
                            FilterChip(
                                selected = on,
                                onClick = {
                                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                    update { it.copy(personas = (if (on) it.personas - p else it.personas + p) + Persona.GENERAL) }
                                },
                                leadingIcon = { Icon(personaIcon(p), contentDescription = null, tint = if (on) cs.onSecondaryContainer else accent, modifier = Modifier.size(18.dp)) },
                                label = { Text(p.title.tr()) },
                            )
                        }
                    }
                }

                SectionHeader("Appearance".tr(), Icons.Rounded.Palette, Fluent.Violet)
                GlassGroup(haze, wash = Fluent.Violet.copy(alpha = 0.16f)) {
                    val dynamicNow = LocalIsDynamic.current
                    SwitchRow(
                        "Wallpaper colours".tr(),
                        if (!supportsDynamicColour()) "Needs Android 12 or newer; using the IMD blue palette".tr()
                        else if (dynamicNow) "Material You palette from your wallpaper".tr() else "Off: IMD blue palette".tr(),
                        s.wallpaperColours, enabled = supportsDynamicColour(),
                    ) { on -> update { it.copy(wallpaperColours = on) } }
                    Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        listOf(cs.primary, cs.primaryContainer, cs.secondary, cs.secondaryContainer, cs.tertiary, cs.tertiaryContainer).forEach { c ->
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c))
                        }
                        Spacer(Modifier.width(Space.s1))
                        Text("Current palette".tr(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    HairlineDivider()
                    SwitchRow(
                        "Weather effects".tr(),
                        "Animated sky, clouds and rain · glass blur %s".trf(a11y.glassBlurReason?.let { "off (%s)".trf(it.tr()) } ?: "on".tr()),
                        s.effectsEnabled,
                    ) { on -> update { it.copy(effectsEnabled = on) } }
                    HairlineDivider()
                    SwitchRow("Large text".tr(), "Single column, bigger values, denser glass".tr(), s.largeText) { on -> update { it.copy(largeText = on) } }
                }

                SectionHeader("Daily briefs".tr(), Icons.Rounded.Notifications, Fluent.Amber, "A morning and evening note for your persona".tr())
                GlassGroup(haze, wash = Fluent.Amber.copy(alpha = 0.16f)) {
                    Image(painterResource(R.drawable.spot_brief), contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = Space.s2))
                    TimeRow("Morning brief".tr(), s.morningBrief, fmt) { picking = "morning" }
                    HairlineDivider()
                    TimeRow("Evening brief".tr(), s.eveningBrief, fmt) { picking = "evening" }
                    HairlineDivider()
                    Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        FilledTonalButton(onClick = { previewBrief(false) }) { Text("Preview morning".tr()) }
                        FilledTonalButton(onClick = { previewBrief(true) }) { Text("Preview evening".tr()) }
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.padding(horizontal = Space.s2)) { Text("Allow notifications".tr()) }
                    }
                }

                SectionHeader("Quiet hours".tr(), Icons.Rounded.Bedtime, Fluent.Indigo, "Briefs stay silent; orange and red alerts still come through".tr())
                GlassGroup(haze, wash = Fluent.Indigo.copy(alpha = 0.16f)) {
                    TimeRow("Start".tr(), s.quietStart, fmt) { picking = "quietStart" }
                    HairlineDivider()
                    TimeRow("End".tr(), s.quietEnd, fmt) { picking = "quietEnd" }
                }

                SectionHeader("Commute".tr(), Icons.Rounded.Commute, Fluent.Teal, "Leave-earlier nudges use this window".tr())
                GlassGroup(haze, wash = Fluent.Teal.copy(alpha = 0.16f)) {
                    TimeRow("Commute starts".tr(), s.commuteStart, fmt) { picking = "commuteStart" }
                    HairlineDivider()
                    TimeRow("Commute ends".tr(), s.commuteEnd, fmt) { picking = "commuteEnd" }
                }

                SectionHeader("Units".tr(), Icons.Rounded.Straighten, Fluent.Coral)
                GlassGroup(haze, wash = Fluent.Coral.copy(alpha = 0.16f)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(Space.s4)) {
                        Units.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = s.units == u, onClick = { update { it.copy(units = u) } }, shape = SegmentedButtonDefaults.itemShape(index = i, count = Units.entries.size)) {
                                Text(if (u == Units.METRIC) "°C · km/h" else "°F · mph")
                            }
                        }
                    }
                }

                SectionHeader("Language".tr(), Icons.Rounded.Language, Fluent.Mint)
                GlassGroup(haze, wash = Fluent.Mint.copy(alpha = 0.16f)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s3)) {
                        listOf("en" to "English", "hi" to "हिन्दी").forEachIndexed { i, (code, label) ->
                            SegmentedButton(selected = s.language == code, onClick = { update { it.copy(language = code) } }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)) { Text(label) }
                        }
                    }
                }

                SectionHeader("Privacy & credits".tr(), Icons.Rounded.Shield, Fluent.Green)
                GlassGroup(haze, wash = Fluent.Green.copy(alpha = 0.16f)) {
                    Column(Modifier.padding(Space.s4)) {
                        Text("Your usage never leaves this phone. Card ranking, taps and preferences are stored only on this device.".tr(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        Spacer(Modifier.height(Space.s3))
                        Text("Data: India Meteorological Department, NDMA SACHET, CPCB via data.gov.in, Weather data by Open-Meteo.com (CC BY 4.0). Icons: Meteocons by Bas Milius (MIT). Font: Roboto Flex (OFL).".tr(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Space.s12))
            }
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
                    update { it.apply(key, t) }
                    picking = null
                }) { Text("Set".tr()) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel".tr()) } },
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
private fun TimeRow(label: String, time: LocalTime, fmt: DateTimeFormatter, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        FilledTonalButton(onClick = onClick) { Text(fmt.format(time), style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
