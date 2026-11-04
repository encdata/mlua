package dev.mlua.runtime.client;

import dev.mlua.runtime.client.MLuaModelAdapterRegistry.Owner;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Owns addon registrations that locate a model inside a renderer instance. */
final class MLuaRendererAdapterRegistry {
    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private static final Map<String, Map<Owner, String>> ADAPTERS = new ConcurrentHashMap<>();

    private MLuaRendererAdapterRegistry() { }

    static synchronized void register(String rendererClass, String modelFieldPath, Owner owner) {
        String className = MLuaModelAdapterRegistry.normalizeClassName(rendererClass);
        String path = MLuaModelPartResolver.normalizeRendererFieldPath(modelFieldPath);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        Map<Owner, String> current = ADAPTERS.get(className);
        if (current == null && ADAPTERS.size() >= MLuaModelAdapterRegistry.MAX_RENDERER_CLASSES) {
            throw new IllegalStateException("MLua already has the maximum of "
                + MLuaModelAdapterRegistry.MAX_RENDERER_CLASSES + " renderer adapter classes registered");
        }
        if (current == null || !current.containsKey(resolvedOwner)) {
            long total = ADAPTERS.values().stream().mapToLong(Map::size).sum();
            if (total >= MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS) {
                throw new IllegalStateException("MLua already has the maximum of "
                    + MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS + " renderer model adapters registered across all addons");
            }
            long owned = ADAPTERS.values().stream().filter(adapters -> adapters.containsKey(resolvedOwner)).count();
            if (owned >= MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS_PER_OWNER) {
                throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' already has the maximum of "
                    + MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS_PER_OWNER + " renderer model adapters");
            }
        }
        LinkedHashMap<Owner, String> updated = current == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
        for (Map.Entry<Owner, String> entry : updated.entrySet()) {
            if (!entry.getKey().equals(resolvedOwner) && !entry.getValue().equals(path)) {
                String addon = entry.getKey().modId().isBlank() ? "another owner" : "addon '" + entry.getKey().modId() + "'";
                throw new IllegalStateException("Renderer model field for " + className
                    + " is already registered differently by " + addon);
            }
        }
        updated.put(resolvedOwner, path);
        ADAPTERS.put(className, Map.copyOf(updated));
    }

    static synchronized boolean unregister(String rendererClass, Owner owner) {
        String className = MLuaModelAdapterRegistry.normalizeClassName(rendererClass);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        Map<Owner, String> current = ADAPTERS.get(className);
        if (current == null || !current.containsKey(resolvedOwner)) return false;
        LinkedHashMap<Owner, String> updated = new LinkedHashMap<>(current);
        updated.remove(resolvedOwner);
        if (updated.isEmpty()) ADAPTERS.remove(className);
        else ADAPTERS.put(className, Map.copyOf(updated));
        return true;
    }

    static String registeredField(String rendererClass) {
        Map<Owner, String> owners = ADAPTERS.get(MLuaModelAdapterRegistry.normalizeClassName(rendererClass));
        return owners == null || owners.isEmpty() ? null : owners.values().iterator().next();
    }

    static String modelFieldFor(Class<?> rendererClass) {
        for (Class<?> type = rendererClass; type != null; type = type.getSuperclass()) {
            Map<Owner, String> owners = ADAPTERS.get(type.getName());
            if (owners != null && !owners.isEmpty()) return owners.values().iterator().next();
        }
        return null;
    }

    static synchronized int classCount() {
        return ADAPTERS.size();
    }

    static synchronized int adapterCount() {
        return ADAPTERS.values().stream().mapToInt(Map::size).sum();
    }

    static synchronized int adapterCount(Owner owner) {
        if (owner == null) return 0;
        return (int) ADAPTERS.values().stream().filter(adapters -> adapters.containsKey(owner)).count();
    }

    static synchronized boolean removeOwned(Predicate<Owner> ownerPredicate) {
        boolean removed = false;
        for (Map.Entry<String, Map<Owner, String>> entry : java.util.List.copyOf(ADAPTERS.entrySet())) {
            LinkedHashMap<Owner, String> remaining = new LinkedHashMap<>(entry.getValue());
            removed |= remaining.keySet().removeIf(ownerPredicate);
            if (remaining.size() == entry.getValue().size()) continue;
            if (remaining.isEmpty()) ADAPTERS.remove(entry.getKey());
            else ADAPTERS.put(entry.getKey(), Map.copyOf(remaining));
        }
        return removed;
    }
}
