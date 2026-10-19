package com.pocketlab.app.graph;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SeriesBufferTest {

    @Test
    public void storesPointsInOrder() {
        SeriesBuffer b = new SeriesBuffer(5);
        b.add(0.0, 10f);
        b.add(1.0, 20f);
        assertEquals(2, b.size());
        assertEquals(0.0, b.getTime(0), 0);
        assertEquals(20f, b.getValue(1), 0);
    }

    @Test
    public void overwritesOldestWhenFull() {
        SeriesBuffer b = new SeriesBuffer(3);
        for (int i = 0; i < 7; i++) b.add(i, i * 10f);
        assertEquals(3, b.size());
        assertEquals(4.0, b.getTime(0), 0);   // oldest retained
        assertEquals(6.0, b.getTime(2), 0);   // newest
        assertEquals(60f, b.getValue(2), 0);
    }

    @Test
    public void lowerBoundFindsFirstPointAtOrAfter() {
        SeriesBuffer b = new SeriesBuffer(4);
        for (int i = 0; i < 10; i++) b.add(i * 0.5, i); // keeps t = 3.0, 3.5, 4.0, 4.5
        assertEquals(0, b.lowerBound(0.0));
        assertEquals(0, b.lowerBound(3.0));
        assertEquals(1, b.lowerBound(3.1));
        assertEquals(3, b.lowerBound(4.5));
        assertEquals(4, b.lowerBound(99));
    }

    @Test
    public void clearEmptiesBuffer() {
        SeriesBuffer b = new SeriesBuffer(3);
        b.add(0, 1);
        b.clear();
        assertEquals(0, b.size());
        assertEquals(0, b.lowerBound(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void outOfRangeIndexThrows() {
        new SeriesBuffer(3).getTime(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroCapacityRejected() {
        new SeriesBuffer(0);
    }
}
