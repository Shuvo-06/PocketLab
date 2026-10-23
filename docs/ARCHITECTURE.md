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
