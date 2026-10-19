# PocketLab

Android scientific utility app: Java, XML + ViewBinding, Material 3, minSdk 26.

## Tooling (Android Studio Dolphin | 2021.3.1 Canary 8)
| Item | Version |
|---|---|
| Android Gradle Plugin | 7.3.0-alpha08 |
| Gradle | 7.4 |
| JDK | 11 (the one bundled with Studio) |
| compileSdk / targetSdk | 32 (Android 12L) |
| Material Components | 1.6.0 |

The proposal says Java 17. AGP 7.x only supports Java 11 sources/JDK 11, so the project uses
Java 11 language level. All code is plain Java and moves to 17 later by changing two lines
in `app/build.gradle` once you upgrade to AGP 8.

If Gradle sync cannot resolve AGP `7.3.0-alpha08`, use **Tools > AGP Upgrade Assistant**, or
fall back to AGP `7.1.3` (root `build.gradle`) with Gradle `7.2` (`gradle-wrapper.properties`).

## Open & run
1. **File > Open** and select this folder; wait for Gradle sync.
2. Install Android SDK Platform 32 if prompted. Run on a device or emulator.
3. Prefer a physical phone. Emulator sensors are simulated (Extended controls > Virtual sensors).

## Status
- [x] Phase 1: skeleton, navigation, theme, architecture docs
- [x] Phase 2: SensorHub, live dashboard, sampling rates, measured Hz
- [x] Phase 3: Canvas graph, free recorder (pause/resume/stop, markers, bounded buffer)
- [ ] Phase 4: scientific analysis engine

See `docs/`.
