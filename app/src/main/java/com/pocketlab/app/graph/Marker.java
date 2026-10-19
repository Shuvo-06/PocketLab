package com.pocketlab.app.graph;

/** A labelled event on the time axis (seconds since the recording started). */
public final class Marker {
    private final double timeSeconds;
    private final String label;

    public Marker(double timeSeconds, String label) {
        this.timeSeconds = timeSeconds;
        this.label = label;
    }

    public double getTimeSeconds() { return timeSeconds; }
    public String getLabel() { return label; }
}
