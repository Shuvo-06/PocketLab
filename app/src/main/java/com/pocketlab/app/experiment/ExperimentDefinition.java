package com.pocketlab.app.experiment;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One guided experiment, loaded from JSON. Immutable. */
public final class ExperimentDefinition {

    /** Which analysis routine in {@link ExperimentAnalyzer} runs on the recording. */
    public enum Method {
        PENDULUM_GRAVITY(SensorType.ACCELEROMETER),
        MAGNETIC_FIELD(SensorType.MAGNETOMETER),
        LIGHT_LEVEL(SensorType.LIGHT),
        RELATIVE_HEIGHT(SensorType.BAROMETER);

        final SensorType requiredSensor;

        Method(SensorType requiredSensor) {
            this.requiredSensor = requiredSensor;
        }
    }

    /** One instruction page. */
    public static final class Step {
        public final String title;
        public final String text;

        public Step(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    /** A theoretical/reference value (single value) or a typical range. */
    public static final class Reference {
        public final String label;
        public final String unit;
        public final double value; // NaN for a range
        public final double min;   // NaN for a single value
        public final double max;

        public Reference(String label, String unit, double value, double min, double max) {
            this.label = label;
            this.unit = unit;
            this.value = value;
            this.min = min;
            this.max = max;
        }

        public boolean isRange() { return !Double.isNaN(min); }

        public boolean contains(double x) { return isRange() && x >= min && x < max; }
    }

    /** A number the user must type before recording. */
    public static final class InputSpec {
        public final String key;
        public final String label;
        public final String unit;
        public final String hint;
        public final double min;
        public final double max;
        public final boolean required;

        public InputSpec(String key, String label, String unit, String hint,
                         double min, double max, boolean required) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.hint = hint;
            this.min = min;
            this.max = max;
            this.required = required;
        }

        /**
         * @return the parsed number, or null if blank and optional
         * @throws IllegalArgumentException with a user-readable message when invalid
         */
        public Double parse(String text) {
            String t = text == null ? "" : text.trim().replace(',', '.');
            if (t.isEmpty()) {
                if (required) throw new IllegalArgumentException("Required");
                return null;
            }
            double v;
            try {
                v = Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Enter a number");
            }
            if (Double.isNaN(v) || Double.isInfinite(v) || v < min || v > max) {
                throw new IllegalArgumentException(String.format(Locale.US,
                        "Must be between %s and %s %s", trim(min), trim(max), unit).trim());
            }
            return v;
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
    }

    private final String id;
    private final String title;
    private final String summary;
    private final SensorType sensor;
    private final SamplingRate rate;
    private final double minSeconds;
    private final double maxSeconds;
    private final String recordHint;
    private final Method method;
    private final List<InputSpec> inputs;
    private final List<Step> steps;
    private final List<Reference> references;
    private final Map<String, Double> params;

    public ExperimentDefinition(String id, String title, String summary, SensorType sensor,
                                SamplingRate rate, double minSeconds, double maxSeconds,
                                String recordHint, Method method, List<InputSpec> inputs,
                                List<Step> steps, List<Reference> references,
                                Map<String, Double> params) {
        if (method.requiredSensor != sensor) {
            throw new IllegalArgumentException(method + " needs sensor " + method.requiredSensor
                    + " but the experiment uses " + sensor);
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("at least one step is required");
        if (!(minSeconds > 0) || maxSeconds < minSeconds) {
            throw new IllegalArgumentException("need 0 < minSeconds <= maxSeconds");
        }
        if (method == Method.PENDULUM_GRAVITY) {
            boolean hasLength = false;
            for (InputSpec in : inputs) if (in.key.equals("length") && in.required) hasLength = true;
            if (!hasLength) throw new IllegalArgumentException("pendulum needs a required 'length' input");
            if (references.isEmpty() || references.get(0).isRange()) {
                throw new IllegalArgumentException("pendulum needs a single-value reference");
            }
            int axis = (int) (params.containsKey("axis") ? params.get("axis") : 0);
            if (axis < 0 || axis >= sensor.getAxes()) throw new IllegalArgumentException("bad axis");
        }
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.sensor = sensor;
        this.rate = rate;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.recordHint = recordHint;
        this.method = method;
        this.inputs = Collections.unmodifiableList(inputs);
        this.steps = Collections.unmodifiableList(steps);
        this.references = Collections.unmodifiableList(references);
        this.params = Collections.unmodifiableMap(params);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public SensorType getSensor() { return sensor; }
    public SamplingRate getRate() { return rate; }
    public double getMinSeconds() { return minSeconds; }
    public double getMaxSeconds() { return maxSeconds; }
    public String getRecordHint() { return recordHint; }
    public Method getMethod() { return method; }
    public List<InputSpec> getInputs() { return inputs; }
    public List<Step> getSteps() { return steps; }
    public List<Reference> getReferences() { return references; }

    public double param(String name, double fallback) {
        Double v = params.get(name);
        return v == null ? fallback : v;
    }
}
