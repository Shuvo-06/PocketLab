# Firebase setup checklist (PocketLab)

All Firebase code is already in the app. You only have to prepare the Firebase project and the config file.
The app still builds and runs without any of this (it then works as "guest only").

Your Firebase project: **PocketLab** (id `pocketlab-6`), Android package name: **`com.pocketlab.app`**.

## 1. What you must do in the Firebase console

| # | Where | What to do |
|---|---|---|
| 1 | Project settings → Your apps | Make sure an Android app with package `com.pocketlab.app` is registered (nickname `PocketLab`). No SHA key is needed for email and password sign in. |
| 2 | Project settings → Your apps | Download **`google-services.json`** and put it in **`app/`** (next to `app/build.gradle`). |
| 3 | Build → Authentication → Sign-in method | Enable **Email/Password**. |
| 4 | Build → Firestore Database | **Create database**, then open **Rules** and publish the Firestore rules below. |
| 5 | Build → Realtime Database | **Create database**, then open **Rules** and publish the Realtime Database rules below. |
| 6 | Project settings → Your apps | **Download `google-services.json` again** after step 5 and replace the old file. The new file contains the Realtime Database address. Without it, raw measurements are not uploaded. |
| 7 | Android Studio | File → Sync Project with Gradle Files, then run the app. |

### Firestore rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

### Realtime Database rules

```
{
  "rules": {
    "samples": { "$uid": { ".read": "auth != null && auth.uid == $uid", ".write": "auth != null && auth.uid == $uid" } },
    "status":  { "$uid": { ".read": "auth != null && auth.uid == $uid", ".write": "auth != null && auth.uid == $uid" } }
  }
}
```

## 2. What is already done in the Gradle files (do not add it again)

The Firebase console shows a "Add Firebase SDK" page with three snippets. **Do not paste them.** They are already
handled, and the console's newest versions do not work with this project:

| Console snippet | In this project |
|---|---|
| Plugin `com.google.gms.google-services` version `4.5.0` in the top level file | The top level `build.gradle` uses `classpath 'com.google.gms:google-services:4.3.10'`. Adding the plugin a second time with another version makes Gradle fail with "plugin is already on the classpath". |
| Plugin in the app `plugins { }` block | `app/build.gradle` applies it at the bottom, **only if `app/google-services.json` exists**. |
| `firebase-bom:35.1.0` | `app/build.gradle` uses **one** BoM, `firebase-bom:29.3.1`. Newer BoMs need `compileSdk 34` or higher and a newer Android Gradle Plugin. Two BoMs in one file confuse the version resolution. |
| `firebase-analytics` | Not used by the app, so it is not added. |

The Firebase libraries that **are** used:

```
implementation platform('com.google.firebase:firebase-bom:29.3.1')
implementation 'com.google.firebase:firebase-auth'        // accounts
implementation 'com.google.firebase:firebase-firestore'   // profile and recording summaries
implementation 'com.google.firebase:firebase-database'    // raw samples and live status
implementation 'androidx.work:work-runtime:2.7.1'         // background upload queue
```

Manifest permissions already present: `INTERNET`, `ACCESS_NETWORK_STATE`.

## 3. Where the Firebase code is

| File (`app/src/main/java/com/pocketlab/app/`) | Purpose |
|---|---|
| `cloud/AuthManager.java` | Sign in, register, password reset, sign out, display name. `isConfigured()` is false when `google-services.json` is missing. |
| `LoginActivity.java`, `res/layout/activity_login.xml` | The login page. |
| `cloud/ProfileManager.java` | Mirrors name and email to Firestore `users/{uid}`. |
| `cloud/FirestoreCloudStore.java`, `cloud/CloudMapper.java` | Recording summaries in Firestore `users/{uid}/sessions/{id}`. |
| `cloud/RtdbSampleStore.java` | Raw samples in Realtime Database `samples/{uid}/{id}/{n}`. |
| `cloud/LiveStatus.java` | "Recording now" status in `status/{uid}`. |
| `cloud/SyncEngine.java`, `SyncPlanner.java`, `RoomLocalStore.java`, `CloudSync.java` | The sync algorithm (no Android code, fully unit tested). |
| `work/SyncWorker.java`, `work/SyncScheduler.java` | WorkManager jobs: upload when a connection exists. |
| `cloud/ConnectivityMonitor.java` | Tells the UI when the phone goes offline or online (offline banner). |

## 4. How to test that it works

1. Run the app. The login page opens. Tap **Create account**, enter name, email and a password of 6 or more characters.
2. In the console, Authentication → Users shows the new user.
3. Record a few seconds in the Recorder, press Stop. Wait about 30 seconds.
4. Console → Firestore: `users/{uid}/sessions` has one document. Console → Realtime Database: `samples/{uid}/...` has data.
5. **Offline test:** turn on airplane mode. A red banner stays on top. Record something. The banner says
   "1 item will upload when you are back online". Turn airplane mode off. The banner says "Back online. Saving 1 item to the
   cloud", then disappears. The new recording appears in the console.

## 5. Common problems

| Problem | Cause and fix |
|---|---|
| Login page says "Cloud backup is not set up in this build" | `app/google-services.json` is missing or has a different package name. Put the file in `app/` and sync. |
| `PERMISSION_DENIED` in the sync message | The rules in step 4 or 5 were not published, or you are not signed in. |
| Summaries sync but raw data does not | The Realtime Database was created after you downloaded `google-services.json`. Download it again (step 6). |
| Gradle error "plugin is already on the classpath" | The google-services plugin was added in the top level `plugins { }` block. Remove it there. Keep the `classpath` line. |
| "Duplicate class" or "requires compileSdk 34" errors | A newer Firebase BoM was added. Keep `firebase-bom:29.3.1`. |
| Items never upload on mobile data | In Cloud backup and updates, switch off **Only on Wi-Fi**. |
| Login says "No internet connection" | The phone is offline. Connect, or tap **Continue without an account**. |

## 6. Upgrading Firebase later

Newer Firebase versions need a newer toolchain: Android Gradle Plugin 8+, Gradle 8+, JDK 17 and `compileSdk` 34 or higher.
Upgrade all of these together, then replace the BoM version (still only one BoM) and the `google-services` classpath version.
