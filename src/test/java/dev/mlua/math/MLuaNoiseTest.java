package dev.mlua.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaNoiseTest {
    @Test
    void fixedAritySamplingMatchesThePreviousLatticeAlgorithm() {
        assertMatchesReference(0.125);
        assertMatchesReference(-1.25, 2.5);
        assertMatchesReference(3.75, -4.125, 0.625);
        assertMatchesReference(-8.5, 2.25, 7.125, -0.75);
        assertMatchesReference(0.375, -2.625, 4.875, 1.5, -3.25);
    }

    private static void assertMatchesReference(double... coordinates) {
        assertEquals(reference(coordinates), MLuaNoise.sample(coordinates), 0.0);
        assertEquals(reference(coordinates), fixedArity(coordinates), 0.0);
    }

    private static double fixedArity(double[] coordinates) {
        return switch (coordinates.length) {
            case 1 -> MLuaNoise.sample(coordinates[0]);
            case 2 -> MLuaNoise.sample(coordinates[0], coordinates[1]);
            case 3 -> MLuaNoise.sample(coordinates[0], coordinates[1], coordinates[2]);
            case 4 -> MLuaNoise.sample(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
            case 5 -> MLuaNoise.sample(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4]);
            default -> throw new AssertionError("Unexpected coordinate count: " + coordinates.length);
        };
    }

    private static double reference(double... coordinates) {
        int dimensions = coordinates.length;
        long[] lower = new long[dimensions];
        double[] blend = new double[dimensions];
        for (int axis = 0; axis < dimensions; axis++) {
            double coordinate = coordinates[axis];
            double floor = Math.floor(coordinate);
            lower[axis] = (long)floor;
            double fraction = coordinate - floor;
            blend[axis] = fraction * fraction * fraction * (fraction * (fraction * 6.0 - 15.0) + 10.0);
        }

        double result = 0.0;
        for (int corner = 0; corner < (1 << dimensions); corner++) {
            double weight = 1.0;
            long hash = mix64(0x9E3779B97F4A7C15L ^ dimensions);
            for (int axis = 0; axis < dimensions; axis++) {
                boolean upper = (corner & (1 << axis)) != 0;
                long lattice = lower[axis] + (upper ? 1L : 0L);
                weight *= upper ? blend[axis] : 1.0 - blend[axis];
                hash = mix64(hash ^ mix64(lattice + 0x9E3779B97F4A7C15L * (axis + 1L)));
            }
            double value = ((hash >>> 11) * 0x1.0p-52) - 1.0;
            result += value * weight;
        }
        return Math.max(-1.0, Math.min(1.0, result));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
