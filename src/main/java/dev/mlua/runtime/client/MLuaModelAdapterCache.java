package dev.mlua.runtime.client;

import dev.mlua.core.util.MLuaWeakIdentityMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Weak model mapping caches and their shared invalidation epoch. */
final class MLuaModelAdapterCache {
    private static final Object LOCK = new Object();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Map<Class<?>, Map<String, String>> CLASS_MAPPINGS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final MLuaWeakIdentityMap<Object, Map<String, String>> INSTANCE_MAPPINGS =
        new MLuaWeakIdentityMap<>();

    private MLuaModelAdapterCache() { }

    static long generation() {
        return GENERATION.get();
    }

    static AtomicLong generationClock() {
        return GENERATION;
    }

    static Map<String, String> classMapping(Class<?> modelClass, long generation) {
        synchronized (LOCK) {
            if (generation != GENERATION.get()) return null;
            return CLASS_MAPPINGS.get(modelClass);
        }
    }

    static boolean cacheClassMapping(Class<?> modelClass, long generation, Map<String, String> mappings) {
        synchronized (LOCK) {
            if (generation != GENERATION.get()) return false;
            CLASS_MAPPINGS.put(modelClass, mappings);
            return true;
        }
    }

    static Map<String, String> instanceMapping(Object model, long generation) {
        synchronized (LOCK) {
            if (generation != GENERATION.get()) return null;
            return INSTANCE_MAPPINGS.get(model);
        }
    }

    static boolean cacheInstanceMapping(Object model, long generation, Map<String, String> mappings) {
        synchronized (LOCK) {
            if (generation != GENERATION.get()) return false;
            INSTANCE_MAPPINGS.put(model, mappings);
            return true;
        }
    }

    static long invalidate() {
        synchronized (LOCK) {
            long generation = GENERATION.incrementAndGet();
            CLASS_MAPPINGS.clear();
            INSTANCE_MAPPINGS.clear();
            return generation;
        }
    }
}
