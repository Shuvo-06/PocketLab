# PocketLab features and how each one is built

Every feature below has two parts: **what the user sees**, and **how it is implemented** (classes and ideas). Package
names are under `com.pocketlab.app`. More detail on algorithms is in `ALGORITHMS.md`, the database in `DATABASE.md`, and
the layers in `ARCHITECTURE.md`.

---

## 1. Accounts and login

**What the user sees.** On first start (when Firebase is set up) a login page opens. The user can sign in, create an
account (with a name), reset a forgotten password by e-mail, or tap **Continue without an account** (guest mode). Pressing
the keyboard's Done key submits the form. If the phone is offline, the page says so at once instead of waiting for a timeout.
The Profile screen shows name, e-mail, join date and number of recordings, lets the user change the name, and sign out.

**How it is built.**
* `LoginActivity` + `activity_login.xml`: Material text fields, one form that switches between "sign in" and "register".
* `cloud/AuthManager` wraps Firebase Authentication (`signInWithEmailAndPassword`, `createUserWithEmailAndPassword`,
  `sendPasswordResetEmail`, `updateProfile`). Input is validated first (e-mail pattern, password of 6 or more characters), and
  Firebase errors are turned into plain messages.
* `AuthManager.needsLogin()` is true when Firebase is configured, nobody is signed in and guest mode was not chosen.
  `MainActivity.onCreate` checks it and starts `LoginActivity`. A tapped notification's target is carried through the login.
* Guest choice is a flag in `SyncPrefs` (SharedPreferences). Firebase keeps the user signed in across restarts.
* **Ownership.** Every recording row has `ownerUid`. History, sync, export and the ContentProvider filter by it, so two
  people on one phone never see each other's data. After a guest signs in, `SessionRepository.claimGuestSessions` gives the
  guest's recordings to the new account.
* If `google-services.json` is missing, `AuthManager.isConfigured()` is false and the app works as guest only.

## 2. Sensor dashboard (tab "Sensors")

**What the user sees.** Cards for accelerometer, gyroscope, magnetometer, light and barometer with live values, units,
whether the sensor exists, hardware details (range, resolution, minimum delay), accuracy, a sampling rate chooser, and the
**measured** rate.

**How it is built.**
* `sensor/SensorHub` wraps `SensorManager`: lists sensors, registers and unregisters listeners with the lifecycle
  (`onStart`/`onStop`), and forwards samples with the sensor's own timestamp.
* `sensor/SensorType` and `SamplingRate` describe supported sensors and requested rates. Android treats the rate as a hint, so
  `sensor/RateEstimator` measures the real rate over a sliding window of 50 timestamps: `(n-1) / (t_last - t_first)`.
* `DashboardFragment` + `DashboardViewModel` + `SensorInfoAdapter` (a RecyclerView) show the cards. Missing sensors are shown as
  "Not available", never as a crash (sensors are `required="false"` in the manifest).

## 3. Free recorder (tab "Recorder")

**What the user sees.** Pick a sensor and rate, then Start, Pause, Resume and Stop. A live graph, a running summary, and an
**event marker** button that labels a moment on the time axis.

**How it is built.**
* `service/RecordingController` is created in `PocketLabApp` and owns the live recording, so rotation or leaving the screen does
  not lose it. `recording/RecordingSession` is a small state machine (idle, recording, paused, finished) that keeps the time axis
  from sensor timestamps and removes paused gaps.
* **Every sample is saved.** `data/SessionRecorder` writes samples in 500-sample chunks on its own writer thread into Room: first
  the session row (`complete = 0`), then chunks in order, and finally markers and `complete = 1` in one transaction.
* `ui/recorder/RecorderFragment` + `RecorderViewModel` draw the screen; a ticker refreshes the UI, not the sensor callback.
* After Stop, `SyncScheduler.onSessionSaved` queues an upload (see section 11).

## 4. Live graph

**What the user sees.** A smooth graph with auto scaling, pinch zoom, pan, double tap to reset, and vertical lines for markers.

**How it is built.** `ui/widget/GraphView` is a custom `View` drawing with Canvas and `GestureDetector`/`ScaleGestureDetector`.
`graph/SeriesBuffer` is a ring buffer (6000 points per axis) so memory stays bounded however long the recording is.
`graph/NiceScale` picks round axis ticks and `graph/TimeFormat` formats time labels. The database still gets every sample.

## 5. Analysis toolbox

Pure Java in `analysis/` with no Android imports, so it is tested on the JVM: mean, standard deviation (Welford's one pass
algorithm for huge recordings), RMS, a first order low pass filter that uses the real time step, peak detection with prominence
and minimum separation, period estimation by zero crossings with hysteresis, linear regression with R², and percentage error.

## 6. Guided experiments (tab "Experiments")

**What the user sees.** Four experiments: pendulum (measures g), magnetic field, light level and relative height. A wizard walks
through instructions, inputs (validated), the run, and a result with an interpretation and an uncertainty.

**How it is built.**
* Definitions are JSON (`assets/experiments.json`), parsed and validated by `experiment/ExperimentParser`. `ExperimentRepository`
  prefers a downloaded cache and falls back to the bundled file.
* `ui/experiments/ExperimentWizardFragment` + `ExperimentViewModel` run the pages. On finish `experiment/ExperimentAnalyzer`
  combines the analysis blocks (for the pendulum: trim, zero phase low pass, zero crossing period, `g = 4π²L/T²`, uncertainty and
  percentage error against 9.80665 m/s²).
* Only successful runs are saved (as `kind = EXPERIMENT` with the result text) and queued for upload; failed attempts are discarded.
* Relative height can call the Open-Meteo weather API for sea level pressure (`net/WeatherApi`) on a background thread. If the call
  fails the result only loses the altitude lines.
* Experiments pause when the app goes to the background (`onAppBackgrounded`), because a run needs the screen. A free recording keeps going.

## 7. History (tab "History")

**What the user sees.** A searchable list of saved recordings, a detail screen with the graph and numbers, rename, swipe to
delete with Undo, and automatic recovery of recordings interrupted by a crash or an empty battery.

**How it is built.** `ui/history/HistoryFragment` (RecyclerView, `ItemTouchHelper`) and `SessionDetailFragment`. `data/SessionRepository`
runs all reads and edits on one background thread, so a read always sees earlier writes. Delete is a soft delete (`trashed = 1`) that
allows Undo and is purged at the next start. The detail screen loads at most 100,000 samples, thinned evenly. `data/SessionRecovery`
keeps what an interrupted recording already saved and renames it "(recovered)". The search uses `LIKE ... ESCAPE` so `%` and `_` are literal.

## 8. Export and import

**What the user sees.** From a recording: CSV, JSON, XML or a PDF lab report, shared with the Android share sheet. XML files can
be imported back.

**How it is built.** `export/` writers stream samples from the database in pages of 1000, so memory stays small.
`export/PdfReport` uses `PdfDocument`, `StaticLayout` and Canvas, with the graph thinned to about 1200 points. Files go to the cache
folder and are shared through `FileProvider` (`res/xml/file_paths.xml`). `export/XmlSessionReader` is a SAX reader that validates
everything (root, version, numbers, time order, declared and real counts, size limits) and rejects DOCTYPE to block XML entity
attacks. `TransferManager` runs it all on one executor.

## 9. Background recording and battery protection

**What the user sees.** A recording continues with the screen off or the app closed, with an ongoing notification that has a Stop
button. A warning at 15% battery, automatic stop and save at 5%, and a 6 hour limit.

**How it is built.** `service/RecordingService` is a foreground service (`foregroundServiceType="dataSync"`) holding a time limited
wake lock. `service/BatteryEventReceiver` is registered at run time (battery broadcasts cannot be declared in the manifest) and
`service/BatteryPolicy` (pure) decides: warn once at 15% or less, stop at 5% or less, never act while charging, re-arm above 20%.

## 10. Notifications

**What the user sees.** Notifications for: recording running, recording saved, backup finished or problem, new experiments, battery
warnings. Tapping one opens the right screen. Profile has a switch for the information notifications and a button to the system
notification settings. (The old "Send a test" button was removed.)

**How it is built.** `service/Notifier` creates three channels (recording: silent, alerts: heads up, events: default) and builds a
`PendingIntent` per destination with its own request code (otherwise Android would reuse one and ignore the extras). `util/AppDestination`
is the deep link key in the Intent extra. `MainActivity` handles it in `onCreate` and `onNewIntent` (`launchMode="singleTop"`) and removes
it so rotation cannot repeat it. Posted from the service and from WorkManager workers, so they work with the app closed. Android 13 and
above asks for `POST_NOTIFICATIONS` once.

## 11. Offline mode and the upload queue (new)

**What the user sees.**
* While the phone has no working internet, a **banner stays at the top of the screen** under the toolbar. For a signed in user with
  waiting items it reads "You are offline. 2 items will upload when you are back online." The count updates by itself.
* The app keeps working fully offline. Recordings and experiment results are saved on the phone first.
* When the connection returns, the banner turns to "Back online. Saving 2 items to the cloud." for four seconds, then disappears. The
  items are uploaded and a "backup finished" notification appears.
* Guests (no account) see the offline banner but nothing is queued, because there is no cloud account to upload to.

**How it is built.**
1. **Detecting the connection.** `cloud/ConnectivityMonitor` registers one `ConnectivityManager.NetworkCallback` for the app (in
   `PocketLabApp`) and exposes `LiveData<Boolean> online()`. "Online" means the network has internet **and** Android has validated it
   (a Wi-Fi network without internet counts as offline). `MainActivity` observes it.
2. **Banner.** `activity_main.xml` has a `connection_banner` TextView between toolbar and content. `MainActivity.onConnectivityChanged`
   shows it with error colors while offline and re-reads the waiting count every 3 seconds. Back online it uses primary colors, and hides it
   with a delayed Runnable. Rotation is safe: the LiveData delivers the current state again.
3. **The queue is the database.** A saved recording or experiment is marked as not yet backed up by its columns
   (`cloudId IS NULL OR modifiedAt > syncedAt`). `SessionDao.countUnsynced` counts them, and that number is the banner count. Nothing is
   lost if the app is killed, because the queue lives in SQLite, not in memory.
4. **Uploading.** `SyncScheduler.onSessionSaved` (called after a recording or a successful experiment) enqueues a unique WorkManager job
   with a network constraint. WorkManager **waits** while offline, survives app restarts and runs the job when the network is back.
   On reconnect `SyncScheduler.syncPending` also enqueues a job (policy `KEEP`, so a flickering connection never cancels a running upload).
5. **Retries.** `work/SyncWorker` calls `CloudSync.run`, which runs `SyncEngine` (see section 12). Temporary failures retry with exponential
   backoff up to 5 attempts. Setup or permission problems stop and notify the user.
6. **Wi-Fi only.** The default is now **any network**, so queued items upload on mobile data too. The user can switch on "Only on Wi-Fi" in
   Cloud backup and updates. If Automatic backup is off, nothing is uploaded by itself (the user can still use Back up now).

## 12. Cloud backup and sync

**What the user sees.** Recordings follow the account to other phones. The Cloud screen shows online state, last sync, counts, and
buttons for Back up now and Restore, plus the Automatic backup and Only on Wi-Fi switches.

**How it is built.**
* **Two Firebase databases, each for what it suits.** Cloud Firestore holds small documents: `users/{uid}` (profile) and
  `users/{uid}/sessions/{id}` (one summary per recording). The Realtime Database holds the bulk raw samples (`samples/{uid}/{id}/{n}`, 500
  per node) and the live status (`status/{uid}`).
* `cloud/SyncEngine` is pure Java over two interfaces (`LocalStore`, `CloudStore`), so the whole algorithm, including failures, is tested with
  fakes. `cloud/SyncPlanner` decides per recording: nothing, upload, apply the cloud's newer name, or restore. If both sides changed, the
  newer `modifiedAt` wins.
* Raw samples are uploaded **before** the summary, so a summary in the cloud always points to complete data. A failed upload deletes the
  partial samples. Samples never change, so a rename uploads only the summary.
* Restore downloads samples first and stores the session and its samples in one transaction, so a failed restore leaves nothing half done.
* `work/SyncScheduler` also schedules a sync about every 6 hours (`PeriodicWorkRequest`) and "soon after save" (30 seconds delay).

## 13. Live status across devices

While a phone records, `cloud/LiveStatus` writes `status/{uid}` every 5 seconds and registers `onDisconnect().removeValue()` with it, so the
entry disappears by itself if the phone loses its connection. The Cloud screen on another device listens with a `ValueEventListener` and shows
"recording now".

## 14. Experiment updates over the network

`work/ExperimentUpdateWorker` runs daily with a network constraint. `net/ExperimentDownloader` uses HTTPS only, an `ETag` conditional GET,
10 second timeouts and a 1 MB limit. The file is parsed with the app's own parser **before** it replaces the cache, so a bad download never breaks
the experiments. A notification tells the user about new experiments. Without a server address (`config.xml`) it does nothing.

## 15. ContentProvider demo

`provider/SessionProvider` is a read-only provider (not exported) that lets the app query its own saved sessions through `ContentResolver`.
`provider/ProviderDemo` runs a few queries and shows the result, including a refused delete. It is now a button on the **Profile** screen
("Provider demo"), because the Info button was removed from the toolbar.

## 16. Profile screen

Name, e-mail, member since, recording counts (total and not yet backed up), edit name, sign out (with a warning if items are not backed up),
sign in and register buttons for guests, notification switch and settings, Provider demo, and a feedback e-mail button (hidden when
`feedback_email` in `config.xml` is empty). Feedback and notification settings use implicit Intents (`ACTION_SENDTO`,
`ACTION_APP_NOTIFICATION_SETTINGS`).

---

## What changed in this version

| Request | Change |
|---|---|
| Remove test notification | Removed the "Send a test" button, its handler, `Notifier.test`, and its strings. |
| Add login page | The login page already existed. Added: keyboard Done key submits, keyboard hides on submit, instant "No internet" message, buttons disabled while busy. |
| Firebase configuration | All Firebase code kept. Fixed `build.gradle` (duplicate BoM and plugin). New `docs/FIREBASE_SETUP.md` lists what to add in the console. |
| Remove the info button | Removed from the toolbar menu and `MainActivity`. The Provider demo moved to Profile. |
| Offline banner and queue | New `ConnectivityMonitor`, banner in `MainActivity`, `SyncScheduler.syncPending`, Wi-Fi only default off. |
| Detailed feature text | This file. |
