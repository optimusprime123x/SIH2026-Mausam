# Data sources

The spec's IMD endpoints (`city.imd.gov.in/api/*.php`, `mausam.imd.gov.in/api/*.php`) answer
`401 … needs to be whitelisted` for every caller. IMD's newer `api.imd.gov.in` gateway needs a
registered account plus a server-held JWT, which cannot ship inside an APK. Neither is usable for
a public app, so the module reads IMD's data through the two open channels IMD itself publishes:

| Need | Primary | Fallback | Notes |
|---|---|---|---|
| District warnings (Day 1–5, colour) | **IMD GeoServer WFS** `imd:district_warnings_india`, CQL by district | NDMA SACHET | `DayN_Color` counts down: 1 red, 2 orange, 3 yellow, 4 green |
| Nowcast (next 3 h) | **IMD WFS** `imd:NowcastWarningDistrict` | SACHET, Open-Meteo hourly | `Color` counts up: 2 yellow, 3 orange, 4 red |
| Current observation | **IMD WFS** `imd:synop_data_layer` (station 42182 = Delhi Safdarjung) + `imd:metar_data_layer` for plain-text conditions and visibility | Open-Meteo `current` | IMD wins when the observation is under 3 h old |
| Hourly, 7-day, rain probability, UV, sunrise/sunset | **Open-Meteo** forecast (`past_days=7`) | on-device NOAA solar maths for sun times | CC-BY 4.0: "Weather data by Open-Meteo.com" is shown |
| AQI now | CPCB via data.gov.in (nearest station ≤ 60 km, recent) | Open-Meteo air-quality model (CAMS) | 24 h trend always comes from the model feed, converted to the Indian AQI scale |
| Sea state | Open-Meteo marine (point nudged offshore) | — | INCOIS exposes no public JSON |
| Rainfall today / past week | IMD station 24 h rainfall + Open-Meteo past-week sums | — | subdivision layer values are categories, not mm, so they are not used |
| Severe alerts across senders | **NDMA SACHET** `FetchAllAlertDetails` (IMD, INCOIS, SDMAs) | — | public domain; matched by district name or centroid within 40 km |
| Tides, agromet advisory, pollen | *pending* | — | shown as "data source being added", never fake numbers |

## Fallback chain

`network → Room cache → assets/snapshots/`. Every raw payload is cached per (source, location)
and re-assembled on read, so cache, network and snapshot go through the same normaliser
(`WeatherAssembler`). Snapshot-backed data is flagged (`CachedResult.Origin.SNAPSHOT`) and the
home shows "Showing bundled sample data". Snapshots were captured on 2026-09-12: national IMD
layers (all districts and stations), the SACHET list, and Open-Meteo forecast/AQI for 16 cities
plus marine for 5 coastal ones.

## Location resolution

`assets/stations.json` (generated from the WFS layers) maps any lat/lon to the nearest SYNOP
station id, METAR airport and district centroid (from IMD's own `india_districts` polygons),
offline. 696 of 756 warning-layer district names resolved; the rest fall back to the nearest
resolved district.

## Keys

Only CPCB needs one. The public data.gov.in sample key is compiled in by default and is
rate-limited; pass `-PcpcbApiKey=…` (or set `cpcbApiKey` in `~/.gradle/gradle.properties`) to
use a personal key. The app degrades to the Open-Meteo AQI model when CPCB fails.
