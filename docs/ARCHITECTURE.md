# Mausam Home: architecture

Personalised homepage module for the Mausam app (SIH26076). Single `:app` module today; the
`dev.mausam.home` package tree is laid out so it can be lifted into a library module unchanged.

## Toolchain

| Piece | Version | Why |
|---|---|---|
| AGP | 9.4.0 (built-in Kotlin, no `kotlin-android` plugin) | Compose 1.12 / material3 1.4 require AGP ≥ 9.1 and compileSdk 37 |
| Gradle | 9.6.0 | AGP 9.4 minimum |
| Kotlin | 2.4.20, KSP 2.3.12 | Current stable; matches the Compose compiler plugin |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | Spec says min 26 |
| Compose BOM | 2026.09.00 → ui 1.12.1; material3 pinned to **1.5.0-alpha27** | 1.4.0 ships only the tokens for FloatingToolbar / LoadingIndicator / MaterialShapes; the components need the 1.5 alpha (pairs with foundation 1.12) |
| Haze 1.7.3, Lottie 6.7.1, compose-shimmer 1.5.0 | | Glass, Meteocons, skeletons |
| Room 2.8.5, DataStore 1.2.1, WorkManager 2.11.2, Glance 1.2.0 | | Cache, prefs, background, widget |
| Retrofit 3.0.0 + kotlinx-serialization converter, OkHttp 4.12.0 | | Network |

Release builds are R8-minified and signed with the standard Android debug keystore so CI can
publish an installable APK without secrets. `.github/workflows/release-build.yml` runs unit
tests, builds `assembleRelease`, verifies the signature and uploads the APK as an artifact, on
pushes to `main` only.

Local build: `ANDROID_HOME=<sdk> ./gradlew :app:testDebugUnitTest assembleRelease`
(AGP 9 only generates unit-test tasks for the debug variant).

## Layers

```
ui.*            Compose. Reads HomeRepository + CardRegistry, never Retrofit/Room.
domain.*        Pure Kotlin, no Android imports. Models, cards, ranking, rules, briefs, solar.
data.*          Implements domain.HomeRepository: sources, Room cache, DataStore, snapshots.
work.*          WorkManager: 30-min refresh + alert poll, twice-daily briefs, boot re-arm.
widget.*        Glance widget, reads the cache only.
```

### Domain

* `domain.model.WeatherBundle` is the normalised, source-agnostic shape every provider maps into:
  current, hourly, daily, warnings, air quality, marine, rainfall, advisory, plus a
  `sources: Map<DataKind, SourceInfo>` so each card can print an honest source line.
* `domain.cards.CardSpec(id, persona, title, sourceLabel, detail, action, wide, gate, fetch)`.
  `fetch` is a pure function of `CardContext` (now, location, bundle, distance to coast,
  settings, destinations), so cards render instantly from cache and are trivially unit-tested.
  `CardValue` is `Ready | Pending | Unavailable`; `Pending` never carries a number.
* `CardRegistry.all` lists every card (30 today). Adding a card = adding one spec.
* `Ranker`: score = persona match (10, general 6) + decayed taps (7-day half-life) + time-of-day
  boost (fitness/commute 5–9 am weekdays, event/travel weekends, gardening evenings) + urgency
  (danger 5, warning 3, caution 1) + "move to top" temporary boost. Gates drop cards that make no
  sense here and now (beach ≤ 30 km from the coast via a bundled coastline polyline, frost only
  when a forecast minimum < 5 °C, fog only when visibility < 4 km or a fog warning exists).
  Pinned first, hidden never. Everything on device.
* `RuleEngine`: heat index (NOAA Rothfusz), humidex (Environment Canada), dew point (Magnus),
  UV estimate (Madronich 2007 clear-sky formula × EPA cloud factors, always labelled estimate),
  run-window score, best-day score, packing tips, commute/school-run rain windows.
* `Solar`: NOAA sunrise/sunset/elevation, computed on device.
* `IndianAqi`: CPCB sub-index breakpoints for PM2.5 / PM10 when a provider gives raw
  concentrations instead of the Indian AQI.
* `BriefComposer`: persona-shaped copy for the two daily notifications; `AlertPolicy`: push only
  new orange/red, never yellow.

### Data

`HomeRepository` (domain interface) is the only door. `HomeRepositoryImpl` fetches each source
in parallel, stores the raw payload per (source, location) in Room, and `WeatherAssembler`
re-assembles the normalised bundle on every read. The source-per-card matrix, the IMD colour-code
findings and the fallback chain are in `docs/DATA_SOURCES.md`.

Rules that hold regardless of provider:

* Every response is cached in Room per location; the UI opens from cache instantly, refreshes in
  the background, and cards crossfade in place.
* `CachedResult<T>(data, fetchedAt, isStale, origin)`; `Freshness` flips to
  "offline, last updated HH:MM" after two hours.
* On any network failure the repository falls back to bundled snapshots under
  `assets/snapshots/` so a demo cannot die on a bad endpoint.
* A `Pending` card is the only honest answer for a source that is not wired; no fake numbers.

### Work

* `RefreshWorker`: periodic 30 min (network constraint). Refreshes every saved location, then
  polls the primary location's warnings and pushes new orange/red ones (bypasses quiet hours).
* `BriefWorker`: one-shot at the morning/evening time from settings, respects quiet hours,
  refreshes best-effort, composes the brief for the first selected persona, re-arms itself.
* `BootReceiver` re-arms the brief timers after reboot. WorkManager is initialised on demand by
  `MausamApp` (`Configuration.Provider`).

## Design system (decisions)

Three languages, one layer each:

* **Structure and components: Material 3 Expressive** (material3 1.5.0-alpha27): `MaterialExpressiveTheme`,
  `MotionScheme.expressive()`, `HorizontalFloatingToolbar`, `LoadingIndicator`, `ButtonGroup`,
  `MaterialShapes` for persona tiles, Roboto Flex variable font.
* **Surfaces: Liquid Glass with the Fluent acrylic recipe** via Haze: backdrop blur 24–36 dp,
  surface tint 50–90 % by tier, noise 2–4 %, 1 dp specular gradient stroke (white 45 % top-left → 0),
  AGSL refraction only on API 33+. Concentric corners 28 / 20 / 12 dp. Everything the glass
  samples sits in one Haze source: the aurora backdrop (three drifting blobs in the theme's
  primary, tertiary and secondary containers) plus the hero scene. Cards, the banner, the
  toolbar pill, the collapsed hero bar, the hero chips, the sheet and the persona tiles are glass.
* **Depth and layout: Fluent 2**: four tiers (scene 0, cards 1, toolbar+banner 2, sheet 3),
  soft coloured shadows, 4 dp grid, 16 dp margins, 12 dp gaps, mica-style tinted base.

Two corrections to the spec, both applied: the expressive springs are the library's own
(spatial 0.8/380, fast 0.6/800, slow 0.8/200; effects 1.0/1600, 3800, 800), never the spec's
700/1400, so `MaterialTheme.motionScheme` is always used; and the glass tint floors at 0.72
only over the raw scene (hero bar), while cards over the soft aurora run at 0.52–0.60 because
the backdrop itself is low-contrast (large-text mode adds 0.20 everywhere).

**Colour.** A Fluent-inspired accent per card and per persona (`ui.theme.Accents`), nudged
12 % toward the theme primary so a wallpaper palette still ties the page together. Each card
carries its accent as a top-left wash on the glass and an icon disc; tone (good / caution /
watch / danger) is a small chip, never the whole card.

**Icons.** Meteocons (animated Lottie) for weather; Material symbols for activities and
services Meteocons has no picture for (running, pollen, tides, school run, agromet, traffic,
commute, comfort, packing, air). `WeatherIcon.symbol` marks the latter; `ui.common.symbolFor`
also supplies the static stand-in used in previews and while a Lottie file loads. Pending and
empty cards show the icon of what they are about, never a generic "not available" glyph.

**Scene.** `ui.scene.WeatherScene` paints a time-of-day sky (warm near sunrise and sunset),
a vector sun or moon on an arc, drifting vector clouds (storm variants for rain), rolling hills
and an Indian skyline in front, then rain, stars, fog bands or lightning. Layers move at
different rates with the hero collapse. The canvas fills its box; gradients never end in
`Color.Transparent` (black at zero alpha), always the same hue at zero alpha.

**Motion.** Card stagger-in, press scale with rim rotation, digit roll on value change,
`animateContentSize`, card→sheet morph with `scaleToBounds` (no remeasure mid-flight), chart
bars that grow in, a bobbing hero icon and breathing sun, bouncy persona selection
(spring 0.45/420), slide-and-fade route transitions, a toolbar pill that shrinks into the FAB.

The card→sheet morph is an in-window sheet (`ui.detail.DetailSheet`), not `ModalBottomSheet`,
because a dialog window can neither share elements nor sample the scene for blur.
