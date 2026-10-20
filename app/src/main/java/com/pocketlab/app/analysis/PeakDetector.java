package com.pocketlab.app.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Finds local maxima that are prominent and well separated. */
public final class PeakDetector {

    private PeakDetector() {}

    /**
     * @param minProminence       how far a peak must rise above the higher of its two
     *                            surrounding valleys (same unit as the values); rejects noise
     * @param minSeparationSeconds minimum time between two accepted peaks; when two are closer,
     *                            the higher one wins
     * @return indices of accepted peaks in increasing order. The first and last sample are never peaks.
     */
    public static int[] findPeaks(Series s, double minProminence, double minSeparationSeconds) {
        int n = s.size();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < n - 1; i++) {
            double v = s.value(i);
            // ">" on the left and ">=" on the right: a flat top yields its first sample only.
            if (v > s.value(i - 1) && v >= s.value(i + 1) && prominence(s, i) >= minProminence) {
                candidates.add(i);
            }
        }

        // Highest first, then greedily drop anything too close to an already accepted peak.
        List<Integer> byHeight = new ArrayList<>(candidates);
        byHeight.sort((a, b) -> {
            int c = Double.compare(s.value(b), s.value(a));
            return c != 0 ? c : Integer.compare(a, b);
        });
        List<Integer> kept = new ArrayList<>();
        for (int idx : byHeight) {
            boolean ok = true;
            for (int k : kept) {
                if (Math.abs(s.time(idx) - s.time(k)) < minSeparationSeconds) {
                    ok = false;
                    break;
                }
            }
            if (ok) kept.add(idx);
        }
        int[] out = new int[kept.size()];
        for (int i = 0; i < out.length; i++) out[i] = kept.get(i);
        Arrays.sort(out);
        return out;
    }

    /** Peak height above the higher of the lowest points reached on each side before a taller point. */
    static double prominence(Series s, int i) {
        double v = s.value(i);
        double leftMin = v;
        for (int j = i - 1; j >= 0 && s.value(j) <= v; j--) leftMin = Math.min(leftMin, s.value(j));
        double rightMin = v;
        for (int j = i + 1; j < s.size() && s.value(j) <= v; j++) rightMin = Math.min(rightMin, s.value(j));
        return v - Math.max(leftMin, rightMin);
    }
}
