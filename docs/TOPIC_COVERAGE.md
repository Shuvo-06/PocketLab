# Topic checklist

Each required topic, where you can **see** it in the app and where it is in the **code**.

| # | Topic | Status | See it in the app | Code |
|---|---|---|---|---|
| 1 | **Realtime Database** | ✅ included | **Cloud screen → "Live on your account"**: start a recording on one phone and another phone signed in to the same account shows it live (no refresh). Raw measurements are also stored here, so recordings keep their graphs on other devices. | `cloud/RtdbSampleStore` (raw samples, 500 per node), `cloud/LiveStatus` (live presence with a `ValueEventListener` and `onDisconnect().removeValue()`), `cloud/FirebaseCloudStore` |
| 2 | **Cloud Firestore** | ✅ included | Recording summaries and the user profile are stored per account; **Profile** and **Cloud → Back up now / Restore** | `cloud/FirestoreCloudStore` (`users/{uid}/sessions/{id}`), `cloud/ProfileManager` (`users/{uid}`), `cloud/CloudMapper` |
| 3 | **Firebase Authentication** | ✅ included | **Login page** at first start: sign in, create account (with name), forgot password, continue as guest. **Profile**: edit name, sign out. | `LoginActivity`, `cloud/AuthManager`, `ui/profile/ProfileFragment` |
| 4 | **Using Intent** | ✅ included | Share a recording (share sheet); tap a notification to jump to a screen; Profile → "System settings" and "Send feedback"; login → main | explicit: `LoginActivity.goToMain`, service start/stop, `Notifier.openApp`; implicit: `Intent.ACTION_SEND` + chooser (`SessionDetailFragment.share`), `Intent.ACTION_SENDTO mailto:` and `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (`ProfileFragment`); Intent **extras** and deep links (`util/AppDestination`, `MainActivity.handleDestination`); `onNewIntent` with `launchMode="singleTop"`; result contracts (`OpenDocument`, `RequestPermission`) |
| 5 | **Notifications** | ✅ included | Recording notification with Stop; **"Recording saved"**; **"Backup finished / problem"**; **"New experiments available"**; battery warnings. All work with the app closed; tapping opens the app on the right screen. | `service/Notifier` (3 channels, deep-link `PendingIntent`s), `service/RecordingService`, `work/SyncWorker`, `work/ExperimentUpdateWorker`, `service/RecordingController` |
| 6 | **JSON** | ✅ included | Experiments are defined in JSON; JSON export; the weather API answers in JSON | `assets/experiments.json` + `experiment/ExperimentParser`, `export/JsonExporter`, `net/WeatherApi` (parses the API's JSON), `net/ExperimentUpdater` (downloaded JSON) |
| 7 | **APIs / components** | ✅ included | *REST API:* Experiments → Relative height → enter latitude/longitude → the result adds today's sea-level pressure from the free **Open-Meteo API** and your altitude. *Android components:* see below | REST: `net/WeatherApi`, `net/ExperimentDownloader`. Components: **Activity** (`MainActivity`, `LoginActivity`), **Service** (`RecordingService`), **BroadcastReceiver** (`BatteryEventReceiver`), **ContentProvider** (`SessionProvider`, + FileProvider), plus WorkManager, Room, SensorManager, PdfDocument, ConnectivityManager APIs |

## Notes

* "Realtime Database" and "Cloud Firestore" are two different Firebase databases. PocketLab uses each for what it suits:
  Firestore for small structured documents (profile, summaries), Realtime Database for bulk raw data and live presence.
* Firebase features need the one-time setup in `CLOUD_SETUP.md`. Without it the app runs fully but offline (and says so).
* Weather lookup sends only the two coordinates you type, and only when you finish a Relative-height run with both filled in.
