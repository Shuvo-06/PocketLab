# PocketLab Architecture (Phases 1-3)

Single-activity app: `MainActivity` hosts a `NavHostFragment`; screens are Fragments
behind a bottom navigation bar. All UI uses XML layouts + ViewBinding.

## Packages (`com.pocketlab.app`)
| Package | Responsibility | Android deps? |
|---|---|---|
| `ui.*` | Fragments, adapters, ViewModels | yes |
| `ui.widget` | `GraphView` (custom Canvas view) | yes |
| `sensor` | `SensorHub` (SensorManager wrapper), `SensorType`, `SensorSample`, `SamplingRate`, `RateEstimator`, `ValueFormatter` | only SensorHub/SensorInfo/SensorType constants |
| `graph` | `SeriesBuffer`, `Marker`, `NiceScale`, `TimeFormat` | no (pure Java) |
| `recording` | `RecordingSession`, `RecordingState` | no (pure Java) |
| `analysis` | Phase 4 maths | no (pure Java, enforced by convention) |
| `data` | Phase 6+ persistence/cloud | yes |

Dependency rule: `ui -> recording/graph/sensor`; `recording -> graph, sensor`; `analysis -> nothing`.

## Sensor layer (Phase 2)
`SensorHub` (owned by `PocketLabApp`) detects sensors once and hands out `Subscription`s
(one `registerListener` each; always `cancel()`). `SensorSample` carries the sensor's own
monotonic timestamp. `SamplingRate` is a hint; `RateEstimator` measures the real rate.
Dashboard subscribes in `onStart`, cancels in `onStop`. Callbacks only store state; a 100 ms
ticker refreshes the UI so UI cost does not scale with sensor rate.

## Recording + graphing (Phase 3)
- `RecordingSession` (pure Java) is a small state machine: IDLE -> RECORDING <-> PAUSED -> STOPPED.
  Time axis = seconds since first sample, from sensor timestamps. Pausing drops samples and
  resuming closes the gap, so the graph continues seamlessly.
- Memory is bounded: each axis has a `SeriesBuffer` ring buffer (6000 points). The graph shows the
  newest points; `getTotalSamples()` counts everything. Phase 6 will stream every sample to Room.
- `RecorderViewModel` is Activity-scoped and owns the subscription, so a recording survives
  rotation and tab switches. `MainActivity.onStop` pauses an active recording when the app leaves
  the foreground (not on rotation) until the Phase 8 foreground service exists.
- `GraphView`: auto-scaling Y axis, "nice" ticks, multi-series legend, dashed event markers,
  pinch-zoom (time window 1-600 s), drag to pan, double-tap = live view. Dense data is reduced
  to a min/max envelope per pixel column, which keeps spikes and keeps drawing O(pixels).
- UI refresh: recorder ticker every 50 ms while visible.

## Decisions
- Java 11 / AGP 7.3-alpha / API 32 for Studio Dolphin Canary 8 (see README).
- `allowBackup=false` until Phase 9.
- Sensors declared `required="false"` so the app installs on any phone.
