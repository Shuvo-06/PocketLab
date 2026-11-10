# Algorithms

All in pure Java unless noted; each has unit tests (see `TESTING.md`).

## Measurement
* **Measured sampling rate**: `RateEstimator`: sliding window of 50 sensor timestamps, `(n-1) / (t_last - t_first)`.
  Android's requested rate is only a hint, so the real rate is always shown.
* **Recording time axis**: seconds since the first sample from sensor timestamps. On resume the paused gap is
  removed: `gap += ts_first_after_resume - ts_last_before_pause`.
* **Graph buffer**: `SeriesBuffer` ring buffer; oldest point is overwritten when full; `lowerBound(t)` is a binary
  search over the ring.

## Statistics (`Stats`, `RunningStats`)
* mean, sample variance (n-1, two-pass), population std-dev, min, max, median, RMS.
* `RunningStats` uses **Welford's algorithm** (one pass, numerically stable) so exports/backups can compute exact
  statistics over hundreds of thousands of samples without holding them in memory.

## Filtering (`LowPassFilter`)
First-order IIR with the **real time step** of each sample (works with uneven Android timestamps):
`α = dt / (RC + dt)`, `RC = 1 / (2π f_c)`, `y_i = y_{i-1} + α (x_i - y_{i-1})`.
`filterZeroPhase` runs it forward and backward to cancel the lag.

## Peak detection (`PeakDetector`)
Local maxima (`>` left, `>=` right so a flat top counts once), kept only if **prominence ≥ threshold**
(height above the higher of the two surrounding valleys), then thinned by a **minimum separation** (the higher
peak wins).

## Period estimation (`PeriodEstimator`)
* **Zero crossings** (used by the pendulum): subtract the mean, find rising crossings with **linear interpolation**
  (sub-sample accuracy) and a **hysteresis band** `±h` (the signal must go below `-h` then above `+h`) so noise
  cannot create false cycles. Period = mean of successive crossing intervals; spread = their std-dev.
* **Peaks**: spacing of detected peaks (resolution limited to one sample).

## Regression and error
* `LinearRegression`: least squares slope/intercept, R² (`1 - SS_res/SS_tot`), standard error of the slope.
* `ErrorMetrics.percentageError = |measured - reference| / |reference| × 100` (NaN for reference 0).

## Experiments (`ExperimentAnalyzer`)
| Experiment | Method |
|---|---|
| Pendulum | X axis → trim start/end → zero-phase low-pass (3 Hz) → zero-crossing period (hysteresis = 25 % of σ) → `g = 4π²L/T²`; uncertainty `δg = g·2·(σ_T/√n)/T`; % error vs 9.80665 m/s² |
| Magnetic field | magnitude `√(x²+y²+z²)`; baseline = mean of first 5 s; peak/minimum/largest change; Earth-field range check (25–65 µT); magnet detected if peak > 2× baseline |
| Light | mean/min/max/σ, matched to a table of typical lighting ranges; warns if σ/mean > 20 % |
| Relative height | mean pressure of first and last 6 s → barometric formula `h = 44330 (1 - (p/p0)^0.190295)` → height change; optional comparison with a known value |

## Export / import
* **Streaming writers** (CSV/JSON/XML): samples are read in pages of 1000 and written as they are read; summary
  statistics come from a first pass with `RunningStats`.
* **PDF graph**: evenly thinned to about 1200 points.
* **XML import** (SAX, streaming): validates root, version, sensor, rate, finite numbers, non-decreasing time,
  declared vs actual sample count, size and count limits; DOCTYPE is rejected (XML entity attacks).

## Cloud sync (`SyncPlanner`)
Each side stores `syncedAt` (the change time last exchanged).
`localChanged = modifiedAt > syncedAt`, `remoteChanged = remote.modifiedAt > syncedAt`.

| localChanged | remoteChanged | Action |
|---|---|---|
| no | no | nothing |
| yes | no | upload |
| no | yes | apply the cloud's name |
| yes | yes | **conflict**: the newer `modifiedAt` wins (tie → local) |

Never backed up, or cloud copy missing → **full upload**: raw samples first (Realtime Database, 500 per node), then the
summary (Firestore) with the number of chunks, so a summary always refers to complete data; failures delete what was
uploaded. A later rename uploads only the summary (samples never change). Cloud documents unknown locally → restore: the
samples are downloaded **before** anything is stored locally and then inserted with the session in one transaction (up to
500,000 samples; larger recordings restore as a summary only).
Progress is saved per recording, so a failure halfway is simply continued by the next run.
Clock differences between devices can affect "newer"; this is accepted for summary metadata.

## Battery policy (`BatteryPolicy`)
Warn once at ≤ 15 %, stop and save at ≤ 5 %, never act while charging, re-arm the warning after recovering above 20 %.

## Network
Conditional GET with `If-None-Match`; the downloaded file is parsed with the app's own parser **before** it
replaces the cache. Only `https://` is accepted, 10 s timeouts, 1 MB limit.

## Altitude above sea level (Relative height, optional)
With a sea-level pressure `p0` from the weather API: `h = 44330 · (1 − (p / p0)^0.190295)` for the start and end pressures.
`p0` is validated (850–1090 hPa) before use; if the call fails the result simply omits the altitude lines.

## Live status
`status/{uid}` is rewritten every 5 s while recording; `onDisconnect().removeValue()` is registered with it so the server
removes the entry if the phone disappears.
