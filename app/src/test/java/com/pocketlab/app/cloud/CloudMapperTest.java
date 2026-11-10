package com.pocketlab.app.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.graph.Marker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudMapperTest {

    static CloudSession sample() {
        return new CloudSession("Pendulum run", 1_000L, "ACCELEROMETER", "GAME", "EXPERIMENT",
                "pendulum", "Simple pendulum", 12.5, 625, "Measured g: 9.8 m/s²",
                Arrays.asList(new Marker(1.5, "M1")),
                Arrays.asList(new CloudSession.Stat("X", 0.1, 1.2, -2, 2),
                        new CloudSession.Stat("Y", 0.0, Double.NaN, 0, 0)), 5_000L);
    }

    private static void rejects(Map<String, Object> m, String fragment) {
        try {
            CloudMapper.fromMap(m);
            throw new AssertionError("accepted: " + m);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    @Test
    public void roundTripKeepsEverything() {
        CloudSession back = CloudMapper.fromMap(CloudMapper.toMap(sample()));
        assertEquals("Pendulum run", back.name);
        assertEquals(1_000L, back.createdAtMillis);
        assertEquals("ACCELEROMETER", back.sensor);
        assertEquals("pendulum", back.experimentId);
        assertEquals(12.5, back.durationSeconds, 0);
        assertEquals(625, back.sampleCount);
        assertEquals("Measured g: 9.8 m/s²", back.resultText);
        assertEquals(5_000L, back.modifiedAt);
        assertEquals(1, back.markers.size());
        assertEquals("M1", back.markers.get(0).getLabel());
        assertEquals(2, back.stats.size());
        assertEquals(1.2, back.stats.get(0).stdDev, 0);
        assertTrue(Double.isNaN(back.stats.get(1).stdDev));   // NaN is stored as null and read back as NaN
    }

    @Test
    public void numbersFromFirestoreMayBeIntegersOrDoubles() {
        Map<String, Object> m = CloudMapper.toMap(sample());
        m.put("durationSeconds", 12);       // Firestore may hand back Long/Integer for whole numbers
        m.put("sampleCount", 625.0);
        CloudSession s = CloudMapper.fromMap(m);
        assertEquals(12.0, s.durationSeconds, 0);
        assertEquals(625, s.sampleCount);
    }

    @Test
    public void damagedOrHostileDocumentsAreRejected() {
        rejects(null, "empty");
        Map<String, Object> m = CloudMapper.toMap(sample());
        m.put("schema", 2L);
        rejects(m, "schema");
        m = CloudMapper.toMap(sample()); m.remove("name");
        rejects(m, "name");
        m = CloudMapper.toMap(sample()); m.put("name", "   ");
        rejects(m, "empty");
        m = CloudMapper.toMap(sample()); m.put("sensor", "RADAR");
        rejects(m, "sensor");
        m = CloudMapper.toMap(sample()); m.put("durationSeconds", "long");
        rejects(m, "not a number");
        m = CloudMapper.toMap(sample()); m.put("sampleCount", -1L);
        rejects(m, "negative");
        m = CloudMapper.toMap(sample()); m.put("durationSeconds", Double.NaN);
        rejects(m, "finite");
        m = CloudMapper.toMap(sample()); m.put("markers", "oops");
        rejects(m, "not a list");
        m = CloudMapper.toMap(sample());
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 1001; i++) many.add(new HashMap<String, Object>());
        m.put("markers", many);
        rejects(m, "too many");
    }

    @Test
    public void overlongTextIsClipped() {
        Map<String, Object> m = CloudMapper.toMap(sample());
        m.put("name", new String(new char[500]).replace('\0', 'n'));
        assertEquals(80, CloudMapper.fromMap(m).name.length());
    }

    @Test
    public void restoredTextAddsBackedUpSummaryWithoutDuplicating() {
        String text = CloudMapper.restoredResultText(sample());
        assertTrue(text.startsWith("Measured g: 9.8 m/s²"));
        assertTrue(text.contains("X: mean 0.1000 · σ 1.2000 · min -2.0000 · max 2.0000 m/s²"));
        assertTrue(text.contains("Y: mean 0.0000 · σ –"));

        CloudSession noStats = new CloudSession("n", 0, "LIGHT", "UI", "FREE", null, null, 1, 1, null,
                Collections.emptyList(), Collections.emptyList(), 1);
        assertNull(CloudMapper.restoredResultText(noStats));
    }

    @Test
    public void sampleChunksRoundTripAndDefaultForOldBackups() {
        CloudSession withChunks = sample().withSampleChunks(7);
        assertEquals(7, CloudMapper.fromMap(CloudMapper.toMap(withChunks)).sampleChunks);
        Map<String, Object> old = CloudMapper.toMap(sample());
        old.remove("sampleChunks");                                   // backups made before raw data was uploaded
        assertEquals(0, CloudMapper.fromMap(old).sampleChunks);
    }

    @Test
    public void absurdChunkCountsAreRejected() {
        Map<String, Object> m = CloudMapper.toMap(sample());
        m.put("sampleChunks", 5_000_000L);
        rejects(m, "sampleChunks");
        m.put("sampleChunks", -1L);
        rejects(m, "sampleChunks");
    }

    @Test
    public void restoredTextOmitsStatsWhenRawDataWasRestored() {
        String withRaw = CloudMapper.restoredResultText(sample(), false);
        assertEquals("Measured g: 9.8 m/s²", withRaw);
    }
}
