package dev.mausam.home.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarHorizontalFabPosition
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.mausam.home.ui.common.CardSkeletons
import dev.mausam.home.ui.detail.CardCatalogue
import dev.mausam.home.ui.detail.DetailSheet
import dev.mausam.home.ui.glass.GlassTier
import dev.mausam.home.ui.glass.mausamGlass
import dev.mausam.home.ui.scene.WeatherScene
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.Space
import dev.mausam.home.ui.theme.micaBase
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenLocations: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val a11y = LocalMausamA11y.current
    val haptics = LocalHapticFeedback.current
    val haze = remember { HazeState() }
    haze.blurEnabled = a11y.glassBlur

    // Re-rank on every home open, and refresh if the cache is getting old.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.onResume() }
        lifecycleOwner.lifecycle.addObserver(obs)
    }
    LaunchedEffect(Unit) { vm.refreshCompleted.collectLatest { haptics.performHapticFeedback(HapticFeedbackType.Confirm) } }

    val density = LocalDensity.current
    val heroMaxPx = with(density) { 220.dp.toPx() }
    val heroMinPx = with(density) { 72.dp.toPx() }
    var heroPx by remember { mutableFloatStateOf(heroMaxPx) }
    var toolbarExpanded by remember { mutableStateOf(true) }
    val collapse = (1f - (heroPx - heroMinPx) / (heroMaxPx - heroMinPx)).coerceIn(0f, 1f)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) toolbarExpanded = false else if (available.y > 4f) toolbarExpanded = true
                if (available.y < 0 && heroPx > heroMinPx) {
                    val consumed = maxOf(available.y, heroMinPx - heroPx)
                    heroPx += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && heroPx < heroMaxPx) {
                    val take = minOf(available.y, heroMaxPx - heroPx)
                    heroPx += take
                    return Offset(0f, take)
                }
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(micaBase())) {
        WeatherScene(
            kind = state.scene, windKph = state.windKph, intensity = state.intensity,
            animated = a11y.sceneAnimated,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -0.4f * (heroMaxPx - heroPx)
                    alpha = (1f - collapse / 0.85f).coerceIn(0f, 1f)
                }
                .hazeSource(haze),
        )

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = overlay,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "overlay",
            ) { current ->
                when (current) {
                    null -> HomeContent(
                        state = state, vm = vm, haze = haze, connection = connection,
                        heroPx = heroPx, collapse = collapse, toolbarExpanded = toolbarExpanded,
                        animatedVisibilityScope = this@AnimatedContent,
                        onOpenLocations = onOpenLocations, onOpenSettings = onOpenSettings,
                    )
                    is HomeOverlay.Detail -> DetailSheet(
                        cardId = current.cardId, state = state, haze = haze,
                        animatedVisibilityScope = this@AnimatedContent, onDismiss = vm::closeOverlay,
                    )
                    HomeOverlay.Catalogue -> CardCatalogue(state = state, haze = haze, onToggle = vm::setCardShown, onDismiss = vm::closeOverlay)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.animation.SharedTransitionScope.HomeContent(
    state: HomeUiState,
    vm: HomeViewModel,
    haze: HazeState,
    connection: NestedScrollConnection,
    heroPx: Float,
    collapse: Float,
    toolbarExpanded: Boolean,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onOpenLocations: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val ptr = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(ptr.distanceFraction >= 1f) {
        if (ptr.distanceFraction >= 1f) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val a11y = LocalMausamA11y.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(connection)) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            AnimatedVisibility(
                visible = state.banner != null,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(),
                exit = fadeOut(),
            ) {
                state.banner?.let { AlertBanner(it, haze, onClick = vm::openWarnings) }
            }
            Hero(state = state, heightPx = heroPx, collapse = collapse, haze = haze)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = vm::refresh,
                state = ptr,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(state = ptr, isRefreshing = state.isRefreshing, modifier = Modifier.align(Alignment.TopCenter))
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.screenMargin, end = Space.screenMargin, top = Space.s2, bottom = Space.listBottomPadding),
                ) {
                    if (state.isLoading) {
                        item { CardSkeletons() }
                    }
                    itemsIndexedKeyed(state.cards) { index, card ->
                        GlassCard(
                            card = card, index = index, haze = haze, freshness = state.freshness,
                            sourceInfo = state.bundle?.sources?.values?.firstOrNull { it.label.contains(card.spec.sourceLabel.take(8), true) },
                            animatedVisibilityScope = animatedVisibilityScope,
                            refract = index < 2 && a11y.refraction,
                            onTap = { vm.openCard(card.spec.id) },
                            onPin = { vm.togglePin(card.spec.id) },
                            onHide = { vm.hide(card.spec.id) },
                            onTop = { vm.moveToTop(card.spec.id) },
                            modifier = Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                        )
                        Spacer(Modifier.height(Space.cardGap))
                    }
                    item(key = "add") {
                        TextButton(onClick = vm::openCatalogue, modifier = Modifier.fillMaxWidth().padding(top = Space.s2)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.padding(Space.s1))
                            Text("Add cards")
                        }
                        state.origin?.let { origin ->
                            if (origin == dev.mausam.home.domain.model.CachedResult.Origin.SNAPSHOT) {
                                Text(
                                    "Showing bundled sample data: no network yet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = Space.s2),
                                )
                            }
                        }
                        Text(
                            "Weather data by Open-Meteo.com · IMD · NDMA SACHET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.s2),
                        )
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(onClick = vm::refresh) {
                    if (state.isRefreshing) LoadingIndicator(Modifier.height(24.dp))
                    else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            },
            floatingActionButtonPosition = FloatingToolbarHorizontalFabPosition.End,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Transparent),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Space.s4)
                .mausamGlass(haze, GlassTier.TOOLBAR, CircleShape),
        ) {
            IconButton(onClick = onOpenLocations) { Icon(Icons.Rounded.Place, contentDescription = "Locations") }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
        }
    }
}

private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedKeyed(
    items: List<dev.mausam.home.domain.cards.RenderedCard>,
    crossinline content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, dev.mausam.home.domain.cards.RenderedCard) -> Unit,
) {
    items(count = items.size, key = { items[it].spec.id }) { i -> content(i, items[i]) }
}
