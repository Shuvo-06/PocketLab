# Testing

## Strategy

| Level | What | How it runs |
|---|---|---|
| **Unit tests** (`app/src/test`) | All logic that does not need Android: sensors maths, graph buffer, recording state machine, analysis, experiment config + analysers, export writers, XML reader/importer, sync planner + engine, cloud mapper, HTTP downloader/cache/updater, battery policy, provider routing | JVM, `./gradlew testDebugUnitTest` |
| **Instrumented tests** (`app/src/androidTest`) | Real Room database, ContentProvider through a ContentResolver, crash recovery, UI smoke test | device or emulator, `./gradlew connectedDebugAndroidTest` |
| **Manual tests** | Sensors on a real phone, notification/background behaviour, PDF look, Firebase | checklist below |

Design for testability: Android-specific code sits behind small interfaces (`SessionStore`, `SampleSource`,
`SyncEngine.LocalStore/CloudStore`), so the algorithms around them are tested with in-memory fakes.
Algorithms are checked against **synthetic signals** with known answers (a sine with a known period must give that
period; a pendulum of length L must give g ≈ 9.81; a pressure drop of 1 hPa must give ≈ 8.3 m) and against
hand-computed values.

## Results

**Unit tests: 167 tests, 29 classes, all passing.**

| Area | Tests |
|---|---|
| analysis (stats, running stats, series, filter, peaks, period, regression, error, physics) | 44 |
| graph (ring buffer, nice scale, time format) | 13 |
| sensor (types, rates, rate estimator, formatter) | 13 |
| recording (state machine, sample sink, summary) | 16 |
| data (chunked recorder, names/LIKE escaping) | 7 |
| experiment (config parser + input validation, analysers) | 21 |
| export (CSV/JSON writers, summary, file names, XML round trip + rejection cases, importer) | 18 |
| cloud (mapper, planner, engine incl. failure/conflict cases) | 18 |
| net (HTTP download against a local test server: ETag, 304, 404, 500, oversize, offline, HTTPS rule; cache) | 5 |
| service / provider (battery policy, URI routes) | 12 |

**Instrumented tests: 16** (`AppDatabaseTest` 6, `SessionRecoveryTest` 3, `SessionProviderTest` 5, `MainActivityTest` 2).

> **Important and honest note:** the unit tests were executed on a desktop JDK against the pure-Java sources. The
> Android-specific code (screens, service, Room, Firebase, WorkManager, PDF) could not be compiled or run in the
> environment where the project was written, so run **Sync → Build → unit tests → instrumented tests** once in
> Android Studio and treat any compile message as a normal first-run fix.

## Manual test checklist (use a physical phone)

**Sensors and recording**
- [ ] Sensors tab shows 5 cards; missing sensors are dimmed with "Not available". Values move when the phone moves.
- [ ] Changing the sampling chip changes the measured Hz.
- [ ] Recorder: Start → graph draws; Pause/Resume leaves no jump; Marker adds a dashed line; Stop shows a summary.
- [ ] Pinch zooms the graph, drag pans, double-tap returns to live.
- [ ] Rotate the phone mid-recording: recording continues.

**Background**
- [ ] Start, press Home, lock the screen for 1 minute: the notification stays, the sample count keeps rising.
- [ ] Notification **Stop** ends the recording and it appears in History.
- [ ] Swipe the app from recents mid-recording: still recording; Stop still saves.
- [ ] Force-stop mid-recording, reopen: a "(recovered)" session exists.
- [ ] Android 13+: the notification permission prompt appears once.

**Experiments**
- [ ] Pendulum (1 m string, small swings, screen facing you): g within ~10 % of 9.8.
- [ ] Wrong length input → clear error message; empty input rejected.
- [ ] Magnet near the phone's top edge: "magnet detected". Light: result matches the room type. Height: use ≥ 3 floors.
- [ ] Press Home during an experiment run: run is cancelled with a message.

**History and export**
- [ ] Search, rename, swipe-delete + Undo; open a session, graph and summary match.
- [ ] Share CSV, JSON, XML, PDF; open each. PDF has objective, results, table, graph, disclaimer.
- [ ] Export XML, delete the session, import the XML: identical session returns. Import a non-PocketLab file: readable error.
- [ ] About → Provider demo shows 3 queries and "Delete: refused".

**Network and cloud** (needs `docs/CLOUD_SETUP.md`)
- [ ] Airplane mode: whole app works; Cloud screen says Offline; "Back up now" says it will run when online.
- [ ] Sign in, Back up now → sessions appear in the Firestore console. Rename on the phone, back up again → document updated.
- [ ] Second device/emulator, same account, Restore: recordings appear as "summary only".
- [ ] Wrong password, weak password, existing email: friendly messages.
- [ ] Experiment server: after hosting a changed file, "Check for updates" says downloaded and the list changes; host an invalid file → rejected, old list kept.

## Known gaps and limitations

* The PDF layout and the Material styling were not seen on a device while writing; send/inspect screenshots.
* Sensor accuracy depends on the phone (see `REQUIREMENTS.md`); pendulum thresholds were tuned on synthetic data.
* Room migration 1→2 has no automated migration test (it would need exported schemas); it was reviewed by hand.
* Cloud backup stores summaries only, is additive (local deletes do not delete backups), and resolves conflicts by
  newest change time, which assumes reasonably correct phone clocks.
* Experiments are cancelled when the app is backgrounded (only free recordings run in the background).
* On Android 14+ (target SDK 34) the foreground service type permission and receiver export flag need adding.
* Up to 500 of the very last samples can be lost if the process is killed abruptly.
