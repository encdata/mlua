package dev.mlua.runtime.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/** Resolves Minecraft's render fraction across loader and game-version context types. */
public final class MLuaRenderPartialTickResolver {
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            return new Accessors(
                findNoArg(type, "getPartialTick", "partialTick", "getPartialTickTime", "getTickDelta", "tickDelta", "getFrameTime"),
                findNoArg(type, "tickCounter", "getTickCounter", "deltaTracker", "getDeltaTracker", "timer", "getTimer"),
                findNoArg(type, "getGameTimeDeltaPartialTick", "getTickDelta", "getPartialTick", "getPartialTickTime"),
                findBooleanArg(type, "getGameTimeDeltaPartialTick", "getTickDelta"));
        }
    };

    private MLuaRenderPartialTickResolver() { }

    /** Returns a finite render fraction clamped to [0, 1], or {@code null} when unavailable. */
    public static Float read(Object context) {
        if (context == null) return null;
        Accessors accessors = ACCESSORS.get(context.getClass());
        Object direct = invoke(context, accessors.direct());
        Float partialTick = finitePartialTick(direct);
        if (partialTick != null) return partialTick;

        Object tracker = direct != null ? direct : invoke(context, accessors.tracker());
        if (tracker == null) return null;
        Accessors trackerAccessors = ACCESSORS.get(tracker.getClass());
        partialTick = finitePartialTick(invoke(tracker, trackerAccessors.tickNoArg()));
        if (partialTick != null) return partialTick;
        return finitePartialTick(invokeBoolean(tracker, trackerAccessors.tickWithBoolean()));
    }

    /** Reads the current client's tracker when a render hook does not provide one. */
    public static Float currentClient() {
        return read(Minecraft.getInstance());
    }

    private static Float finitePartialTick(Object value) {
        if (!(value instanceof Number number)) return null;
        float partialTick = number.floatValue();
        return Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : null;
    }

    private static Method findNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Method findBooleanArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name, boolean.class);
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Object invoke(Object target, Method method) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeBoolean(Object target, Method method) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private record Accessors(Method direct, Method tracker, Method tickNoArg, Method tickWithBoolean) { }
}
