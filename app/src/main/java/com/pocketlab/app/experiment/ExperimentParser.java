package com.pocketlab.app.experiment;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the experiments JSON into {@link ExperimentDefinition}s, rejecting bad configuration. */
public final class ExperimentParser {

    private ExperimentParser() {}

    public static List<ExperimentDefinition> parse(String json) throws JSONException {
        JSONArray arr = new JSONObject(json).getJSONArray("experiments");
        List<ExperimentDefinition> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            ExperimentDefinition def = parseOne(arr.getJSONObject(i));
            if (!ids.add(def.getId())) throw new JSONException("duplicate experiment id: " + def.getId());
            out.add(def);
        }
        return out;
    }

    private static ExperimentDefinition parseOne(JSONObject o) throws JSONException {
        String id = o.getString("id");
        try {
            List<ExperimentDefinition.InputSpec> inputs = new ArrayList<>();
            JSONArray in = o.optJSONArray("inputs");
            for (int i = 0; in != null && i < in.length(); i++) {
                JSONObject x = in.getJSONObject(i);
                inputs.add(new ExperimentDefinition.InputSpec(x.getString("key"), x.getString("label"),
                        x.optString("unit", ""), x.optString("hint", ""),
                        x.getDouble("min"), x.getDouble("max"), x.optBoolean("required", true)));
            }

            List<ExperimentDefinition.Step> steps = new ArrayList<>();
            JSONArray st = o.getJSONArray("steps");
            for (int i = 0; i < st.length(); i++) {
                JSONObject x = st.getJSONObject(i);
                steps.add(new ExperimentDefinition.Step(x.getString("title"), x.getString("text")));
            }

            List<ExperimentDefinition.Reference> refs = new ArrayList<>();
            JSONArray rs = o.optJSONArray("references");
            for (int i = 0; rs != null && i < rs.length(); i++) {
                JSONObject x = rs.getJSONObject(i);
                String unit = x.optString("unit", "");
                refs.add(x.has("value")
                        ? new ExperimentDefinition.Reference(x.getString("label"), unit,
                                x.getDouble("value"), Double.NaN, Double.NaN)
                        : new ExperimentDefinition.Reference(x.getString("label"), unit,
                                Double.NaN, x.getDouble("min"), x.getDouble("max")));
            }

            Map<String, Double> params = new HashMap<>();
            JSONObject p = o.optJSONObject("params");
            if (p != null) {
                for (Iterator<String> it = p.keys(); it.hasNext(); ) {
                    String k = it.next();
                    params.put(k, p.getDouble(k));
                }
            }

            return new ExperimentDefinition(id, o.getString("title"), o.getString("summary"),
                    SensorType.valueOf(o.getString("sensor")),
                    SamplingRate.valueOf(o.optString("sampleRate", "GAME")),
                    o.getDouble("minSeconds"), o.getDouble("maxSeconds"),
                    o.optString("recordHint", ""),
                    ExperimentDefinition.Method.valueOf(o.getString("method")),
                    inputs, steps, refs, params);
        } catch (IllegalArgumentException e) {
            throw new JSONException("Experiment '" + id + "': " + e.getMessage());
        }
    }
}
