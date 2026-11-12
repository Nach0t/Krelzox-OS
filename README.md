# Krelzox OS

[![Android CI](https://github.com/Nach0t/Krelzox-OS/actions/workflows/android.yml/badge.svg)](https://github.com/Nach0t/Krelzox-OS/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/platform-Android%20TV%2017-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose%20for%20TV-7F52FF)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

**English** · [Español](README.es.md)

A premium Android TV experience for the Raspberry Pi 5. Its first component is
**Krelzox Launcher** (`dev.krelzox.launcher`): a home screen written in Kotlin and
Jetpack Compose for TV that installs as a regular app — no root, no ROM changes,
and the stock launcher always kept as a safety net.

*Formerly known as Nexium OS.*

![Krelzox home screen with the performance panel](docs/capturas/etapa5/01-inicio-panel.png)

## Features

**Home screen**
- Dynamic hero carousel: the app you were using (*Continue*), recent apps,
  favorites and suggestions.
- Favorites row with live reordering, plus an apps row; **Library** (all apps)
  and **Media** (video, audio and image apps) tabs.
- Built for the remote: every screen works with the D-pad, focus is never lost,
  and Back is hierarchical.

**Performance monitor**
- Always-on top-bar panel: CPU, RAM, temperature and free storage.
- Expanded view with live charts of the last 4 minutes (current, max, average).
- **System & performance** center in Settings: processor (usage, temperature,
  frequency, load, fan), memory, storage, system and network.
- Real values only, with their source on screen. If the system blocks a source,
  the metric says *Not available* — it is never estimated.

| Expanded panel | Performance center |
|---|---|
| ![Expanded performance panel](docs/capturas/etapa5/02-panel-ampliado.png) | ![Processor details](docs/capturas/etapa5/04-procesador.png) |

**Appearance and screensaver**
- Four themes — Ion (original), Aurora, Amber and Jade — that recolor the whole
  system at once: focus, brand mark, charts and screensaver. Each theme card
  previews its own focus before you pick it.
- Krelzox screensaver: time, date and the theme's light on deep graphite. It
  moves every minute to protect the screen and barely uses the CPU.
- A Raspberry Pi tile that tells you exactly where KonstaKANG's hardware
  settings are (Android does not let other apps open them directly).

| Appearance | Screensaver |
|---|---|
| ![Appearance in the Amber theme](docs/capturas/etapa7/04-apariencia-ambar.png) | ![Krelzox screensaver in the Aurora theme](docs/capturas/etapa7/08-salvapantallas-aurora.png) |

**Reliability**
- Runs as the HOME app, with **safe mode**: after 3 crashes in 2 minutes it
  opens a minimal screen to pick another launcher or open Settings.
- The stock launcher (Catapult) is never disabled; a
  [recovery guide](docs/recuperacion.md) covers every way back.

**Performance and privacy**
- Baseline Profile + R8: 2.8 MB release APK; D-pad navigation with < 1 % janky
  frames even right after install; cold start well under 600 ms.
- A single, normal permission (`ACCESS_NETWORK_STATE`) — themes and the
  screensaver add none. No root, no telemetry, no ads.

## Target platform

| | |
|---|---|
| Hardware | Raspberry Pi 5 Model B Rev 1.1, 16 GB RAM |
| System | LineageOS 24.0 (Android TV 17, SDK 37), KonstaKANG `rpi5_tv` build |
| Display | 1920×1080 @ 60 Hz, 320 dpi (960×540 dp) |
| GPU | Broadcom V3D (Mesa GLES 3.1 / Vulkan 1.4) |

The device has no Google Mobile Services. Krelzox runs on other Android TV
devices with SDK 31+, but it is designed and measured on this one.

## Getting started

Requirements: JDK 21, Android SDK (platform 37) and ADB access to the device.

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release-unsigned.apk
```

Sign the APK (for development, the local debug key is enough) and install it:

```sh
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --out krelzox.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r krelzox.apk
```

Make it the home app (fully reversible — see the [recovery guide](docs/recuperacion.md)):

```sh
adb shell cmd role add-role-holder android.app.role.HOME dev.krelzox.launcher
```

Windows equivalents are in [docs/entorno.md](docs/entorno.md) (Spanish).

## Development

| Command | What it does | Where |
|---|---|---|
| `./gradlew testDebugUnitTest lintDebug` | Unit tests and lint | CI + local |
| `./gradlew assembleRelease` | Release build (R8 + Baseline Profile) | CI + local |
| `./gradlew :app:connectedDebugAndroidTest` | D-pad focus UI tests | Device |
| `./gradlew :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` | Regenerates the Baseline Profile (the flag keeps Krelzox and its data installed) | Device |
| `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` | Cold-start Macrobenchmark | Device |

Debug builds use the package `dev.krelzox.launcher.debug`, so tests never replace
or uninstall the launcher in use. Device procedures and results:
[docs/rendimiento.md](docs/rendimiento.md).

CI (GitHub Actions) runs unit tests, lint, the release build and compiles the
device tests on every push and pull request, and posts a summary (tests, lint,
APK size, Baseline Profile). Dependabot proposes weekly dependency updates.

## Project structure

```
app/                 Launcher: activity, shell, Home / Library / Media / Settings, performance UI
core/designsystem/   Tokens, theme (Inter), icons, charts — no data dependencies
core/data/           Apps, favorites, recents, network, system metrics
baselineprofile/     Baseline Profile generator and startup benchmarks (runs on the device)
docs/                Architecture, visual identity, roadmap, ADRs (in Spanish)
```

## Documentation

Detailed documentation is written in **Spanish**:

- [Architecture](docs/arquitectura.md) · [Visual identity](docs/identidad-visual.md) · [Roadmap](docs/hoja-de-ruta.md)
- [Development environment](docs/entorno.md) · [Performance and device tests](docs/rendimiento.md)
- [Device recovery](docs/recuperacion.md) · [Reference device](docs/dispositivo.md)
- Architecture decision records: [docs/decisiones/](docs/decisiones/)

## Contributing

Contributions are welcome. Read the [contributing guide](CONTRIBUTING.md) and
the [Code of Conduct](CODE_OF_CONDUCT.md) before opening an issue or pull
request. Accessibility status and known limitations are described in
[ACCESSIBILITY.md](ACCESSIBILITY.md).

## Conventions

- Code, identifiers and commit messages: **English**.
- README: English and Spanish. Detailed documentation (`docs/`): **Spanish**.

## Security

To report a vulnerability, follow the [security policy](SECURITY.md) — never
use public issues.

Never commit signing keys (`*.jks`, `*.keystore`), passwords, tokens,
`local.properties` or device logs. `.gitignore` excludes them, but always check
`git status` before committing. Screenshots of the network screen show the
device's IP addresses and must not be published.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Includes the **Inter** typeface © The Inter Project Authors, under the SIL Open
Font License 1.1 ([third_party/inter/OFL.txt](third_party/inter/OFL.txt)).
