# Architecture

Single-activity app. `MainActivity` hosts a Navigation `NavHostFragment`; screens are Fragments behind a
bottom bar (Sensors, Recorder, Experiments, History) plus pushed screens (experiment wizard, session
detail, cloud). UI uses XML layouts with ViewBinding and Material 3.

## Layers and packages (`com.pocketlab.app`)

| Package | Responsibility | Android classes? |
|---|---|---|
| `ui.*` | Fragments, adapters, ViewModels. No business logic | yes |
| `ui.widget` | `GraphView`, the custom Canvas graph | yes |
| `service` | `RecordingController` (owner of the live recording), `RecordingService` (foreground service), `BatteryEventReceiver` + `BatteryPolicy` | mostly (`BatteryPolicy` is pure) |
| `provider` | Read-only `SessionProvider`, `SessionContract`, `ProviderRoute`, `ProviderDemo` | mostly (`ProviderRoute` is pure) |
| `work` | WorkManager workers and `SyncScheduler` | yes |
| `cloud` | Auth (Firebase), Firestore store, sync engine/planner/mapper | `SyncEngine`, `SyncPlanner`, `CloudMapper` are **pure** |
| `net` | HTTP downloader, disk cache, updater | pure Java (JDK HTTP client) |
| `export` | CSV/JSON/XML writers, XML reader, importer, PDF report, `TransferManager` | writers/reader/importer pure; PDF and manager Android |
| `experiment` | JSON config model, parser, analyzers, repository | model/parser/analyzer pure |
| `recording` | `RecordingSession` state machine, `RecordingSummary` | pure |
| `graph` | `SeriesBuffer` ring buffer, `NiceScale`, `Marker`, `TimeFormat` | pure |
| `analysis` | Statistics, filters, peaks, period, regression, physics | pure, imports nothing from the app |
| `sensor` | `SensorHub` (SensorManager wrapper), `SensorType`, `SamplingRate`, `RateEstimator` | `SensorHub`/`SensorInfo` only |
| `data` | Room database, entities, DAOs, `SessionRecorder`, `SessionRepository`, recovery | Room parts yes; `SessionRecorder`/`SessionStore` pure |

**Dependency rule:** `ui → (service, work, export, experiment, data, recording, graph, sensor)`;
`cloud/export/experiment → analysis, graph, recording, data`; `analysis → nothing`.
Anything that can be pure Java is, so it is unit-tested on the JVM without a device.

## Key design decisions

1. **Interfaces at the Android boundary.** `SessionStore` (database), `SampleSource` (reading samples),
   `SyncEngine.LocalStore` / `CloudStore` hide Room and Firestore. The chunked writing, the export
   writers and the whole sync algorithm are tested with in-memory fakes.
2. **Time comes from sensor timestamps**, not from when callbacks run. Pausing closes the time gap, so
   graphs and period measurements stay correct.
3. **Bounded memory everywhere.** The graph keeps a ring buffer (6000 points per axis), the database
   receives every sample in 500-sample chunks on a writer thread, exports stream from the database in
   pages, and imports have hard limits (500,000 samples, 64 MB).
4. **The live recording belongs to the process, not the screen.** `RecordingController` (created in
   `PocketLabApp`) owns the session, so rotation, closing the screen or leaving the app does not lose
   it; `RecordingService` keeps the process alive and shows the notification.
5. **Every long job is off the UI thread:** one single-thread executor per concern (database reads,
   per-recording writer, export/import) and WorkManager for deferred sync.
6. **Defensive data handling.** Config JSON, imported XML and cloud documents are all validated; bad
   input is rejected with a readable message and never half-applied. A bad download never replaces the
   working experiment cache.

## Threading

| Work | Thread |
|---|---|
| Sensor callbacks, UI refresh tickers | main |
| Recording writes (`SessionRecorder`) | one single-thread executor per recording (session row first, chunks in order) |
| History reads, rename, delete, sync counts | repository's single-thread executor (so a read always sees earlier writes) |
| Export, import | `TransferManager` single-thread executor |
| Cloud sync, experiment update | WorkManager worker threads |
| ContentProvider queries | caller's binder thread |

## Main flows

**Recording:** `RecorderFragment` → `RecorderViewModel` → `RecordingController.start()` → subscribes to
`SensorHub`, creates `RecordingSession` + `SessionRecorder`, starts `RecordingService`. Samples flow
`SensorHub → controller → RecordingSession → (graph buffers, SessionRecorder → Room)`. Stop saves the
session and asks `SyncScheduler` to back it up soon (if automatic backup is on).

**Experiment:** `ExperimentWizardFragment` ↔ `ExperimentViewModel` (pages, inputs, run). On finish
`ExperimentAnalyzer` combines `analysis` building blocks; a successful run is saved with its result text.

**Sync:** `SyncWorker` → `CloudSync` → `SyncEngine(RoomLocalStore, FirestoreCloudStore)` with
`SyncPlanner` deciding upload / apply / restore per recording (see `ALGORITHMS.md`).

**Experiment definitions:** `ExperimentRepository` prefers the disk cache written by
`ExperimentUpdater`; if missing or invalid it falls back to `assets/experiments.json`.

## Accounts, ownership and data across devices

* **Login page** (`LoginActivity`) is shown by `MainActivity` while accounts are available and nobody is signed in
  (guest mode skips it). Firebase Authentication keeps the user signed in across restarts.
* **Ownership:** every recording row has an `ownerUid` ("" = recorded without an account). All queries (history, sync,
  ContentProvider, import) filter by the signed-in account, so accounts on the same phone are isolated. When a guest signs
  in, their recordings are claimed by the account.
* **Two Firebase databases, each for what it suits:** Cloud Firestore holds small documents (the profile and one summary per
  recording); the Realtime Database holds the bulk raw samples (500 per node) and the live "recording now" status.
* **Sync rules** (`SyncPlanner`/`SyncEngine`, pure and tested): a recording is uploaded as summary + raw samples the first
  time (samples never change, so a rename only re-uploads the summary); the summary is written **after** the samples, so a
  summary in the cloud always points at complete data; failed uploads clean up after themselves. Restoring downloads the
  samples first and stores session and samples in one transaction, so a failed restore leaves nothing half-done.
* **Live status:** the recording service publishes `status/{uid}` every 5 s with `onDisconnect().removeValue()`; the Cloud
  screen listens with a `ValueEventListener`, so another device shows it live and the entry disappears by itself if the
  recording phone loses its connection.

## Notifications

`Notifier` owns three channels (recording: silent and ongoing; alerts: heads-up; events: default) and builds every
`PendingIntent`. A tap opens `MainActivity` with an `AppDestination` extra (`launchMode="singleTop"`, handled in both
`onCreate` and `onNewIntent`, then removed so rotation cannot repeat it). Each destination has its own request code, otherwise
Android would reuse one PendingIntent and ignore the other extras. Notifications are posted from the foreground service and
from WorkManager workers, so they appear with the app closed. If the login page is in the way, the target is carried through it.
The user can switch the information notifications off in Profile; Android 13+ asks for the permission once at first start.

## REST API use

`WeatherApi` (Open-Meteo, JSON) runs on a background thread when a Relative-height run finishes with coordinates entered;
the analysis waits for it, and a failed call only adds a note to the result.
