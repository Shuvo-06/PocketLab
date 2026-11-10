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

    /** The same result with an extra paragraph (for example "weather reference unavailable") appended to the message. */
    public ExperimentResult withNote(String note) {
        return new ExperimentResult(ok, message + "\n\n" + note, new ArrayList<>(lines));
    }

    /** Labelled values followed by the interpretation, as plain text (stored in history). */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) sb.append(l.label).append(": ").append(l.value).append('\n');
        if (sb.length() > 0) sb.append('\n');
        return sb.append(message).toString();
    }

    public boolean isOk() { return ok; }
    /** The interpretation when {@link #isOk()}, otherwise what went wrong and how to fix it. */
    public String getMessage() { return message; }
    public List<Line> getLines() { return lines; }
}
