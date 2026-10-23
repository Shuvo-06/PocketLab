# Database (Room / SQLite)

File `pocketlab.db`, **version 2**. Foreign keys are enforced by Room. All access is off the main thread.

## Tables

### `sessions`: one saved recording

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `name` | TEXT NOT NULL | user-editable, max 80 chars |
| `createdAt` | INTEGER NOT NULL | wall-clock start (ms); indexed |
| `sensorType` | TEXT NOT NULL | `SensorType` enum name |
| `samplingRate` | TEXT NOT NULL | `SamplingRate` enum name |
| `kind` | TEXT NOT NULL | `FREE`, `EXPERIMENT`, `IMPORTED`, `RESTORED` |
| `experimentId`, `experimentTitle` | TEXT | experiments only |
| `durationSeconds` | REAL | |
| `sampleCount` | INTEGER | |
| `resultText` | TEXT | experiment result (inputs, calculated values, % error) |
| `complete` | INTEGER (bool) | 0 while recording / after a crash |
| `trashed` | INTEGER (bool) | soft delete, allows undo |
| `modifiedAt` | INTEGER NOT NULL DEFAULT 0 | stamped by SQL on finish, rename, recover |
| `cloudId` | TEXT | Firestore document id; null = never backed up |
| `syncedAt` | INTEGER NOT NULL DEFAULT 0 | `modifiedAt` value last exchanged with the cloud |

### `samples`: raw measurements (can be hundreds of thousands per session)

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | insertion order = time order |
| `sessionId` | INTEGER NOT NULL | FK → `sessions.id` **ON DELETE CASCADE**; indexed |
| `timeSeconds` | REAL | seconds since recording start (pauses removed) |
| `v0`, `v1`, `v2` | REAL (float) | axis values; single-axis sensors use only `v0` |

### `markers`: user events on the time axis

`id` PK, `sessionId` FK CASCADE (indexed), `timeSeconds` REAL, `label` TEXT.

## Relationships

```
sessions 1 ───< samples
sessions 1 ───< markers
```
Deleting a session deletes its samples and markers automatically.

## Lifecycle of a row

1. Recording starts → row inserted with `complete = 0`.
2. Samples are inserted in 500-row chunks (one transaction each).
3. Stop → markers inserted and `complete = 1` in **one transaction**.
4. Delete → `trashed = 1` (undo possible); purged for good at the next app start.
5. App/phone died mid-recording → at the next start the row is **recovered**: kept, renamed
   "… (recovered)", duration and count rebuilt from the saved samples; if no samples were saved it is deleted.

## Migration 1 → 2 (cloud sync)

```sql
ALTER TABLE sessions ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sessions ADD COLUMN cloudId TEXT;
ALTER TABLE sessions ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0;
UPDATE sessions SET modifiedAt = createdAt;   -- existing recordings count as "changed": first sync uploads them
```

## Notable queries

| Query | Purpose |
|---|---|
| `search(:query)` | finished, not trashed, name LIKE with ESCAPE so `%` and `_` are literal; newest first |
| `decimated(:id, :step)` | every n-th sample (detail screens cap at 100,000 samples) |
| `page(:id, :afterId, :limit)` | keyset pagination used by exports and the ContentProvider |
| `syncable()`, `countUnsynced()` | cloud sync |
| `incomplete()` + `recover(...)` | crash recovery |
