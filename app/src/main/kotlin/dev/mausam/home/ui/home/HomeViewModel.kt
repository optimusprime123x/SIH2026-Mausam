package dev.mausam.home.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mausam.home.AppGraph
import dev.mausam.home.domain.cards.CardContext
import dev.mausam.home.domain.cards.CardPref
import dev.mausam.home.domain.cards.CardAction
import dev.mausam.home.domain.cards.CardRegistry
import dev.mausam.home.domain.cards.CardUsage
import dev.mausam.home.domain.cards.Ranker
import dev.mausam.home.domain.cards.RenderedCard
import dev.mausam.home.domain.cards.UserSettings
import dev.mausam.home.domain.geo.Coastline
import dev.mausam.home.domain.model.CachedResult
import dev.mausam.home.domain.model.Freshness
import dev.mausam.home.domain.model.Location
import dev.mausam.home.domain.model.SceneKind
import dev.mausam.home.ui.scene.SceneSpec
import dev.mausam.home.ui.scene.sunProgress
import dev.mausam.home.domain.model.WeatherBundle
import dev.mausam.home.domain.model.WeatherWarning
import dev.mausam.home.work.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

data class HomeUiState(
    val location: Location? = null,
    val bundle: WeatherBundle? = null,
    val freshness: Freshness? = null,
    val origin: CachedResult.Origin? = null,
    val cards: List<RenderedCard> = emptyList(),
    val banner: WeatherWarning? = null,
    val activeWarnings: List<WeatherWarning> = emptyList(),
    val scene: SceneSpec = SceneSpec(),
    val isRefreshing: Boolean = false,
    /** The last refresh reached no source at all; cached data is still shown. */
    val refreshFailed: Boolean = false,
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val context: CardContext? = null,
    val prefs: Map<String, CardPref> = emptyMap(),
)

sealed interface HomeOverlay {
    data class Detail(val cardId: String) : HomeOverlay
    data object Catalogue : HomeOverlay
}

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val repo = graph.repository
    private val tick = MutableStateFlow(0L)
    private val refreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)
    private var fetch: Job? = null
    private val freezeOrder = MutableStateFlow(false)
    private var lastOrder: List<String> = emptyList()

    val overlay = MutableStateFlow<HomeOverlay?>(null)
    /** True when at least one source answered. */
    private val _refreshCompleted = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val refreshCompleted: SharedFlow<Boolean> = _refreshCompleted

    private val primary = combine(repo.locations, repo.primaryLocationId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    val state: StateFlow<HomeUiState> = combine(primary, repo.settings, repo.cardUsage, repo.cardPrefs, tick) { loc, settings, usage, prefs, _ ->
        Inputs(loc, settings, usage, prefs)
    }.flatMapLatest { inp ->
        val loc = inp.location ?: return@flatMapLatest flowOf(HomeUiState(isLoading = false, settings = inp.settings))
        combine(repo.bundle(loc), freezeOrder, repo.dismissedBanner) { cached, frozen, dismissed -> build(loc, inp, cached, frozen, dismissed) }
    }.combine(refreshing) { s, r -> s.copy(isRefreshing = r) }
        .combine(refreshFailed) { s, f -> s.copy(refreshFailed = f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class Inputs(val location: Location?, val settings: UserSettings, val usage: Map<String, CardUsage>, val prefs: Map<String, CardPref>)

    init {
        viewModelScope.launch {
            val loc = repo.primaryLocation() ?: return@launch
            val cached = repo.cachedBundle(loc)
            if (cached == null || cached.isStale) refresh()
        }
        consumePendingOpen()
    }

    /** A tapped alert notification, whether it launched the app or arrived while it was open. */
    private fun consumePendingOpen() {
        graph.pendingOpen?.let { open ->
            graph.pendingOpen = null
            if (open == Notifier.OPEN_WARNINGS) overlay.value = HomeOverlay.Detail(CardRegistry.warnings.id)
        }
    }

    private suspend fun build(loc: Location, inp: Inputs, cached: CachedResult<WeatherBundle>?, frozen: Boolean, dismissed: String?): HomeUiState {
        if (cached == null) return HomeUiState(location = loc, isLoading = true, settings = inp.settings)
        val now = ZonedDateTime.now(loc.zoneId)
        val others = repo.currentLocations().filter { it.id != loc.id }.mapNotNull { repo.cachedBundle(it)?.data }
        val ctx = CardContext(
            now = now, location = loc, bundle = cached.data,
            distanceToCoastKm = Coastline.distanceKm(loc.latitude, loc.longitude),
            settings = inp.settings, destinations = others,
        )
        val ranked = Ranker.rank(CardRegistry.all, ctx, inp.usage, inp.prefs)
        // After a refresh the previous order is held for 300 ms so the user sees the move.
        val ordered = if (frozen && lastOrder.isNotEmpty()) {
            ranked.sortedBy { lastOrder.indexOf(it.spec.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        } else ranked.also { lastOrder = it.map { c -> c.spec.id } }
        val active = cached.data.activeWarnings(now.toInstant())
        val cur = cached.data.current
        val today = cached.data.daily.firstOrNull()
        val scene = SceneSpec(
            kind = cur?.let { SceneKind.from(it.condition, it.isDay) } ?: SceneKind.CLEAR_DAY,
            sunProgress = sunProgress(now.hour * 60 + now.minute, today?.sunrise?.toSecondOfDay()?.div(60), today?.sunset?.toSecondOfDay()?.div(60)),
            windKph = cur?.windKph?.toFloat() ?: 0f,
            intensity = ((cur?.precipitationMm ?: 0.0) / 5.0).toFloat().coerceIn(0f, 1f),
        )
        return HomeUiState(
            location = loc, bundle = cached.data,
            freshness = Freshness.of(cached.fetchedAt, now.toInstant(), loc.zoneId), origin = cached.origin,
            cards = ordered, banner = active.firstOrNull()?.takeIf { it.id != dismissed }, activeWarnings = active,
            scene = scene,
            isLoading = false, settings = inp.settings, context = ctx, prefs = inp.prefs,
        )
    }

    fun onResume() {
        consumePendingOpen()
        tick.value = System.currentTimeMillis()
        viewModelScope.launch {
            val loc = repo.primaryLocation() ?: return@launch
            val cached = repo.cachedBundle(loc)
            if (cached != null && Duration.between(cached.fetchedAt, Instant.now()) > Duration.ofMinutes(20)) refresh()
        }
    }

    /**
     * Pull-to-refresh and the toolbar button both land here. The spinner is visual only: it shows
     * for at least 300 ms and at most [REFRESH_SPINNER_MS], while the fetch itself keeps its own
     * network timeouts and updates the page whenever it lands. A pull while an earlier fetch is
     * still in flight spins again and waits on that same fetch rather than starting a second one.
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            val loc = repo.primaryLocation() ?: return@launch
            refreshing.value = true
            freezeOrder.value = true
            val job = fetch?.takeIf { it.isActive } ?: viewModelScope.launch {
                refreshFailed.value = false
                val ok = runCatching { repo.refresh(loc) }.isSuccess
                refreshFailed.value = !ok
                graph.onDataRefreshed()
                _refreshCompleted.tryEmit(ok)
            }.also { fetch = it }
            try {
                withTimeoutOrNull(REFRESH_SPINNER_MS) { job.join(); delay(300) }
            } finally {
                freezeOrder.value = false
                refreshing.value = false
            }
        }
    }

    /** One-shot URIs for cards whose action is a deep link, most specific first; the screen tries each in turn. */
    private val _openUri = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    val openUri: SharedFlow<List<String>> = _openUri

    /** Tap recorded when the sheet closes: a re-rank under the opening morph cost a frame of layout. */
    private var pendingTap: String? = null

    fun openCard(cardId: String) {
        val action = CardRegistry.all.firstOrNull { it.id == cardId }?.action
        if (action is CardAction.DeepLink) {
            viewModelScope.launch { repo.recordTap(cardId) }
            val loc = state.value.location
            val lat = loc?.latitude?.let { String.format(java.util.Locale.ENGLISH, "%.5f", it) } ?: "0"
            val lon = loc?.longitude?.let { String.format(java.util.Locale.ENGLISH, "%.5f", it) } ?: "0"
            val primary = action.uri.replace("{lat}", lat).replace("{lon}", lon)
            _openUri.tryEmit(listOf(primary, "geo:$lat,$lon?z=13"))
        } else {
            pendingTap = cardId
            overlay.value = HomeOverlay.Detail(cardId)
        }
    }

    fun openWarnings() { overlay.value = HomeOverlay.Detail(CardRegistry.warnings.id) }
    fun dismissBanner() = viewModelScope.launch { repo.dismissBanner(state.value.banner?.id) }
    fun openCatalogue() { overlay.value = HomeOverlay.Catalogue }
    fun closeOverlay() {
        overlay.value = null
        pendingTap?.let { id -> pendingTap = null; viewModelScope.launch { repo.recordTap(id) } }
    }

    fun togglePin(cardId: String) = viewModelScope.launch {
        repo.updatePref(cardId) { it.copy(pinnedAt = if (it.pinnedAt == null) Instant.now() else null) }
    }

    fun hide(cardId: String) = viewModelScope.launch { repo.updatePref(cardId) { it.copy(hidden = true, pinnedAt = null) } }

    fun moveToTop(cardId: String) = viewModelScope.launch {
        repo.updatePref(cardId) { it.copy(boostUntil = Instant.now().plus(Duration.ofHours(24))) }
    }

    /** Catalogue toggle: shown cards are hidden; hidden or out-of-persona cards are added. */
    fun setCardShown(cardId: String, shown: Boolean) = viewModelScope.launch {
        repo.updatePref(cardId) { it.copy(hidden = !shown, added = shown) }
    }
}

/** Longest the refresh indicator stays on screen, whatever the network does. */
private const val REFRESH_SPINNER_MS = 5_000L
