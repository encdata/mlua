package dev.mlua.runtime.client;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaRenderHelperTest {
    @Test
    void transformsNormalsUsingThreadLocalScratchAndHandlesZeroLength() {
        Matrix3f normalMatrix = new Matrix3f().scaling(0.5F, 1.0F, 1.0F);
        Vector3f normal = MLuaRenderHelper.transformedNormal(normalMatrix, 1.0F, 1.0F, 0.0F);

        assertEquals(1.0F, normal.length(), 0.0001F);
        assertEquals(1.0F / (float) Math.sqrt(5.0F), normal.x, 0.0001F);
        assertEquals(2.0F / (float) Math.sqrt(5.0F), normal.y, 0.0001F);

        Vector3f fallback = MLuaRenderHelper.transformedNormal(normalMatrix, 0.0F, 0.0F, 0.0F);

        assertSame(normal, fallback);
        assertEquals(0.0F, normal.x, 0.0001F);
        assertEquals(1.0F, normal.y, 0.0001F);
        assertEquals(0.0F, normal.z, 0.0001F);
    }
}
