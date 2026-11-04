package dev.mlua.modules.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaEntityBoundsTest {
    @Test
    void choosesTheNearestFaceAndKeepsThePreviousTieOrder() {
        AABB bounds = new AABB(0, 0, 0, 2, 2, 2);

        Vec3 nearPositiveY = MLuaEntityBounds.rayHitNormal(bounds, new Vec3(1, 1.9, 1));
        assertEquals(new Vec3(0, 1, 0), nearPositiveY);

        Vec3 centerTie = MLuaEntityBounds.rayHitNormal(bounds, new Vec3(1, 1, 1));
        assertEquals(new Vec3(-1, 0, 0), centerTie);
    }

    @Test
    void reusesImmutableAxisNormalsAcrossRayHits() {
        AABB bounds = new AABB(0, 0, 0, 2, 2, 2);
        Vec3 first = MLuaEntityBounds.rayHitNormal(bounds, new Vec3(0, 1, 1));
        Vec3 second = MLuaEntityBounds.rayHitNormal(bounds, new Vec3(0, 0.5, 1.5));

        assertSame(first, second);
    }
}
