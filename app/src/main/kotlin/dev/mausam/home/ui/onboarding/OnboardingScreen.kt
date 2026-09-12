package dev.mausam.home.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.micaBase

/** One expressive shape per persona, distinguishable at a glance. */
fun personaPolygon(p: Persona): RoundedPolygon = when (p) {
    Persona.HEALTH -> MaterialShapes.Clover4Leaf
    Persona.FITNESS -> MaterialShapes.Burst
    Persona.BEACH -> MaterialShapes.Oval
    Persona.TRAVEL -> MaterialShapes.Arrow
    Persona.PARENTS -> MaterialShapes.Bun
    Persona.AGRICULTURE -> MaterialShapes.Flower
    Persona.COMMUTERS -> MaterialShapes.Pill
    Persona.EVENTS -> MaterialShapes.Cookie9Sided
    Persona.GENERAL -> MaterialShapes.Circle
}

/** A Compose Shape morphing between two polygons at a given progress. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f))
        val m = Matrix(); m.scale(size.width, size.height)
        path.transform(m)
        val b = path.getBounds()
        path.translate(androidx.compose.ui.geometry.Offset(size.width / 2 - b.center.x, size.height / 2 - b.center.y))
        return Outline.Generic(path)
    }
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(micaBase()).safeDrawingPadding()) {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { s ->
            when (s) {
                OnboardingStep.LOCATION -> LocationStep(vm)
                OnboardingStep.PERSONAS -> PersonaStep(vm, onDone)
            }
        }
    }
}

@Composable
private fun LocationStep(vm: OnboardingViewModel) {
    val cs = MaterialTheme.colorScheme
    val locating by vm.locating.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true) vm.useDeviceLocation()
    }
    Column(Modifier.fillMaxSize().padding(Space.s6)) {
        Spacer(Modifier.height(Space.s12))
        Text("Mausam", style = MaterialTheme.typography.displaySmallEmphasized, color = cs.onSurface)
        Text("Weather for the way you live.", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(Space.s8))
        Text("Mausam uses your location to show warnings and nowcasts for your district.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Spacer(Modifier.height(Space.s4))
        Button(
            onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
            enabled = !locating, modifier = Modifier.fillMaxWidth(),
        ) {
            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Allow location")
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.padding(top = Space.s2)) }
        Spacer(Modifier.height(Space.s6))
        Text("Or search for a city", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
        OutlinedTextField(
            value = query, onValueChange = vm::onQuery, singleLine = true,
            placeholder = { Text("City or district") }, modifier = Modifier.fillMaxWidth().padding(top = Space.s2),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.s2)) {
            items(results.size) { i ->
                val loc = results[i]
                Column(Modifier.fillMaxWidth().clickable { vm.choose(loc) }.padding(vertical = Space.s3)) {
                    Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    Text(loc.region ?: "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        Text("Your usage never leaves this phone.", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PersonaStep(vm: OnboardingViewModel, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val selected by vm.selected.collectAsStateWithLifecycle()
    val a11y = LocalMausamA11y.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun finish(skip: Boolean) {
        vm.finish(skip) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            onDone()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Space.s4)) {
        Spacer(Modifier.height(Space.s8))
        Text("What matters to you?", style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.onSurface, modifier = Modifier.padding(horizontal = Space.s2))
        Text("Pick any. Your home page is built from these.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.s2))
        Spacer(Modifier.height(Space.s4))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (a11y.largeText) 1 else 2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s3),
            contentPadding = PaddingValues(bottom = Space.s4),
        ) {
            items(Persona.pickable, key = { it.key }) { p -> PersonaTile(p, p in selected) { vm.toggle(p) } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s3), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { finish(skip = true) }) { Text("Skip") }
            Button(onClick = { finish(skip = false) }, enabled = selected.isNotEmpty()) { Text("Continue") }
        }
    }
}

@Composable
private fun PersonaTile(persona: Persona, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val morph = remember(persona) { Morph(MaterialShapes.Circle, personaPolygon(persona)) }
    val p by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "morph")
    val shape = remember(p) { MorphShape(morph, p) }
    val tint = if (selected) cs.secondaryContainer else cs.surfaceContainer
    val onTint = if (selected) cs.onSecondaryContainer else cs.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MausamRadius.cardShape)
            .background(cs.surfaceContainerLow)
            .clickable { haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); onClick() }
            .padding(Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f)
                .graphicsLayer { scaleX = 1f + 0.04f * p; scaleY = 1f + 0.04f * p }
                .clip(shape)
                .background(if (selected) cs.primaryContainer else tint),
        )
        Spacer(Modifier.height(Space.s3))
        Text(persona.title, style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, color = onTint, textAlign = TextAlign.Center)
        Text(persona.tagline, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}
