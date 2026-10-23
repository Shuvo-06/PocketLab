# Syllabus coverage

Where each Android concept from the proposal is demonstrated.

| Concept | Where |
|---|---|
| **Activities** | `MainActivity` (single activity, toolbar, menu, About dialog) |
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
| **ContentProvider + ContentResolver** | `provider/SessionProvider` (read-only), `SessionContract`, `ProviderDemo` (About → Provider demo), instrumented `SessionProviderTest` |
| **Notifications and channels** | `RecordingService` (low-importance ongoing channel, high-importance alert channel); Android 13 permission request in `RecorderFragment` |
| **Runtime permissions & manifest** | `AndroidManifest.xml` (see comments); `POST_NOTIFICATIONS` request |
| **Room database (CRUD)** | `data/*`: entities, DAOs, migration 1→2, transactions, relations with cascade delete |
| **Background threading** | `ExecutorService`s (see ARCHITECTURE.md) and **WorkManager** (`work/*`) |
| **Networking** | `net/ExperimentDownloader` (HttpURLConnection, HTTPS, ETag, timeouts) |
| **JSON parsing** | `experiment/ExperimentParser` (org.json), `JsonExporter` (streaming writer) |
| **XML parsing** | `export/XmlSessionReader` (SAX) and `XmlExporter` (round-trip) |
| **File handling & sharing** | `FileProvider` (`res/xml/file_paths.xml`), cache files, Storage Access Framework import (`OpenDocument`) |
| **PDF generation** | `export/PdfReport` (`PdfDocument`, `StaticLayout`, Canvas) |
| **Firebase Authentication** | `cloud/AuthManager` (email/password) |
| **Cloud Firestore** | `cloud/FirestoreCloudStore`, `CloudMapper`, `SyncEngine` |
| **Lifecycle & configuration changes** | process-level `RecordingController`, activity-scoped ViewModels, state restored on rotation |
| **Testing** | JUnit (pure logic), Room/ContentProvider/UI instrumented tests (`TESTING.md`) |
