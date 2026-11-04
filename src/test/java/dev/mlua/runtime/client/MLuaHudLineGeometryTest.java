package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaHudLineGeometryTest {
    @Test
    void horizontalLineUsesSingleSpanIncludingBothEndpoints() {
        var geometry = MLuaHudLineGeometry.from(2, 4, 12, 4, 2);
        assertEquals(11, geometry.lengthPixels());
        assertEquals(2, geometry.thicknessPixels());
        assertEquals(0.0F, geometry.angleRadians());
    }

    @Test
    void diagonalAndZeroLengthLinesRemainVisible() {
        var diagonal = MLuaHudLineGeometry.from(0, 0, 3, 4, 1);
        assertEquals(6, diagonal.lengthPixels());
        assertTrue(diagonal.angleRadians() > 0.0F);
        assertEquals(1, MLuaHudLineGeometry.from(1, 1, 1, 1, 1).lengthPixels());
    }

    @Test
    void rejectsCoordinatesThatCannotProduceFiniteGeometry() {
        assertNull(MLuaHudLineGeometry.from(-Double.MAX_VALUE, 0, Double.MAX_VALUE, 0, 1));
    }
}
