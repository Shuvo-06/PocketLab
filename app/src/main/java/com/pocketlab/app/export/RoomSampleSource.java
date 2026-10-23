package com.pocketlab.app.export;

import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.SampleEntity;

import java.util.Collections;
import java.util.List;

/** Reads a saved session's samples from Room in pages, so even huge recordings use little memory. */
public final class RoomSampleSource implements SampleSource {

    private static final int PAGE = 5000;

    private final AppDatabase db;
    private final long sessionId;

    public RoomSampleSource(AppDatabase db, long sessionId) {
        this.db = db;
        this.sessionId = sessionId;
    }

    @Override
    public Cursor open() {
        return new Cursor() {
            private long afterId = 0;
            private List<SampleEntity> page = Collections.emptyList();
            private int pos = 0;
            private boolean lastPage = false;

            @Override
            public int read(double[] times, float[][] values, int max) {
                int n = 0;
                while (n < max) {
                    if (pos >= page.size()) {
                        if (lastPage) break;
                        page = db.sampleDao().page(sessionId, afterId, PAGE);
                        pos = 0;
                        if (page.isEmpty()) break;
                        afterId = page.get(page.size() - 1).id;
                        lastPage = page.size() < PAGE;
                    }
                    SampleEntity s = page.get(pos++);
                    times[n] = s.timeSeconds;
                    values[0][n] = s.v0;
                    values[1][n] = s.v1;
                    values[2][n] = s.v2;
                    n++;
                }
                return n;
            }
        };
    }
}
