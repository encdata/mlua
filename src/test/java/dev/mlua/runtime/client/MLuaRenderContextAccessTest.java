package dev.mlua.runtime.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MLuaRenderContextAccessTest {
    @Test
    void resolvesContextAccessorsAcrossAlternateNames() {
        ContextFixture context = new ContextFixture();

        assertEquals("pose-stack", MLuaRenderContextAccess.optional(context, "matrixStack", "poseStack"));
    }

    @Test
    void convertsCameraPositionsAndRejectsNonFiniteCoordinates() {
        assertEquals(new Vec3(2.0D, 3.0D, 4.0D),
            MLuaRenderContextAccess.cameraPosition(new CameraFixture()));
        assertEquals(new Vec3(5.0D, 6.0D, 7.0D),
            MLuaRenderContextAccess.cameraPosition(new Vec3(5.0D, 6.0D, 7.0D)));
        assertNull(MLuaRenderContextAccess.cameraPosition(new InvalidCameraFixture()));
    }

    @Test
    void convertsLookDirectionAndKeepsTheEstablishedFallback() {
        assertEquals(new Vec3(0.0D, 1.0D, 0.0D),
            MLuaRenderContextAccess.cameraLook(new LookCameraFixture()));
        assertEquals(new Vec3(0.0D, 0.0D, 1.0D), MLuaRenderContextAccess.cameraLook(new Object()));
    }

    public static final class ContextFixture {
        public Object poseStack() { return "pose-stack"; }
    }

    public static final class CameraFixture {
        public VectorFixture getPosition() { return new VectorFixture(2.0D, 3.0D, 4.0D); }
    }

    public static final class LookCameraFixture {
        public VectorFixture getLookDirection() { return new VectorFixture(0.0D, 1.0D, 0.0D); }
    }

    public static final class InvalidCameraFixture {
        public VectorFixture position() { return new VectorFixture(Double.NaN, 0.0D, 0.0D); }
    }

    public static final class VectorFixture {
        private final double x;
        private final double y;
        private final double z;

        public VectorFixture(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
    }
}
