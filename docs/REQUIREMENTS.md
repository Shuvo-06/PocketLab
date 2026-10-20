# Requirements & Sensor Limitations

## Functional requirements
| ID | Requirement | Phase | Status |
|---|---|---|---|
| F1 | Availability, units, live values for 5 sensors | 2 | done |
| F2 | Free recording: pause/resume/stop, markers | 3 | done |
| F3 | Real-time multi-series graph, bounded buffer | 3 | done |
| F4 | Analysis: mean, std-dev, low-pass, peaks, period, regression, % error | 4 | done |
| F5 | Four guided experiments from JSON config | 5 | |
| F6 | Save/rename/delete sessions (Room) | 6 | |
| F7 | Export CSV/JSON/PDF, share via FileProvider | 7 | |
| F8 | Foreground-service recording, BroadcastReceiver, ContentProvider | 8 | |
| F9 | HTTP experiment definitions, Firebase backup, WorkManager sync | 9 | |

## Non-functional requirements
Works offline; missing sensors never crash; UI thread never blocked by DB/file work; light/dark
themes; results labelled as estimates.

## Sensor notes
| Sensor | Unit | Caveats |
|---|---|---|
| Accelerometer | m/s² | Includes gravity; Android 12+ caps at 200 Hz without HIGH_SAMPLING_RATE_SENSORS |
| Gyroscope | rad/s | Drift; absent on some low-end phones |
| Magnetometer | µT | Affected by metal, cases, calibration |
| Light | lx | Low resolution; on-change delivery |
| Barometer | hPa | Absent on many budget phones; ~0.1 hPa ≈ 1 m near sea level |

## Permissions
Now: INTERNET, ACCESS_NETWORK_STATE, HIGH_SAMPLING_RATE_SENSORS.
Later: POST_NOTIFICATIONS (API 33+), FOREGROUND_SERVICE.
