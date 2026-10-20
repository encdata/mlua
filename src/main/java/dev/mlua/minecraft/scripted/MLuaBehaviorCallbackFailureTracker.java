package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaLogger;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Bounds repeated failures from scripted content without retaining unloaded levels or Lua closures. */
final class MLuaBehaviorCallbackFailureTracker {
    private static final int MAX_FAILURES_PER_LEVEL = 4096;
    private static final Map<Level, LinkedHashMap<FailureKey, WeakReference<Object>>> FAILURES = new WeakHashMap<>();

    private MLuaBehaviorCallbackFailureTracker() { }

    static Varargs invoke(Level level, BlockPos pos, String domain, String behaviorKey, String event,
                          LuaValue callback, Supplier<Varargs> invocation) {
        return invoke(level, pos, domain, behaviorKey, event, callback, callback, invocation);
    }

    static Varargs invoke(Level level, BlockPos pos, String domain, String behaviorKey, String event,
                          MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback,
                          Supplier<Varargs> invocation) {
        return invoke(level, pos, domain, behaviorKey, event,
            behaviors == null ? null : behaviors.identity(), callback, invocation);
    }

    static Varargs invoke(Level level, BlockPos pos, String domain, String behaviorKey, String event,
                          Object definitionIdentity, LuaValue callback, Supplier<Varargs> invocation) {
        if (level == null || domain == null || behaviorKey == null || event == null
                || definitionIdentity == null || callback == null || !callback.isfunction()) return LuaValue.NONE;
        FailureKey key = new FailureKey(pos == null ? 0L : pos.asLong(), pos != null, domain, behaviorKey, event);
        if (isDisabled(level, key, definitionIdentity)) return LuaValue.NONE;
        try {
            return invocation.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disable(level, key, definitionIdentity);
            MLuaLogger.LOGGER.error("MLua {} {} callback failed for '{}'{}; disabled in this level until replaced",
                domain, event, behaviorKey, pos == null ? "" : " at " + pos, failure);
            return LuaValue.NONE;
        }
    }

    private static synchronized boolean isDisabled(Level level, FailureKey key, Object definitionIdentity) {
        LinkedHashMap<FailureKey, WeakReference<Object>> failures = FAILURES.get(level);
        if (failures == null) return false;
        WeakReference<Object> failedCallback = failures.get(key);
        if (failedCallback == null) return false;
        if (failedCallback.get() == definitionIdentity) return true;
        failures.remove(key);
        if (failures.isEmpty()) FAILURES.remove(level);
        return false;
    }

    private static synchronized void disable(Level level, FailureKey key, Object definitionIdentity) {
        LinkedHashMap<FailureKey, WeakReference<Object>> failures = FAILURES.computeIfAbsent(level,
            ignored -> new LinkedHashMap<>(64, 0.75F, true));
        failures.put(key, new WeakReference<>(definitionIdentity));
        while (failures.size() > MAX_FAILURES_PER_LEVEL) {
            failures.remove(failures.keySet().iterator().next());
        }
    }

    private record FailureKey(long position, boolean hasPosition, String domain, String behaviorKey, String event) { }
}
