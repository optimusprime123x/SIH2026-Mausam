package dev.mausam.home.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.mausam.home.R
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.ui.common.CardSkeletons
import dev.mausam.home.ui.detail.CardCatalogue
import dev.mausam.home.ui.detail.DetailSheet
import dev.mausam.home.ui.scene.AuroraBackdrop
import dev.mausam.home.ui.scene.WeatherScene
import dev.mausam.home.ui.theme.LocalMausamA11y
import dev.mausam.home.ui.theme.Space
import kotlinx.coroutines.flow.collectLatest

/** Every callback the home scaffold can fire, so the scaffold itself has no view-model dependency. */
data class HomeActions(
    val refresh: () -> Unit = {},
    val openCard: (String) -> Unit = {},
    val openWarnings: () -> Unit = {},
    val dismissBanner: () -> Unit = {},
    val openCatalogue: () -> Unit = {},
    val closeOverlay: () -> Unit = {},
    val togglePin: (String) -> Unit = {},
    val hide: (String) -> Unit = {},
    val moveToTop: (String) -> Unit = {},
    val setCardShown: (String, Boolean) -> Unit = { _, _ -> },
    val openLocations: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenLocations: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Re-rank on every home open, and refresh if the cache is getting old.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.onResume() }
        lifecycleOwner.lifecycle.addObserver(obs)
    }
    LaunchedEffect(Unit) { vm.refreshCompleted.collectLatest { haptics.performHapticFeedback(HapticFeedbackType.Confirm) } }
    val actions = remember(vm, onOpenLocations, onOpenSettings) {
        HomeActions(
            refresh = vm::refresh, openCard = vm::openCard, openWarnings = vm::openWarnings, dismissBanner = vm::dismissBanner,
            openCatalogue = vm::openCatalogue, closeOverlay = vm::closeOverlay, togglePin = { vm.togglePin(it) }, hide = { vm.hide(it) },
            moveToTop = { vm.moveToTop(it) }, setCardShown = { id, shown -> vm.setCardShown(id, shown) },
            openLocations = onOpenLocations, openSettings = onOpenSettings,
        )
    }
    HomeScaffold(state, overlay, actions)
}

/** The whole home page for a given state: backdrop, scene, banner, hero, cards, toolbar, overlays. */
@Composable
fun HomeScaffold(state: HomeUiState, overlay: HomeOverlay?, actions: HomeActions) {
    val a11y = LocalMausamA11y.current
    val haze = remember { HazeState() }
    haze.blurEnabled = a11y.glassBlur

    val density = LocalDensity.current
    val heroMaxPx = with(density) { 272.dp.toPx() }
    val heroMinPx = with(density) { 76.dp.toPx() }
    var heroPx by remember { mutableFloatStateOf(heroMaxPx) }
    var toolbarExpanded by remember { mutableStateOf(true) }
    val collapse = (1f - (heroPx - heroMinPx) / (heroMaxPx - heroMinPx)).coerceIn(0f, 1f)

    // One list state shared with the connection so "at the top" is known before the list scrolls.
    val listState = rememberLazyListState()
    val connection = remember(listState) {
        object : NestedScrollConnection {
            private fun listAtTop() = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) toolbarExpanded = false else if (available.y > 6f) toolbarExpanded = true
                // Scrolling up: the hero collapses before the list moves.
                if (available.y < 0 && heroPx > heroMinPx) {
                    val consumed = maxOf(available.y, heroMinPx - heroPx)
                    heroPx += consumed
                    return Offset(0f, consumed)
                }
                // Dragging down at the top: the hero expands first; pull-to-refresh only arms once it is open.
                if (available.y > 0 && heroPx < heroMaxPx && listAtTop()) {
                    val take = minOf(available.y, heroMaxPx - heroPx)
                    heroPx += take
                    return Offset(0f, take)
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
    val heroExpanded = heroPx >= heroMaxPx - 0.5f

    Box(Modifier.fillMaxSize()) {
        // Everything the glass samples lives in this one source layer.
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AuroraBackdrop(animated = a11y.sceneAnimated, modifier = Modifier.fillMaxSize())
            WeatherScene(
                spec = state.scene,
                animated = a11y.sceneAnimated,
                collapse = collapse,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .graphicsLayer {
                        translationY = -0.45f * (heroMaxPx - heroPx)
                        // Never fully gone: the collapsed glass bar still has sky to blur.
                        alpha = 1f - 0.4f * collapse
                    },
            )
        }

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = overlay,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "overlay",
            ) { current ->
                when (current) {
                    null -> HomeContent(
                        state = state, actions = actions, haze = haze, connection = connection, listState = listState,
                        heroPx = heroPx, collapse = collapse, toolbarExpanded = toolbarExpanded, refreshEnabled = heroExpanded,
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                    is HomeOverlay.Detail -> DetailSheet(
                        cardId = current.cardId, state = state, haze = haze,
                        animatedVisibilityScope = this@AnimatedContent, onDismiss = actions.closeOverlay,
                    )
                    HomeOverlay.Catalogue -> CardCatalogue(state = state, haze = haze, onToggle = actions.setCardShown, onDismiss = actions.closeOverlay)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.animation.SharedTransitionScope.HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    haze: HazeState,
    connection: NestedScrollConnection,
    listState: androidx.compose.foundation.lazy.LazyListState,
    heroPx: Float,
    collapse: Float,
    toolbarExpanded: Boolean,
    refreshEnabled: Boolean,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
) {
    val ptr = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(ptr.distanceFraction >= 1f) {
        if (ptr.distanceFraction >= 1f) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val a11y = LocalMausamA11y.current
    val cs = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(connection)) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            AnimatedVisibility(
                visible = state.banner != null,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(),
            ) {
                state.banner?.let { AlertBanner(it, haze, onClick = actions.openWarnings, onDismiss = actions.dismissBanner) }
            }
            Hero(state = state, heightPx = heroPx, collapse = collapse, haze = haze)
            // Pull-to-refresh arms only once the hero is fully open, so a drag never fights the expansion.
            Box(
                Modifier
                    .fillMaxSize()
                    .pullToRefresh(isRefreshing = state.isRefreshing, state = ptr, enabled = refreshEnabled, onRefresh = actions.refresh),
            ) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr, isRefreshing = state.isRefreshing, modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                    containerColor = cs.primaryContainer, color = cs.onPrimaryContainer,
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.screenMargin, end = Space.screenMargin, top = Space.listTopPadding, bottom = Space.listBottomPadding),
                ) {
                    if (state.isLoading) {
                        item { CardSkeletons() }
                    }
                    itemsIndexedKeyed(state.cards) { index, card ->
                        GlassCard(
                            card = card, index = index, haze = haze, freshness = state.freshness,
                            sourceInfo = card.spec.sourceLabel.takeIf { it.length >= 8 }?.let { l -> state.bundle?.sources?.values?.firstOrNull { it.label.contains(l.take(8), true) } },
                            animatedVisibilityScope = animatedVisibilityScope,
                            refract = index < 2 && a11y.refraction,
                            onTap = { actions.openCard(card.spec.id) },
                            onPin = { actions.togglePin(card.spec.id) },
                            onHide = { actions.hide(card.spec.id) },
                            onTop = { actions.moveToTop(card.spec.id) },
                            modifier = Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                        )
                        Spacer(Modifier.height(Space.cardGap))
                    }
                    if (!state.isLoading && state.cards.isEmpty()) {
                        item(key = "empty") {
                            Column(Modifier.fillMaxWidth().padding(top = Space.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(R.drawable.spot_empty_cards), contentDescription = null, modifier = Modifier.size(200.dp))
                                Text("No cards yet", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                                Text("Add some from the catalogue.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    item(key = "add") {
                        TextButton(onClick = actions.openCatalogue, modifier = Modifier.fillMaxWidth().padding(top = Space.s2)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(Space.s1))
                            Text("Add cards")
                        }
                        if (state.origin == CachedResult.Origin.SNAPSHOT) {
                            Row(Modifier.fillMaxWidth().padding(top = Space.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                                Image(painterResource(R.drawable.spot_offline), contentDescription = null, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.width(Space.s2))
                                Text("Showing bundled sample data until the network answers", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Weather data by Open-Meteo.com · IMD · NDMA SACHET · CPCB",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.s3),
                        )
                    }
                }
            }
        }

        HomeToolbar(
            expanded = toolbarExpanded,
            refreshing = state.isRefreshing,
            haze = haze,
            onRefresh = actions.refresh,
            onLocations = actions.openLocations,
            onCatalogue = actions.openCatalogue,
            onSettings = actions.openSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = Space.s4, bottom = Space.s4),
        )
    }
}

private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedKeyed(
    items: List<dev.mausam.home.domain.cards.RenderedCard>,
    crossinline content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, dev.mausam.home.domain.cards.RenderedCard) -> Unit,
) {
    items(count = items.size, key = { items[it].spec.id }) { i -> content(i, items[i]) }
}
