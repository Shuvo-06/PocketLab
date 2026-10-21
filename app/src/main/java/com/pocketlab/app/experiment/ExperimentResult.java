package com.pocketlab.app.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of analysing a recording: either labelled values plus an interpretation, or an error. */
public final class ExperimentResult {

    public static final class Line {
        public final String label;
        public final String value;

        Line(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private final boolean ok;
    private final String message; // interpretation when ok, error text otherwise
    private final List<Line> lines;

    private ExperimentResult(boolean ok, String message, List<Line> lines) {
        this.ok = ok;
        this.message = message;
        this.lines = Collections.unmodifiableList(lines);
    }

    static ExperimentResult failure(String error) {
        return new ExperimentResult(false, error, new ArrayList<>());
    }

    static ExperimentResult success(List<Line> lines, String interpretation) {
        return new ExperimentResult(true, interpretation, lines);
    }

    public boolean isOk() { return ok; }
    /** The interpretation when {@link #isOk()}, otherwise what went wrong and how to fix it. */
    public String getMessage() { return message; }
    public List<Line> getLines() { return lines; }
}
