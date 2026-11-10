# Testing

## Strategy

| Level | What | How it runs |
|---|---|---|
| **Unit tests** (`app/src/test`) | All logic that does not need Android: sensor maths, graph buffer, recording state machine, analysis, experiment config + analysers, export writers, XML reader/importer, sync planner + engine (incl. raw-data upload/restore), cloud mapper, HTTP downloader/cache/updater, weather API client, battery policy, provider routing, notification destinations | JVM, `./gradlew testDebugUnitTest` |
| **Instrumented tests** (`app/src/androidTest`) | Real Room database, ContentProvider through a ContentResolver, crash recovery, UI smoke test | device or emulator, `./gradlew connectedDebugAndroidTest` |
| **Manual tests** | Sensors on a real phone, notifications and background behaviour, login and Firebase, PDF look | checklist below |

Design for testability: Android-specific code sits behind small interfaces (`SessionStore`, `SampleSource`,
`SyncEngine.LocalStore/CloudStore`), so the algorithms around them are tested with in-memory fakes. Network code uses only
the JDK HTTP client and is tested against a real local test server. Algorithms are checked against **synthetic signals**
with known answers and hand-computed values.

## Results

**Unit tests: 187 tests, 31 classes, all passing.**

| Area | Tests |
|---|---|
| analysis (stats, running stats, series, filter, peaks, period, regression, error, physics) | 44 |
| graph (ring buffer, nice scale, time format) | 13 |
| sensor (types, rates, rate estimator, formatter) | 13 |
| recording (state machine, sample sink, summary) | 16 |
| data (chunked recorder, names/LIKE escaping) | 7 |
| experiment (config parser + input validation, analysers incl. altitude from the weather reference) | 23 |
| export (CSV/JSON writers, summary, file names, XML round trip + rejection cases, importer) | 18 |
| cloud (mapper; planner and engine incl. conflicts, failures, raw-data upload/restore/cleanup) | 29 |
| net (experiment download: ETag, 304, 404, 500, oversize, offline, HTTPS rule, cache; weather API: parsing, bad data, errors, coordinates) | 9 |
| service / provider / util (battery policy, URI routes, notification destinations) | 15 |

**Instrumented tests: 16** (`AppDatabaseTest` 6, `SessionRecoveryTest` 3, `SessionProviderTest` 5, `MainActivityTest` 2).

> **Honest note:** the unit tests were executed on a desktop JDK against the pure-Java sources. The Android-specific code
> (screens, login, service, notifications, Room, Firebase, WorkManager, PDF) could not be compiled or run where the project
> was written, so run **Sync → Build → unit tests → instrumented tests** once in Android Studio and treat any compile message
> as a normal first-run fix. Static checks were run on all XML files, string/drawable/layout references, view ids used by the
> code, and manifest/navigation class names.

## Manual test checklist (use a physical phone)

**Sensor picker (bug fix)**
- [ ] Recorder → Sensor dropdown lists **every** available sensor, every time it is opened (also after rotating, after
      switching tabs, and after choosing one).
- [ ] Choose Gyroscope, Start: the graph legend and the status show Gyroscope (repeat for Magnetometer, Light, Barometer).

**Login, profile, data across devices** (needs `docs/CLOUD_SETUP.md`)
- [ ] First start shows the **login page**. Wrong password, short password, bad email, existing email: friendly messages.
- [ ] Create an account (with a name), sign in, sign out, sign in again. "Forgot password?" sends an email.
- [ ] Profile shows name, email, member since, recording counts; changing the name is saved.
- [ ] Record while signed out (guest), then sign in: the guest recordings appear in the account and are backed up.
- [ ] A second account on the same phone does **not** see the first account's recordings; they return when the first signs in.
- [ ] Second phone, same account: after signing in, recordings **with their graphs** appear (raw data restored).
- [ ] Record on phone A; phone B → Cloud screen shows "A is recording … right now" live, and clears when A stops.
- [ ] Airplane mode: whole app works; Cloud screen says Offline; backup waits and runs when online.

**Notifications**
- [ ] Android 13+: the permission prompt appears once at first start.
- [ ] Record something, press Home, open the shade: the recording notification shows; tap it: the app opens on the Recorder.
- [ ] Turn on airplane mode: a red banner stays at the top while offline. Record or finish an experiment: the banner shows "1 item will upload". Turn airplane mode off: the banner says "Back online. Saving 1 item to the cloud" and then disappears, and the item appears in the Firebase console.
- [ ] Record, press Home: ongoing notification with elapsed time; **Stop** saves; tapping the body opens the Recorder.
- [ ] After Stop: "Recording saved" notification; tapping it opens History.
- [ ] With the app closed, a backup that uploads something shows "Backup finished"; tapping opens the Cloud screen.
- [ ] Switch off the notification setting in Profile: information notifications stop (the recording one stays).
- [ ] Tapping a notification while logged out goes through the login page and then to the target.

**Sensors, recording, background**
- [ ] Sensors tab live values; sampling chips change measured Hz; Recorder start/pause/resume/stop/markers; zoom/pan.
- [ ] Home + screen off for a minute: samples keep rising; swipe from recents: still recording; force-stop: "(recovered)".

**Experiments and the weather API**
- [ ] Pendulum, magnetic, light work as before.
- [ ] Relative height with latitude and longitude filled and internet on: result shows sea-level pressure and altitudes.
- [ ] Same with airplane mode: the result still appears, with "Weather reference unavailable".
- [ ] Only one coordinate filled: a clear message asks for both.

**History, export, provider**
- [ ] Search, rename, swipe-delete + Undo; share CSV/JSON/XML/PDF; export XML → delete → import XML.
- [ ] Profile → Provider demo shows queries and "Delete: refused".

## Known gaps and limitations

* The PDF layout and the Material styling were not seen on a device while writing; inspect screenshots.
* Firebase behaviour (rules, offline queueing, the Realtime Database's responses) could only be written against the
  documentation; the sync algorithm itself is fully tested with fakes.
* Backups are additive: deleting a recording on the phone does not delete its backup. Conflicts are decided by newest change time.
* Raw-data restore is limited to 500,000 samples per recording (bigger ones restore as a summary).
* Room migrations 1→2 and 2→3 have no automated migration test (it would need exported schemas); they were reviewed by hand.
* Experiments are cancelled when the app is backgrounded (only free recordings run in the background).
* On Android 14+ (target SDK 34) the foreground service type permission and receiver export flag need adding.
* Up to 500 of the very last samples can be lost if the process is killed abruptly.
