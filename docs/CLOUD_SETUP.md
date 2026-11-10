# Cloud setup (Firebase and the optional experiment server)

The app **builds and runs without any of this**. Without Firebase the login page offers only "Continue without an
account", and everything works on the phone alone. To get **accounts, profile, backup across devices, live status**, do
section A once.

## A. Firebase

Short checklist for this project: **`FIREBASE_SETUP.md`**. The full steps are below.

1. In the [Firebase console](https://console.firebase.google.com) create a project and **add an Android app** with the
   package name `com.pocketlab.app`.
2. Download **`google-services.json`** and put it in the **`app/`** folder (next to `app/build.gradle`). The build detects
   it and applies the Google Services plugin automatically. Sync the project.
3. **Authentication → Sign-in method → Email/Password → Enable.**
4. **Firestore Database → Create database.** Then open **Rules** and publish:
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
5. **Realtime Database → Create database** (pick a location, start in locked mode). Then open **Rules** and publish:
   ```
   {
     "rules": {
       "samples": { "$uid": { ".read": "auth != null && auth.uid == $uid", ".write": "auth != null && auth.uid == $uid" } },
       "status":  { "$uid": { ".read": "auth != null && auth.uid == $uid", ".write": "auth != null && auth.uid == $uid" } }
     }
   }
   ```
   **After creating the Realtime Database, download `google-services.json` again** (it now contains the database address)
   and replace the old file. Without it the app still works, but raw measurements are not uploaded and live status is off
   (summaries still sync).
6. Run the app. Create an account on the login page. A first sync starts automatically.

### What is stored where

| Data | Service | Path |
|---|---|---|
| Account (email, password hash, name) | Firebase Authentication | |
| Profile mirror (email, name) | Cloud Firestore | `users/{uid}` |
| Recording summaries (name, date, sensor, duration, result text, markers, statistics) | Cloud Firestore | `users/{uid}/sessions/{id}` |
| Raw measurements | Realtime Database | `samples/{uid}/{id}/{n}` (500 samples per node) |
| "Recording now" status | Realtime Database | `status/{uid}` |

Everything is scoped to the signed-in user by the rules above. On the phone, every recording is also tagged with its
owner, so two people using the same phone never see each other's recordings.

### How data follows the user to another device

Sign in on the second phone: the first sync downloads every recording (summary **and** raw data, up to 500,000 samples
each) and the profile. Renames and new recordings flow both ways in the background (about every 6 hours on Wi-Fi, and
soon after each recording). Deleting a recording on one phone does not delete the backup.

### Limits and costs

Firebase's free plan is plenty for a course project. Raw data is the largest part (about 20 KB per 500 samples, uploaded
once per recording). You can switch off automatic backup in **Cloud backup and updates** or limit it to Wi-Fi.

## B. Experiment server (downloadable experiment definitions)

1. Host a file in the same format as `app/src/main/assets/experiments.json` at an **https://** address (for example the
   "raw" link of a file in a GitHub repository).
2. Put the address in `app/src/main/res/values/config.xml` (`experiments_url`).
3. The app checks once a day and when you press **Check for updates**. It validates the file with its own parser before
   using it; a bad or unreachable server never breaks the experiments. When new experiments arrive you get a notification.

Leave `experiments_url` empty to use only the built-in experiments.

## C. Other optional settings (`res/values/config.xml`)

* `feedback_email`: where Profile → "Send feedback" writes to (button hidden when empty).

## D. Weather API (no setup)

The Relative height experiment can ask the free **Open-Meteo** service (https://open-meteo.com, no key) for today's
sea-level pressure at the latitude/longitude you enter, to report your altitude above sea level. Free for
non-commercial use; only the two coordinates are sent.
