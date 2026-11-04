package dev.mlua.runtime.client;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.phys.Vec3;

/** Resolves loader-specific render-context and camera accessors at the bridge boundary. */
final class MLuaRenderContextAccess {
    private static final ClassValue<ConcurrentMap<String, Optional<Method>>> ACCESSORS = new ClassValue<>() {
        @Override protected ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private MLuaRenderContextAccess() { }

    static Object optional(Object target, String... names) {
        try {
            return target == null ? null : invoke(target, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static Vec3 cameraLook(Object camera) {
        Object value = optional(camera, "getLookVector", "getLookDirection", "getForwardVector",
            "getLookAngle", "lookVector", "lookDirection");
        Object x = optional(value, "x");
        Object y = optional(value, "y");
        Object z = optional(value, "z");
        return x instanceof Number && y instanceof Number && z instanceof Number
            ? new Vec3(((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue())
            : new Vec3(0, 0, 1);
    }

    static Vec3 cameraPosition(Object camera) {
        Object value = camera instanceof Vec3 ? camera : optional(camera, "getPosition", "position");
        if (value instanceof Vec3 position) return position;
        Object x = optional(value, "x", "getX", "xCoord");
        Object y = optional(value, "y", "getY", "yCoord");
        Object z = optional(value, "z", "getZ", "zCoord");
        if (x instanceof Number numberX && y instanceof Number numberY && z instanceof Number numberZ) {
            double px = numberX.doubleValue();
            double py = numberY.doubleValue();
            double pz = numberZ.doubleValue();
            if (Double.isFinite(px) && Double.isFinite(py) && Double.isFinite(pz)) {
                return new Vec3(px, py, pz);
            }
        }
        return null;
    }

    private static Object invoke(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            Optional<Method> accessor = ACCESSORS.get(target.getClass()).computeIfAbsent(name,
                candidate -> findAccessor(target.getClass(), candidate));
            if (accessor.isPresent()) return accessor.get().invoke(target);
        }
        return null;
    }

    private static Optional<Method> findAccessor(Class<?> targetClass, String name) {
        try {
            return Optional.of(targetClass.getMethod(name));
        } catch (NoSuchMethodException ignored) {
            // Cache missing names too; this loader class will always expose the same method set.
            return Optional.empty();
        }
    }
}
