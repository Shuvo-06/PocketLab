package com.pocketlab.app.export;

import java.io.IOException;

/** A recording's samples that can be read from the start more than once, in chunks. */
public interface SampleSource {

    Cursor open() throws IOException;

    interface Cursor {
        /**
         * Fills {@code times[0..n)} and {@code values[axis][0..n)} with up to {@code max} samples
         * and returns n; returns 0 at the end.
         */
        int read(double[] times, float[][] values, int max) throws IOException;
    }
}
