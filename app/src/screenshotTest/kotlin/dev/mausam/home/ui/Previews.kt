package dev.mausam.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.Ranker
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.domain.model.SceneKind
import dev.mausam.home.domain.model.WarningSeverity
import dev.mausam.home.domain.personas.Persona
import dev.mausam.home.ui.home.HomeActions
import dev.mausam.home.ui.home.HomeOverlay
import dev.mausam.home.ui.home.HomeScaffold
import dev.mausam.home.ui.home.HomeUiState
import dev.mausam.home.ui.locations.LocationRow
import dev.mausam.home.ui.locations.LocationsContent
import dev.mausam.home.ui.onboarding.PersonaStepContent
import dev.mausam.home.ui.onboarding.LocationStepContent
import dev.mausam.home.ui.scene.SceneSpec
import dev.mausam.home.ui.settings.SettingsContent
import dev.mausam.home.ui.theme.MausamTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Rendered by the Compose screenshot plugin (`updateDebugScreenshotTest`), inspected as PNGs. */
private val personas = setOf(Persona.HEALTH, Persona.FITNESS, Persona.TRAVEL, Persona.GENERAL)
private val settings = UserSettings(personas = personas, wallpaperColours = false)

private fun homeState(kind: SceneKind, withBanner: Boolean): HomeUiState {
    val warnings = if (withBanner) listOf(PreviewFixtures.warning(PreviewFixtures.now, WarningSeverity.YELLOW, "Thunderstorm & lightning")) else emptyList()
    val bundle = PreviewFixtures.bundle(warnings = warnings)
    val ctx = CardContext(
        now = PreviewFixtures.now, location = bundle.location, bundle = bundle, distanceToCoastKm = 900.0,
        settings = settings, destinations = emptyList(),
    )
    val cards = Ranker.rank(CardRegistry.all, ctx, emptyMap(), emptyMap())
    return HomeUiState(
        location = bundle.location, bundle = bundle,
        freshness = Freshness.of(bundle.fetchedAt, PreviewFixtures.now.toInstant(), PreviewFixtures.zone),
        origin = CachedResult.Origin.NETWORK, cards = cards,
        banner = warnings.firstOrNull(), activeWarnings = warnings,
        scene = SceneSpec(kind = kind, sunProgress = 0.55f, windKph = 12f),
        isLoading = false, settings = settings, context = ctx,
    )
}

class DarkProvider : PreviewParameterProvider<Boolean> { override val values = sequenceOf(false, true) }

/** Previews share one process, so each pins the language it wants before composing. */
@Composable
private fun InLang(lang: dev.mausam.home.domain.i18n.Lang, content: @Composable () -> Unit) {
    dev.mausam.home.domain.i18n.L10n.lang = lang
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { dev.mausam.home.domain.i18n.L10n.lang = dev.mausam.home.domain.i18n.Lang.EN } }
    content()
}
private val hindiSettings = settings.copy(language = "hi")

@PreviewTest
@Preview(name = "home_clear", widthDp = 412, heightDp = 915)
@Composable
fun HomeClearPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) { HomeScaffold(homeState(SceneKind.CLEAR_DAY, withBanner = true), null, HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "home_rain", widthDp = 412, heightDp = 915)
@Composable
fun HomeRainPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) { HomeScaffold(homeState(SceneKind.RAIN, withBanner = false), null, HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "home_fog", widthDp = 412, heightDp = 915)
@Composable
fun HomeFogPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) { HomeScaffold(homeState(SceneKind.FOG, withBanner = false).let { it.copy(scene = it.scene.copy(sunProgress = 1.4f)) }, null, HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "home_night", widthDp = 412, heightDp = 915)
@Composable
fun HomeNightPreview() {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = true) { HomeScaffold(homeState(SceneKind.CLEAR_NIGHT, withBanner = false).let { it.copy(scene = it.scene.copy(sunProgress = 1.4f)) }, null, HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "home_detail", widthDp = 412, heightDp = 915)
@Composable
fun HomeDetailPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) { HomeScaffold(homeState(SceneKind.CLOUDY, withBanner = false), HomeOverlay.Detail(CardRegistry.aqi.id), HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "onboarding_personas", widthDp = 412, heightDp = 915)
@Composable
fun PersonasPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) {
            androidx.compose.foundation.layout.Box {
                dev.mausam.home.ui.scene.AuroraBackdrop(animated = false, modifier = androidx.compose.ui.Modifier.fillMaxSize())
                PersonaStepContent(selected = setOf(Persona.HEALTH, Persona.TRAVEL), onToggle = {}, onSkip = {}, onContinue = {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "onboarding_location", widthDp = 412, heightDp = 915)
@Composable
fun LocationPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) {
            androidx.compose.foundation.layout.Box {
                dev.mausam.home.ui.scene.AuroraBackdrop(animated = false, modifier = androidx.compose.ui.Modifier.fillMaxSize())
                LocationStepContent(locating = false, query = "", results = emptyList(), error = null, onQuery = {}, onChoose = {}, onUseLocation = {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "settings", widthDp = 412, heightDp = 1400)
@Composable
fun SettingsPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) { SettingsContent(s = settings, update = {}, previewBrief = {}, onBack = {}) }
    }
}

@PreviewTest
@Preview(name = "locations", widthDp = 412, heightDp = 915)
@Composable
fun LocationsPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        val b1 = PreviewFixtures.bundle()
        val b2 = PreviewFixtures.bundle(location = PreviewFixtures.mumbai)
        val rows = listOf(
            LocationRow(b1.location, CachedResult(b1, b1.fetchedAt, false, CachedResult.Origin.NETWORK), true),
            LocationRow(b2.location, CachedResult(b2, b2.fetchedAt, false, CachedResult.Origin.NETWORK), false),
        )
        MausamTheme(settings = settings, dark = dark) {
            LocationsContent(rows = rows, query = "", results = emptyList(), locating = false, onBack = {}, onQuery = {}, onAdd = {}, onDeviceLocation = {}, onSetPrimary = {}, onMove = { _, _ -> }, onRemove = {})
        }
    }
}

/** The collapsed hero bar and toolbar over the scene, with the software blur path active. */
@PreviewTest
@Preview(name = "hero_bar", widthDp = 412, heightDp = 360)
@Composable
fun HeroBarPreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.EN) {
        MausamTheme(settings = settings, dark = dark) {
            androidx.compose.runtime.CompositionLocalProvider(
                dev.mausam.home.ui.theme.LocalMausamA11y provides dev.mausam.home.ui.theme.MausamA11y(
                    largeText = false, reduceMotion = true, reduceTransparency = false, effectsEnabled = true, powerSave = false,
                ),
            ) {
                val state = homeState(SceneKind.CLEAR_DAY, withBanner = false)
                val aurora = dev.mausam.home.ui.scene.rememberAuroraRenderer(animated = false)
                val scene = dev.mausam.home.ui.scene.rememberSceneRenderer(state.scene, animated = false)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val sceneHeightPx = with(density) { 440.dp.toPx() }
                var rootSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                val sampler = androidx.compose.runtime.remember {
                    dev.mausam.home.ui.glass.BackdropSampler(
                        frame = { rootSize.height },
                        draw = {
                            val full = androidx.compose.ui.geometry.Size(rootSize.width.toFloat(), rootSize.height.toFloat())
                            with(aurora) { drawAurora(full) }
                            with(scene) { drawScene(androidx.compose.ui.geometry.Size(full.width, sceneHeightPx), 1f) }
                        },
                    )
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
                    dev.mausam.home.ui.scene.AuroraBackdrop(renderer = aurora, modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    dev.mausam.home.ui.scene.WeatherScene(renderer = scene, collapse = 1f, modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(440.dp))
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(32.dp))
                        dev.mausam.home.ui.home.Hero(
                            state = state, heightPx = with(density) { 76.dp.toPx() }, collapse = 1f,
                            haze = dev.chrisbanes.haze.HazeState(), sampler = sampler,
                        )
                    }
                    dev.mausam.home.ui.home.HomeToolbar(
                        expanded = true, refreshing = false, sampler = sampler, onRefresh = {}, onLocations = {}, onCatalogue = {}, onSettings = {},
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
                    )
                }
            }
        }
    }
}

@PreviewTest
@Preview(name = "hi_home_clear", widthDp = 412, heightDp = 915)
@Composable
fun HindiHomePreview(@PreviewParameter(DarkProvider::class) dark: Boolean) {
    InLang(dev.mausam.home.domain.i18n.Lang.HI) {
        MausamTheme(settings = hindiSettings, dark = dark) { HomeScaffold(homeState(SceneKind.FOG, withBanner = true).copy(settings = hindiSettings), null, HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "hi_home_detail", widthDp = 412, heightDp = 915)
@Composable
fun HindiDetailPreview() {
    InLang(dev.mausam.home.domain.i18n.Lang.HI) {
        MausamTheme(settings = hindiSettings, dark = false) { HomeScaffold(homeState(SceneKind.CLOUDY, withBanner = false).copy(settings = hindiSettings), HomeOverlay.Detail(CardRegistry.aqi.id), HomeActions()) }
    }
}

@PreviewTest
@Preview(name = "hi_settings", widthDp = 412, heightDp = 1400)
@Composable
fun HindiSettingsPreview() {
    InLang(dev.mausam.home.domain.i18n.Lang.HI) {
        MausamTheme(settings = hindiSettings, dark = true) { SettingsContent(s = hindiSettings, update = {}, previewBrief = {}, onBack = {}) }
    }
}

@PreviewTest
@Preview(name = "hi_onboarding", widthDp = 412, heightDp = 915)
@Composable
fun HindiPersonasPreview() {
    InLang(dev.mausam.home.domain.i18n.Lang.HI) {
        MausamTheme(settings = hindiSettings, dark = false) {
            androidx.compose.foundation.layout.Box {
                dev.mausam.home.ui.scene.AuroraBackdrop(animated = false, modifier = androidx.compose.ui.Modifier.fillMaxSize())
                PersonaStepContent(selected = setOf(Persona.HEALTH, Persona.TRAVEL), onToggle = {}, onSkip = {}, onContinue = {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "hi_locations", widthDp = 412, heightDp = 915)
@Composable
fun HindiLocationsPreview() {
    InLang(dev.mausam.home.domain.i18n.Lang.HI) {
        MausamTheme(settings = hindiSettings, dark = false) {
            val b1 = PreviewFixtures.bundle()
            val b2 = PreviewFixtures.bundle(location = PreviewFixtures.mumbai)
            val rows = listOf(
                LocationRow(b1.location, CachedResult(b1, b1.fetchedAt, false, CachedResult.Origin.NETWORK), true),
                LocationRow(b2.location, CachedResult(b2, b2.fetchedAt, false, CachedResult.Origin.NETWORK), false),
            )
            LocationsContent(rows = rows, query = "", results = emptyList(), locating = false, onBack = {}, onQuery = {}, onAdd = {}, onDeviceLocation = {}, onSetPrimary = {}, onMove = { _, _ -> }, onRemove = {})
        }
    }
}
