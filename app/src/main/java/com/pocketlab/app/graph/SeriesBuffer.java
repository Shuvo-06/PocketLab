package com.pocketlab.app.graph;

/**
 * Fixed-capacity ring buffer of (time, value) points for one graph series.
 * When full, the oldest point is overwritten, so memory use stays bounded no matter
 * how long a recording runs. Times must be added in non-decreasing order.
 * Index 0 is always the oldest retained point.
 */
public class SeriesBuffer {

    private final double[] times;
    private final float[] values;
    private int start;
    private int size;

    public SeriesBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        times = new double[capacity];
        values = new float[capacity];
    }

    public void add(double timeSeconds, float value) {
        int idx;
        if (size < times.length) {
            idx = (start + size) % times.length;
            size++;
        } else {
            idx = start;
            start = (start + 1) % times.length;
        }
        times[idx] = timeSeconds;
        values[idx] = value;
    }

    public int size() { return size; }
    public int getCapacity() { return times.length; }

    public double getTime(int i) {
        checkIndex(i);
        return times[(start + i) % times.length];
    }

    public float getValue(int i) {
        checkIndex(i);
        return values[(start + i) % times.length];
    }

    /** Index of the first point whose time is >= t, or size() if there is none. */
    public int lowerBound(double t) {
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (getTime(mid) < t) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    public void clear() {
        start = 0;
        size = 0;
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException("index " + i + ", size " + size);
    }
}
