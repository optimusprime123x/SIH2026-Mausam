# Mausam Home

Personalised homepage module for the Mausam app (SIH26076, MoES / IMD). Persona-based cards,
ranked on device by usage and context, offline-first, with Material 3 Expressive structure,
Liquid Glass surfaces and Fluent 2 depth.

## Build

```
export ANDROID_HOME=<sdk with platforms;android-37.0 and build-tools;37.0.0>
./gradlew :app:testDebugUnitTest assembleRelease
```

Release builds are signed with the standard Android debug keystore so the CI artifact installs
anywhere. `.github/workflows/release-build.yml` builds and uploads the APK on every push to `main`.

## Layout

* `docs/ARCHITECTURE.md` — layers, ranking, motion and glass decisions.
* `docs/DATA_SOURCES.md` — why the spec's IMD endpoints are unusable and what replaces them.
* `app/src/main/kotlin/dev/mausam/home/` — `domain` (pure Kotlin), `data`, `work`, `widget`, `ui`.

## Credits

India Meteorological Department (GeoServer WFS), NDMA SACHET (public domain), CPCB via
data.gov.in, Weather data by Open-Meteo.com (CC BY 4.0), Meteocons by Bas Milius (MIT), Roboto Flex
(OFL 1.1), Haze (Apache-2.0), rounded-rect refraction shader from Kyant0/AndroidLiquidGlass
(Apache-2.0).
