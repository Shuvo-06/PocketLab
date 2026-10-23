# Cloud setup (optional)

The app **builds and runs without any of this**. Without Firebase the Cloud screen says "not set up" and
everything else works. Two independent options:

## A. Cloud backup (Firebase)

1. In the [Firebase console](https://console.firebase.google.com) create a project and **add an Android app**
   with package name `com.pocketlab.app`.
2. Download **`google-services.json`** and put it in the **`app/`** folder (next to `app/build.gradle`).
   The build detects it and applies the Google Services plugin automatically.
3. **Authentication → Sign-in method → Email/Password → Enable.**
4. **Firestore Database → Create database**, then set these **security rules** (each user can only touch their own data):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
5. Sync the project and run. Open the toolbar menu **⋮ → Cloud backup and updates**, create an account, then
   **Back up now**.

What is stored: `users/{uid}/sessions/{id}`, a summary per recording (name, date, sensor, duration, sample
count, result text, markers, per-axis statistics). Raw samples are **not** uploaded.
Deleting a recording on the phone does not delete its backup.

If you see "The cloud refused access", the security rules are missing or wrong.

## B. Experiment server (downloadable experiment definitions)

1. Host a file in the same format as `app/src/main/assets/experiments.json` at an **https://** address, for example a
   file in a GitHub repository (use the "raw" URL).
2. Put the address in `app/src/main/res/values/config.xml` (`experiments_url`).
3. The app checks once a day (and when you press **Check for updates**). It uses a conditional request, validates the
   file with its own parser, and only then replaces its cache. If the server is down or the file is invalid, the last
   good copy (or the built-in experiments) keeps working.

Leave `experiments_url` empty to use only the built-in experiments.
