package dev.mausam.home.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mausam.home.R
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.domain.i18n.tr
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.scene.AuroraBackdrop
import dev.mausam.home.ui.theme.Fluent
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.MausamRadius
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.onAccent

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

/** The symbol drawn inside each persona shape. */
fun personaIcon(p: Persona): ImageVector = when (p) {
    Persona.HEALTH -> Icons.Rounded.MonitorHeart
    Persona.FITNESS -> Icons.AutoMirrored.Rounded.DirectionsRun
    Persona.BEACH -> Icons.Rounded.Surfing
    Persona.TRAVEL -> Icons.Rounded.Flight
    Persona.PARENTS -> Icons.Rounded.FamilyRestroom
    Persona.AGRICULTURE -> Icons.Rounded.Agriculture
    Persona.COMMUTERS -> Icons.Rounded.Commute
    Persona.EVENTS -> Icons.Rounded.Celebration
    Persona.GENERAL -> Icons.Rounded.Explore
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
    val a11y = LocalMausamA11y.current
    val haze = remember { HazeState() }
    haze.blurEnabled = a11y.glassBlur
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(animated = a11y.sceneAnimated, modifier = Modifier.fillMaxSize().hazeSource(haze))
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedContent(
                step,
                transitionSpec = {
                    (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 3 } + fadeOut(effects))
                },
                label = "onboarding",
            ) { s ->
                when (s) {
                    OnboardingStep.LOCATION -> LocationStep(vm)
                    OnboardingStep.PERSONAS -> PersonaStep(vm, onDone, haze)
                }
            }
        }
    }
}

@Composable
private fun LocationStep(vm: OnboardingViewModel) {
    val locating by vm.locating.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true) vm.useDeviceLocation()
    }
    LocationStepContent(
        locating = locating, query = query, results = results, error = error,
        onQuery = vm::onQuery, onChoose = vm::choose,
        onUseLocation = { launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
    )
}

@Composable
fun LocationStepContent(
    locating: Boolean, query: String, results: List<dev.mausam.home.domain.model.Location>, error: String?,
    onQuery: (String) -> Unit, onChoose: (dev.mausam.home.domain.model.Location) -> Unit, onUseLocation: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = Space.s6)) {
        Spacer(Modifier.height(Space.s6))
        Image(painterResource(R.drawable.spot_location), contentDescription = null, modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1.2f).align(Alignment.CenterHorizontally))
        Text("Mausam", style = MaterialTheme.typography.displaySmallEmphasized, color = cs.onSurface)
        Text("Weather for the way you live.".tr(), style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Spacer(Modifier.height(Space.s4))
        Text("Mausam uses your location to show IMD warnings and nowcasts for your district.".tr(), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Spacer(Modifier.height(Space.s4))
        Button(onClick = onUseLocation, enabled = !locating, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
            else { Icon(Icons.Rounded.MyLocation, contentDescription = null); Spacer(Modifier.width(Space.s2)); Text("Use my location".tr(), style = MaterialTheme.typography.titleMedium) }
        }
        error?.let { Text(it.tr(), style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.padding(top = Space.s2)) }
        Spacer(Modifier.height(Space.s5))
        Text("Or search for a city".tr(), style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("City or district".tr()) }, modifier = Modifier.fillMaxWidth().padding(top = Space.s2),
            shape = MausamRadius.innerShape,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.s2)) {
            items(results.size) { i ->
                val loc = results[i]
                Column(Modifier.fillMaxWidth().clip(MausamRadius.chipShape).clickable { onChoose(loc) }.padding(vertical = Space.s3, horizontal = Space.s2)) {
                    Text(loc.name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    Text(loc.region ?: "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        Text("Your usage never leaves this phone.".tr(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = Space.s3))
    }
}

@Composable
private fun PersonaStep(vm: OnboardingViewModel, onDone: () -> Unit, haze: HazeState) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun finish(skip: Boolean) {
        vm.finish(skip) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            onDone()
        }
    }
    PersonaStepContent(selected = selected, onToggle = vm::toggle, onSkip = { finish(true) }, onContinue = { finish(false) }, haze = haze)
}

@Composable
fun PersonaStepContent(selected: Set<Persona>, onToggle: (Persona) -> Unit, onSkip: () -> Unit, onContinue: () -> Unit, haze: HazeState? = null) {
    val cs = MaterialTheme.colorScheme
    val a11y = LocalMausamA11y.current
    Column(Modifier.fillMaxSize().padding(horizontal = Space.s4)) {
        Spacer(Modifier.height(Space.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = Space.s2)) {
                Text("What matters to you?".tr(), style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.onSurface)
                Text("Pick any. Your home page is built from these.".tr(), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            Image(painterResource(R.drawable.spot_personas), contentDescription = null, modifier = Modifier.width(120.dp).height(100.dp))
        }
        Spacer(Modifier.height(Space.s3))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (a11y.largeText) 1 else 2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s3),
            contentPadding = PaddingValues(bottom = Space.s4),
        ) {
            itemsIndexed(Persona.pickable, key = { _, p -> p.key }) { i, p -> PersonaTile(p, p in selected, i, haze) { onToggle(p) } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s3), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip) { Text("Skip".tr()) }
            Button(onClick = onContinue, enabled = selected.isNotEmpty(), modifier = Modifier.height(52.dp)) { Text("Continue".tr(), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PersonaTile(persona: Persona, selected: Boolean, index: Int, haze: HazeState?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val a11y = LocalMausamA11y.current
    val accent = Fluent.forPersona(persona)
    val morph = remember(persona) { Morph(MaterialShapes.Circle, personaPolygon(persona)) }
    // The morph follows the spatial spring; the pop overshoots on purpose (expressive bounce).
    val p by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "morph")
    val pop by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "pop")
    val shape = remember(p) { MorphShape(morph, p) }
    val fill = lerp(accent.copy(alpha = 0.55f), accent, p)
    // Staggered entrance: 50 ms per tile, rising 20 dp.
    var shown by remember { mutableStateOf(a11y.reduceMotion) }
    LaunchedEffect(Unit) { if (!shown) { delay(60L * index); shown = true } }
    val enterA by animateFloatAsState(if (shown) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "enterA")
    val enterY by animateFloatAsState(if (shown) 0f else 20f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "enterY")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enterA; translationY = enterY * density }
            .mausamGlass(haze, GlassTier.CARD, MausamRadius.cardShape, tint = if (selected) lerp(cs.surfaceContainer, accent, 0.18f) else null, wash = accent.copy(alpha = if (selected) 0.35f else 0.12f))
            .clickable { haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); onClick() }
            .padding(Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.56f)
                .aspectRatio(1f)
                .graphicsLayer { scaleX = 1f + 0.08f * pop; scaleY = 1f + 0.08f * pop; rotationZ = -8f * pop }
                .clip(shape)
                .background(Brush.linearGradient(listOf(lerp(fill, androidx.compose.ui.graphics.Color.White, 0.18f), fill))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(personaIcon(persona), contentDescription = null, tint = onAccent(fill), modifier = Modifier.fillMaxWidth(0.42f).aspectRatio(1f).graphicsLayer { rotationZ = 8f * p })
        }
        Spacer(Modifier.height(Space.s3))
        Text(persona.title.tr(), style = if (selected) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleSmall, color = cs.onSurface, textAlign = TextAlign.Center)
        Text(persona.tagline.tr(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}
