package com.pocketlab.app.data;

import java.util.List;

/** Everything the detail screen needs. {@code samples} may be a decimated subset of {@code totalSamples}. */
public final class SessionDetail {
    public final SessionEntity session;
    public final List<SampleEntity> samples;
    public final List<MarkerEntity> markers;
    public final int totalSamples;

    public SessionDetail(SessionEntity session, List<SampleEntity> samples,
                         List<MarkerEntity> markers, int totalSamples) {
        this.session = session;
        this.samples = samples;
        this.markers = markers;
        this.totalSamples = totalSamples;
    }
}
