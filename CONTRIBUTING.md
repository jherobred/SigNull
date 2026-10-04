# Contributing to SigNull?

Thanks for helping. Bug reports with your phone model, Android version and carrier are as valuable as code.

## Setup

1. Install Android Studio (or JDK 17+ and the Android command-line tools).
2. Install Android SDK Platform 37.2.
3. Clone and open the project, or build from a terminal:

```bash
./gradlew assembleDebug
```

Signal readings need a real phone. The emulator reports simulated signal values.

## Project layout

```
app/src/main/java/app/signull/
  core/signal/    Cellular and Wi-Fi monitors, quality scale, band tables, interference rules
  core/sensors/   Orientation, steps, barometer, magnetometer, GPS
  core/angle/     Angle sweep model and guide math (pure Kotlin, unit tested)
  core/map/       Heatmap, polygons, GPS footprints (pure Kotlin, unit tested)
  data/           Room database, repositories, settings
  update/         GitHub release check, download, install
  ui/             Jetpack Compose screens, one package per screen
    components/   Shared widgets: gauge, morphing shapes, cards
    model3d/      Canvas 3D renderer used by the floor and building views
    streetmap/    MapLibre street map
    theme/        Colors, Google Sans type scale, motion springs
```

Dependencies are wired by hand in `AppContainer`. Screens use a `XxxDestination` composable that owns the ViewModel and a stateless `XxxScreen` (or layout) composable that tests can render.

## Before you open a pull request

```bash
./gradlew testDebugUnitTest lintDebug
```

- Keep logic that doesn't need Android in `core/` and cover it with a JVM unit test.
- If you change the UI, run `./gradlew recordRoborazziDebug` and include the updated images from `app/src/test/screenshots/`.
- Match the existing style: Kotlin official code style, Material 3 components, motion through the springs in `ui/theme/Motion.kt`.
- One topic per pull request.

## Changing the database

Bump the version in `SigNullDatabase`, add a migration (an `AutoMigration` when Room can work it out), and commit the generated schema JSON in `app/schemas/`. Never drop user data in a migration.

## Releasing (maintainers)

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts` and merge to `main`.
2. Optionally add release notes in `.github/release-notes/vX.Y.Z.md`. Without the file, GitHub generates notes from merged pull requests.
3. Tag and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow builds the APK, signs it with the release key from the repository secrets, and publishes it. Installed copies of SigNull? pick it up within 12 hours. The tag must match `versionName`, and only builds signed with the release key can update installed copies.
