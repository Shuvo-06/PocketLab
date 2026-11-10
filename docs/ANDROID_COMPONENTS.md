# Syllabus coverage

Where each Android concept from the proposal is demonstrated.

| Concept | Where |
|---|---|
| **Activities** | `MainActivity` (single activity, toolbar, menu, offline banner, login gate, deep links), `LoginActivity` (login page) |
| **Fragments + Navigation component** | `res/navigation/nav_graph.xml`; `DashboardFragment`, `RecorderFragment`, `ExperimentsFragment`, `ExperimentWizardFragment`, `HistoryFragment`, `SessionDetailFragment`, `CloudFragment` |
| **Intents** (explicit, implicit, extras) | Share: `Intent.ACTION_SEND` + chooser (`SessionDetailFragment.share`); notification → `MainActivity`; service start/stop intents; navigation arguments as `Bundle` |
| **Layouts / XML** | `res/layout/*.xml`: LinearLayout, FrameLayout, ScrollView, MaterialCardView, ChipGroup, TextInputLayout |
| **ViewBinding** | every screen (`FragmentXBinding`, `ItemXBinding`) |
| **Material Design 3 + themes + dark mode** | `res/values/themes.xml`, `values-night/themes.xml`, Material components throughout |
| **RecyclerView + Adapter + ViewHolder** | `SensorInfoAdapter`, experiment list, session list (`HistoryFragment.Adapter`), swipe-to-delete with `ItemTouchHelper` |
| **Custom View (Canvas)** | `ui/widget/GraphView` (onDraw, gestures, pinch/pan/double-tap) |
| **Sensors (SensorManager)** | `sensor/SensorHub`, listener lifecycle in `onStart`/`onStop` |
| **ViewModel / lifecycle** | `DashboardViewModel`, `RecorderViewModel`, `ExperimentViewModel` |
| **Services** | `service/RecordingService`, a foreground service (notification, Stop action, wake lock, 6 h limit) |
| **BroadcastReceiver** | `service/BatteryEventReceiver` (dynamically registered: `ACTION_BATTERY_CHANGED` and `ACTION_BATTERY_LOW` cannot be declared in the manifest) |
| **ContentProvider + ContentResolver** | `provider/SessionProvider` (read-only), `SessionContract`, `ProviderDemo` (Profile → Provider demo), instrumented `SessionProviderTest` |
| **ConnectivityManager + LiveData** | `cloud/ConnectivityMonitor` (default `NetworkCallback`), observed by `MainActivity` for the offline banner |
| **Notifications and channels** | `service/Notifier` (three channels, tap-to-open `PendingIntent`s), posted by `RecordingService`, `SyncWorker`, `ExperimentUpdateWorker`, `RecordingController`; Android 13 permission request in `MainActivity` |
| **Runtime permissions & manifest** | `AndroidManifest.xml` (see comments); `POST_NOTIFICATIONS` request |
| **Room database (CRUD)** | `data/*`: entities, DAOs, migration 1→2, transactions, relations with cascade delete |
| **Background threading** | `ExecutorService`s (see ARCHITECTURE.md) and **WorkManager** (`work/*`) |
| **Networking** | `net/ExperimentDownloader` (HttpURLConnection, HTTPS, ETag, timeouts) |
| **JSON parsing** | `experiment/ExperimentParser` (org.json), `JsonExporter` (streaming writer) |
| **XML parsing** | `export/XmlSessionReader` (SAX) and `XmlExporter` (round-trip) |
| **File handling & sharing** | `FileProvider` (`res/xml/file_paths.xml`), cache files, Storage Access Framework import (`OpenDocument`) |
| **PDF generation** | `export/PdfReport` (`PdfDocument`, `StaticLayout`, Canvas) |
| **Firebase Authentication** | `cloud/AuthManager` (email/password, display name, password reset), `LoginActivity`, `ui/profile/ProfileFragment` |
| **Cloud Firestore** | `cloud/FirestoreCloudStore`, `ProfileManager`, `CloudMapper`, `SyncEngine` |
| **Firebase Realtime Database** | `cloud/RtdbSampleStore` (raw samples), `cloud/LiveStatus` (listener + `onDisconnect`) |
| **REST API** | `net/WeatherApi` (Open-Meteo, JSON), `net/ExperimentDownloader` |
| **Intents** (deep links, implicit) | `util/AppDestination`, `service/Notifier.openApp`, `MainActivity.onNewIntent`, `ProfileFragment` (`ACTION_SENDTO`, `ACTION_APP_NOTIFICATION_SETTINGS`) |
| **Lifecycle & configuration changes** | process-level `RecordingController`, activity-scoped ViewModels, state restored on rotation |
| **Testing** | JUnit (pure logic), Room/ContentProvider/UI instrumented tests (`TESTING.md`) |
