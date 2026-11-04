package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Applies temporary MLua model-part changes and restores Minecraft's shared model state. */
final class MLuaModelPartPoseAccess {
    private static final Map<Object, IdentityHashMap<ModelPart, PartPose>> OVERRIDE_POSES = new WeakHashMap<>();
    private static final Field MODEL_PART_X_SCALE = resolveField("xScale");
    private static final Field MODEL_PART_Y_SCALE = resolveField("yScale");
    private static final Field MODEL_PART_Z_SCALE = resolveField("zScale");

    private MLuaModelPartPoseAccess() { }

    static void restorePendingOverrides() {
        Runnable restore = () -> {
            IdentityHashMap<Object, IdentityHashMap<ModelPart, PartPose>> pending;
            synchronized (OVERRIDE_POSES) {
                if (OVERRIDE_POSES.isEmpty()) return;
                pending = new IdentityHashMap<>(OVERRIDE_POSES);
                OVERRIDE_POSES.clear();
            }
            pending.values().forEach(poses -> poses.forEach((part, pose) -> pose.restore(part)));
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || MLuaClientThread.isClientThread()) restore.run();
        else minecraft.execute(restore);
    }

    static void restoreOverrides(Object model) {
        if (model == null) return;
        IdentityHashMap<ModelPart, PartPose> poses;
        synchronized (OVERRIDE_POSES) {
            poses = OVERRIDE_POSES.remove(model);
        }
        if (poses != null) poses.forEach((part, pose) -> pose.restore(part));
    }

    static void rememberOverridePose(Object model, ModelPart part) {
        if (model == null || part == null) return;
        synchronized (OVERRIDE_POSES) {
            OVERRIDE_POSES.computeIfAbsent(model, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(part, PartPose::capture);
        }
    }

    static void restoreOverridePose(Object model, ModelPart part) {
        if (model == null || part == null) return;
        PartPose pose;
        synchronized (OVERRIDE_POSES) {
            IdentityHashMap<ModelPart, PartPose> poses = OVERRIDE_POSES.get(model);
            if (poses == null) return;
            pose = poses.remove(part);
            if (poses.isEmpty()) OVERRIDE_POSES.remove(model);
        }
        if (pose != null) pose.restore(part);
    }

    static void applyState(Object model, ModelPart part, MLuaClientModelManager.PartState state) {
        if (part == null || state == null) return;
        rememberOverridePose(model, part);
        if (state.cframe() != null) applyCFrameDelta(part, state.cframe());
        LuaTable offset = state.offset();
        if (offset != null) {
            part.x += (float) (component(offset, "X") * 16.0);
            part.y += (float) (component(offset, "Y") * 16.0);
            part.z += (float) (component(offset, "Z") * 16.0);
        }
        if (state.visible() != null) part.visible = state.visible();
        LuaTable scale = state.scale();
        if (scale != null) {
            applyScale(part, (float) component(scale, "X"), (float) component(scale, "Y"),
                (float) component(scale, "Z"));
        }
    }

    static void applyCFrameDelta(ModelPart part, MLuaCFrame delta) {
        if (part == null || delta == null) return;
        // Script CFrame positions use blocks; ModelPart pivots use pixels.
        part.x += (float) (delta.x() * 16.0);
        part.y += (float) (delta.y() * 16.0);
        part.z += (float) (delta.z() * 16.0);
        // Compose after Minecraft's Z * Y * X base pose so the script transform preserves vanilla animation.
        double sx = Math.sin(part.xRot), cx = Math.cos(part.xRot);
        double sy = Math.sin(part.yRot), cy = Math.cos(part.yRot);
        double sz = Math.sin(part.zRot), cz = Math.cos(part.zRot);
        double a00 = cz * cy, a01 = cz * sy * sx - sz * cx, a02 = cz * sy * cx + sz * sx;
        double a10 = sz * cy, a11 = sz * sy * sx + cz * cx, a12 = sz * sy * cx - cz * sx;
        double a20 = -sy, a21 = cy * sx, a22 = cy * cx;
        double m00 = a00 * delta.r00() + a01 * delta.r10() + a02 * delta.r20();
        double m01 = a00 * delta.r01() + a01 * delta.r11() + a02 * delta.r21();
        double m10 = a10 * delta.r00() + a11 * delta.r10() + a12 * delta.r20();
        double m11 = a10 * delta.r01() + a11 * delta.r11() + a12 * delta.r21();
        double m20 = a20 * delta.r00() + a21 * delta.r10() + a22 * delta.r20();
        double m21 = a20 * delta.r01() + a21 * delta.r11() + a22 * delta.r21();
        double m22 = a20 * delta.r02() + a21 * delta.r12() + a22 * delta.r22();
        double composedY = Math.asin(clamp(-m20, -1.0, 1.0));
        double cosY = Math.cos(composedY);
        double composedX;
        double composedZ;
        if (Math.abs(cosY) > 1.0e-6) {
            composedX = Math.atan2(m21, m22);
            composedZ = Math.atan2(m10, m00);
        } else {
            composedX = 0.0;
            composedZ = Math.atan2(-m01, m11);
        }
        part.xRot = (float) composedX;
        part.yRot = (float) composedY;
        part.zRot = (float) composedZ;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record PartPose(float x, float y, float z, float xRot, float yRot, float zRot,
                            boolean visible, float xScale, float yScale, float zScale) {
        private static PartPose capture(ModelPart part) {
            return new PartPose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.visible,
                readScale(MODEL_PART_X_SCALE, part), readScale(MODEL_PART_Y_SCALE, part),
                readScale(MODEL_PART_Z_SCALE, part));
        }

        private void restore(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.visible = visible;
            trySet(MODEL_PART_X_SCALE, part, xScale);
            trySet(MODEL_PART_Y_SCALE, part, yScale);
            trySet(MODEL_PART_Z_SCALE, part, zScale);
        }
    }

    private static float readScale(Field field, ModelPart part) {
        if (field == null) return 1.0F;
        try {
            return field.getFloat(part);
        } catch (IllegalAccessException ignored) {
            return 1.0F;
        }
    }

    private static double component(LuaTable vector, String key) {
        LuaValue value = vector.get(key);
        if (value.isnil()) value = vector.get(key.toLowerCase());
        return value.optdouble(0.0);
    }

    private static void applyScale(ModelPart part, float x, float y, float z) {
        trySet(MODEL_PART_X_SCALE, part, x);
        trySet(MODEL_PART_Y_SCALE, part, y);
        trySet(MODEL_PART_Z_SCALE, part, z);
    }

    private static void trySet(Field field, ModelPart part, float value) {
        if (field == null) return;
        try {
            field.setFloat(part, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field resolveField(String name) {
        try {
            Field field = ModelPart.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
