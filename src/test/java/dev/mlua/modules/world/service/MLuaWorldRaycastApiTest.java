package dev.mlua.modules.world.service;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaWorldRaycastApiTest {
    @Test
    void normalizesLargeFiniteDirectionsWithoutOverflow() {
        Vec3 direction = MLuaWorldRaycastApi.normalizedDirection(
            new double[] { 1.0e308, -1.0e308, 1.0e308 }, "World:Raycast");

        assertEquals(1.0 / Math.sqrt(3.0), direction.x, 1.0e-12);
        assertEquals(-1.0 / Math.sqrt(3.0), direction.y, 1.0e-12);
        assertEquals(1.0 / Math.sqrt(3.0), direction.z, 1.0e-12);
    }

    @Test
    void returnsNoDirectionForZeroAndRejectsNonFiniteComponents() {
        assertNull(MLuaWorldRaycastApi.normalizedDirection(new double[] { 0.0, 0.0, 0.0 }, "World:Raycast"));
        assertThrows(LuaError.class, () -> MLuaWorldRaycastApi.normalizedDirection(
            new double[] { 1.0, Double.POSITIVE_INFINITY, 0.0 }, "World:Raycast"));
    }
}
