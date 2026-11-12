# Contributing to Krelzox OS

Thanks for your interest in Krelzox OS. This guide explains how to set up the
project, report problems and propose changes. By taking part you agree to the
[Code of Conduct](CODE_OF_CONDUCT.md).

## Development environment

Requirements:

- **JDK 21** (the project compiles to Java 17 bytecode; CI uses Temurin 21).
- **Android SDK** with platform 37 (`compileSdk = 37`, `minSdk = 31`).
- **ADB**, and an Android TV device with SDK 31+ for device tests. The reference
  device is a Raspberry Pi 5 running LineageOS 24.0 (Android TV 17) — see
  [docs/dispositivo.md](docs/dispositivo.md).

Gradle and all dependencies are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and downloaded by the
Gradle wrapper on the first build; there is nothing else to install. Point
Gradle to the Android SDK with `local.properties` (`sdk.dir=...`) or the
`ANDROID_HOME` environment variable. A step-by-step Windows setup is in
[docs/entorno.md](docs/entorno.md) (Spanish).

## Build, run and test

```sh
./gradlew assembleDebug                    # debug APK (package dev.krelzox.launcher.debug)
./gradlew testDebugUnitTest lintDebug      # unit tests and lint — must pass
./gradlew assembleRelease                  # release build (R8 + Baseline Profile)
./gradlew :app:connectedDebugAndroidTest   # D-pad focus UI tests, needs a device
```

Install the debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
It uses its own package, so it never replaces the launcher in daily use.
Installing a release build and making Krelzox the home app is described in the
[README](README.md#getting-started). If a device gets stuck, follow the
[recovery guide](docs/recuperacion.md).

Device-only procedures (UI tests, Baseline Profile, benchmarks) are documented
in [docs/rendimiento.md](docs/rendimiento.md) (Spanish).

## Reporting bugs and proposing improvements

- Search the [existing issues](https://github.com/Nach0t/Krelzox-OS/issues)
  first.
- Use the **Bug report**, **Feature request** or **Question** issue template.
- For bugs, include the device, Android version, Krelzox version and steps to
  reproduce. Remove IP addresses, account names and other personal data from
  logs and screenshots before attaching them.
- **Never report security vulnerabilities in public issues** — follow the
  [security policy](SECURITY.md).

For larger changes, open an issue to discuss the idea before writing code.

## Code conventions

- **Kotlin**, official code style (`kotlin.code.style=official`), and Jetpack
  Compose for TV for the UI.
- Respect the module boundaries: `core:designsystem` holds UI tokens and
  components with no data dependencies; `core:data` holds data sources;
  `app` holds screens. See [docs/arquitectura.md](docs/arquitectura.md).
- Every screen must work with the D-pad alone and never lose focus.
- Show only real metrics; when a source is unavailable, say so instead of
  estimating.
- Do not add permissions, telemetry or new dependencies without discussing
  them in an issue first.
- User-facing strings go in resources, in English (`values/`) and Spanish
  (`values-es/`).
- Code, identifiers and commit messages are in **English**; detailed
  documentation in `docs/` is in Spanish.
- Add or update tests for behavior you change.

## Branches and commits

- Fork the repository and branch from `main`. Use short, descriptive branch
  names with a prefix: `feat/…`, `fix/…`, `docs/…`, `refactor/…`, `ci/…`.
- Write commit messages in English, in the imperative mood, with a summary
  line of about 72 characters or less (for example,
  `Fix focus loss when closing the favorites picker`). Add a body explaining
  *why* when the change is not obvious.
- Keep each commit focused on one change.
- Never commit signing keys, passwords, tokens, `local.properties` or device
  logs. Check `git status` before every commit.

## Pull requests

1. Make sure `./gradlew testDebugUnitTest lintDebug assembleRelease` passes
   locally.
2. If your change affects the UI or navigation, run the device UI tests and
   attach screenshots (without IP addresses or personal data).
3. Open the pull request against `main` and fill in the template.
4. CI must pass. The maintainer reviews the change and may ask for
   adjustments; changes that affect the device are also checked on the
   reference Raspberry Pi before merging.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the license of this project.
