package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Resolves packed world light without binding loader render hooks to one Minecraft signature. */
public final class MLuaWorldLight {
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> LIGHT_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<BrightnessAccess>> BRIGHTNESS_METHODS = new ConcurrentHashMap<>();
    private static final AtomicBoolean UNAVAILABLE_REPORTED = new AtomicBoolean();

    private MLuaWorldLight() { }

    /** Returns the packed light at a world-space position, using fallback when no client level is active. */
    public static int packedAt(double x, double y, double z, int fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        Object level = minecraft == null ? null : minecraft.level;
        if (level == null) return fallback;
        BlockPos position = BlockPos.containing(x, y, z);
        Method method = LIGHT_METHODS.computeIfAbsent(level.getClass(), MLuaWorldLight::findMethod).orElse(null);
        if (method != null) {
            try {
                Object result = method.invoke(null, level, position);
                if (result instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        BrightnessAccess brightness = BRIGHTNESS_METHODS.computeIfAbsent(
            level.getClass(), MLuaWorldLight::findBrightnessAccess).orElse(null);
        if (brightness != null) {
            try {
                int block = ((Number) brightness.method().invoke(level, brightness.blockLayer(), position)).intValue();
                int sky = ((Number) brightness.method().invoke(level, brightness.skyLayer(), position)).intValue();
                return (Math.max(0, Math.min(15, sky)) << 20) | (Math.max(0, Math.min(15, block)) << 4);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        if (UNAVAILABLE_REPORTED.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.warn("[MLua] This Minecraft target does not expose world light sampling; managed world render objects will use fallback lighting");
        }
        return fallback;
    }

    private static Optional<BrightnessAccess> findBrightnessAccess(Class<?> levelClass) {
        try {
            Class<?> lightLayer = Class.forName("net.minecraft.world.level.LightLayer", false,
                MLuaWorldLight.class.getClassLoader());
            if (!lightLayer.isEnum()) return Optional.empty();
            Object block = null;
            Object sky = null;
            for (Object constant : lightLayer.getEnumConstants()) {
                if (!(constant instanceof Enum<?> value)) continue;
                if (value.name().equals("BLOCK")) block = value;
                else if (value.name().equals("SKY")) sky = value;
            }
            if (block == null || sky == null) return Optional.empty();
            Method method = levelClass.getMethod("getBrightness", lightLayer, BlockPos.class);
            return Optional.of(new BrightnessAccess(method, block, sky));
        } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
            return Optional.empty();
        }
    }

    private static Optional<Method> findMethod(Class<?> levelClass) {
        try {
            Class<?> renderer = Class.forName("net.minecraft.client.renderer.LevelRenderer", false,
                MLuaWorldLight.class.getClassLoader());
            return Arrays.stream(renderer.getMethods())
                .filter(method -> method.getName().equals("getLightColor")
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(levelClass)
                    && method.getParameterTypes()[1].isAssignableFrom(BlockPos.class)
                    && (method.getReturnType() == int.class || method.getReturnType() == Integer.class))
                .sorted(Comparator.comparing(method -> method.getParameterTypes()[0].getName()))
                .findFirst();
        } catch (ClassNotFoundException | LinkageError | SecurityException unavailable) {
            return Optional.empty();
        }
    }

    private record BrightnessAccess(Method method, Object blockLayer, Object skyLayer) { }
}
