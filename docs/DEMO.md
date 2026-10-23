# Demonstration and submission guide

## 10-minute demo script

| Min | Show | Say / point out |
|---|---|---|
| 0:00 | **Sensors** tab | live values, units, measured Hz vs requested, missing sensor handled gracefully |
| 1:30 | **Recorder** | pick Accelerometer / Game, Start, shake, add a marker, pinch-zoom, Pause/Resume, Stop → summary |
| 3:00 | Press Home, lock the screen, come back | foreground service + notification + Stop; recording survived (mention wake lock and battery rule) |
| 4:00 | **Experiments → Simple pendulum** | JSON-driven wizard, input validation, run, g with uncertainty and % error |
| 5:30 | **History** | search, open a session (graph restored from the database), rename, swipe-delete → Undo |
| 6:30 | **Share → PDF report**, then CSV | FileProvider + share sheet; open the PDF |
| 7:15 | **About → Provider demo** | ContentResolver queries on the custom read-only provider |
| 8:00 | **Cloud** screen | offline state, sign in, Back up now, auto backup with WorkManager, restore on a second device |
| 9:00 | Architecture slide | pure-Java core + tests, interfaces at the Android boundary |

## Screenshots to take (for the report)

1. Sensors dashboard (live values) · 2. Recorder mid-recording with graph + markers · 3. Recording notification
(shade) · 4. Experiment wizard: instruction, input, record, results · 5. History list with search · 6. Session detail ·
7. Share sheet · 8. PDF report (page 1) · 9. Provider demo dialog · 10. Cloud screen (signed in) · 11. Firestore console
showing a backup · 12. Dark mode.

## Demonstration video (about 3 minutes)

Screen-record steps 1–7 of the script on a real phone. Include the notification and the PDF.

## Report outline

1. Introduction, problem statement, objectives (from the proposal)
2. System overview and screenshots
3. Architecture (`ARCHITECTURE.md`), including the layering and the testability decision
4. Sensors and recording (time axis, bounded memory, background service)
5. Scientific analysis (`ALGORITHMS.md`), validation with synthetic data
6. Guided-experiment framework (JSON config, wizard, four experiments)
7. Persistence (`DATABASE.md`), recovery, migration
8. Export/import (formats, PDF, XML parsing, security checks)
9. System components (service, receiver, provider) and Android 12–14 restrictions
10. Networking and cloud (offline-first, conflict handling)
11. Testing (`TESTING.md`), results, limitations
12. Syllabus coverage matrix (`ANDROID_COMPONENTS.md`)
13. Conclusion and future work: frequency spectrum, more experiments, Google sign-in, uploading raw data,
    Android 14 targeting, Kotlin/Compose migration

## Presentation outline (10 slides)

Problem → Idea → Live demo → Architecture → Sensors + time axis → Experiments → Background + system components →
Data: history/export/cloud → Testing → Conclusion/future work.
