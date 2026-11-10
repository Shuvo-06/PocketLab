# PocketLab

**A smart Android scientific utility app.** PocketLab turns a phone into a portable physics lab: it
reads the built-in sensors, draws them live, guides the user through experiments, analyses the data,
stores every session, exports lab reports, records in the background, and can back summaries up to the
cloud.

Java · XML layouts + ViewBinding · Material Design 3 · Room (SQLite) · WorkManager · Firebase (optional)

| | |
|---|---|
| Version | 1.1.0 (all 10 phases of the proposal, plus accounts, notifications and Realtime Database) |
| Min / target / compile SDK | 26 / 32 / 32 |
| Built for | Android Studio Dolphin \| 2021.3.1 Canary 8 |

## What it does

| Area | Features |
|---|---|
| Sensors | Live dashboard for accelerometer, gyroscope, magnetometer, light and barometer; availability, units, hardware info, selectable sampling rate, **measured** rate from sensor timestamps |
| Recording | Free recorder with pause/resume/stop and event markers; custom Canvas graph (auto-scale, pinch-zoom, pan, markers); bounded graph buffer, **every sample saved** to the database |
| Analysis | Mean, standard deviation, low-pass filter, peak detection, period (zero-crossing with hysteresis), linear regression, percentage error |
| Experiments | JSON-driven wizard with four experiments: pendulum (g), magnetic field, light level, relative height; input validation; result + interpretation + uncertainty |
| History | Searchable list, detail view, rename, swipe-to-delete with undo, recovery of interrupted recordings |
| Export / import | CSV, JSON, XML, PDF lab report; share sheet through FileProvider; safe XML import |
| Accounts and profile | Login page (email/password, create account, password reset, guest mode), profile with editable name, per-account data on the phone |
| Notifications | Recording (with Stop), recording saved, backup finished/problem, new experiments, battery warnings; they work with the app closed and open the right screen when tapped |
| Offline mode | A banner stays on top while the phone is offline; recordings and experiment results are saved on the phone and queued, then uploaded to the cloud automatically when the connection returns |
| System components | Foreground service, battery-low BroadcastReceiver, read-only ContentProvider (+ demo) |
| Network / cloud | Firebase Authentication; Cloud Firestore (profile + recording summaries); **Realtime Database** (raw measurements across devices + live "recording now" status); WorkManager background sync with conflict resolution; experiment definitions over HTTPS (ETag, cache, bundled fallback); Open-Meteo REST API for altitude |

Results are **estimates** from phone sensors, not laboratory-grade measurements.

## Build and run

1. Android Studio Dolphin Canary 8: **File > Open** this folder, wait for Gradle sync
   (or run `./gradlew assembleDebug`; the Gradle 7.4 wrapper is included).
2. Install Android SDK Platform 32 if asked. Run on a **physical phone** if you can (emulator sensors are
   simulated: Extended controls > Virtual sensors).
3. The app works fully **without** Firebase (guest mode). To turn on accounts and data across devices, follow `docs/FIREBASE_SETUP.md`.

Tooling: AGP 7.3.0-alpha08, Gradle 7.4, JDK 11, Java 11 language level (AGP 7 does not support Java 17).
If sync cannot resolve the AGP version: Tools > AGP Upgrade Assistant, or use AGP 7.1.3 + Gradle 7.2.

## Tests

* `./gradlew testDebugUnitTest`: JVM unit tests for all the pure logic (see `docs/TESTING.md`).
* `./gradlew connectedDebugAndroidTest`: instrumented tests (Room, ContentProvider, recovery, UI smoke); needs a device or emulator.

## Documentation (`docs/`)

| File | Contents |
|---|---|
| `ARCHITECTURE.md` | Layers, packages, threading, data flows |
| `DATABASE.md` | Room schema, relationships, migration |
| `ALGORITHMS.md` | Every algorithm with its parameters and limits |
| `ANDROID_COMPONENTS.md` | Syllabus coverage matrix: concept → where it is in the code |
| `TESTING.md` | Test strategy, results, manual checklist, known gaps |
| `DEMO.md` | Demo script, screenshot list, report and presentation outline |
| `FEATURES.md` | Every feature and how it is implemented, in plain language |
| `FIREBASE_SETUP.md` | Short checklist: what to add in the Firebase console and what not to add to Gradle |
| `CLOUD_SETUP.md` | Firebase and experiment-server setup, security rules |
| `REQUIREMENTS.md` | Requirements and sensor limitations |
| `TOPIC_COVERAGE.md` | Checklist: Realtime Database, Firestore, Authentication, Intents, Notifications, JSON, APIs/components |
