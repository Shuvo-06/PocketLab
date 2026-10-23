package com.pocketlab.app.data;

/**
 * Cleans up after an interrupted app. A recording that never finished (the process was killed,
 * the phone ran out of battery) still has the samples that were already written. Those are kept
 * and finished with a "(recovered)" suffix; unfinished sessions with no samples are removed, as
 * are sessions the user deleted. Call from a background thread.
 */
public final class SessionRecovery {

    private SessionRecovery() {}

    /** @return how many sessions were recovered */
    public static int run(AppDatabase db) {
        db.sessionDao().purgeTrashed();
        int recovered = 0;
        for (SessionEntity s : db.sessionDao().incomplete()) {
            int count = db.sampleDao().count(s.id);
            if (count == 0) {
                db.sessionDao().delete(s.id);
            } else {
                db.sessionDao().recover(s.id, s.name + " (recovered)", db.sampleDao().lastTime(s.id), count);
                recovered++;
            }
        }
        return recovered;
    }
}
