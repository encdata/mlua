package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Resolves registered model adapters against concrete model classes and instances. */
final class MLuaModelAdapterLookup {
    private MLuaModelAdapterLookup() { }

    static boolean isAdaptedModel(Object model) {
        return model != null && (findMappingForModel(model) != null
            || !customAdaptersForModel(model.getClass()).adapters().isEmpty());
    }

    static MLuaModelAdapterRegistry.CustomAdapterResolution customAdaptersForModel(Class<?> modelClass) {
        if (modelClass == null) return MLuaModelAdapterRegistry.CustomAdapterResolution.EMPTY;
        synchronized (MLuaModelAdapterRegistry.RESOLVED_CUSTOM_ADAPTERS) {
            var cached = MLuaModelAdapterRegistry.RESOLVED_CUSTOM_ADAPTERS.get(modelClass);
            if (cached != null) return cached;
            ArrayList<Class<?>> hierarchy = hierarchy(modelClass);
            LinkedHashMap<String, MLuaModelAdapterRegistry.CustomAdapter> winners = new LinkedHashMap<>();
            ArrayList<MLuaModelAdapterRegistry.CustomAdapter> ordered = new ArrayList<>();
            for (Class<?> type : hierarchy) {
                Map<MLuaModelAdapterRegistry.Owner, MLuaModelAdapterRegistry.CustomAdapter> registered =
                    MLuaModelAdapterRegistry.CUSTOM_ADAPTERS.get(type.getName());
                if (registered == null) continue;
                registered.values().stream()
                    .sorted(Comparator.comparingLong(adapter -> adapter.registrationOrder))
                    .forEach(adapter -> {
                        if (!adapter.active) return;
                        ordered.add(adapter);
                        for (String bone : adapter.bones) winners.put(bone, adapter);
                    });
            }
            LinkedHashMap<MLuaModelAdapterRegistry.CustomAdapter, List<String>> selected = new LinkedHashMap<>();
            for (MLuaModelAdapterRegistry.CustomAdapter adapter : ordered) {
                ArrayList<String> bones = new ArrayList<>();
                for (String bone : adapter.bones) if (winners.get(bone) == adapter) bones.add(bone);
                if (!bones.isEmpty()) selected.put(adapter, List.copyOf(bones));
            }
            var result = selected.isEmpty() ? MLuaModelAdapterRegistry.CustomAdapterResolution.EMPTY
                : new MLuaModelAdapterRegistry.CustomAdapterResolution(Collections.unmodifiableMap(selected),
                    Collections.unmodifiableMap(new LinkedHashMap<>(winners)));
            MLuaModelAdapterRegistry.RESOLVED_CUSTOM_ADAPTERS.put(modelClass, result);
            return result;
        }
    }

    static MLuaModelAdapterRegistry.CustomAdapter customAdapterForBone(Class<?> modelClass, String boneName) {
        return customAdaptersForModel(modelClass).byBone().get(boneName);
    }

    static Map<String, String> findMapping(Class<?> modelClass) {
        if (modelClass == null) return null;
        return findMapping(modelClass, 0);
    }

    private static Map<String, String> findMapping(Class<?> modelClass, int invalidationRetries) {
        long generation = MLuaModelAdapterCache.generation();
        Map<String, String> cached = MLuaModelAdapterCache.classMapping(modelClass, generation);
        if (cached != null) return cached.isEmpty() ? null : cached;

        TreeMap<String, String> merged = new TreeMap<>();
        for (Class<?> type : hierarchy(modelClass)) {
            Map<MLuaModelAdapterRegistry.Owner, Map<String, String>> adapters =
                MLuaModelAdapterRegistry.ADAPTERS.get(type.getName());
            if (adapters != null) adapters.values().forEach(merged::putAll);
        }
        Map<String, String> resolved = merged.isEmpty() ? Map.of() : MLuaModelAdapterRegistry.orderedMap(merged);
        if (!MLuaModelAdapterCache.cacheClassMapping(modelClass, generation, resolved)) {
            return invalidationRetries < 2 ? findMapping(modelClass, invalidationRetries + 1) : null;
        }
        return resolved.isEmpty() ? null : resolved;
    }

    static Map<String, String> findMappingForModel(Object model) {
        if (model != null && !MLuaClientRenderThread.require("Lua model adapter resolver")) {
            return findMapping(model.getClass());
        }
        return findMappingForModel(model, 0);
    }

    private static Map<String, String> findMappingForModel(Object model, int invalidationRetries) {
        if (model == null) return null;
        long generation = MLuaModelAdapterCache.generation();
        Map<String, String> cached = MLuaModelAdapterCache.instanceMapping(model, generation);
        if (cached != null) return cached.isEmpty() ? null : cached;
        TreeMap<String, String> merged = new TreeMap<>();
        for (Class<?> type : hierarchy(model.getClass())) {
            Map<MLuaModelAdapterRegistry.Owner, Map<String, String>> staticMappings =
                MLuaModelAdapterRegistry.ADAPTERS.get(type.getName());
            java.util.TreeMap<String, String> classMappings = new java.util.TreeMap<>();
            if (staticMappings != null) staticMappings.values().forEach(classMappings::putAll);
            Map<MLuaModelAdapterRegistry.Owner, MLuaModelAdapterRegistry.AdapterResolver> resolvers =
                MLuaModelAdapterRegistry.ADAPTER_RESOLVERS.get(type.getName());
            if (resolvers != null && !resolvers.isEmpty()) {
                for (var resolver : orderedResolvers(type.getName(), resolvers)) {
                    Map<String, String> resolved = MLuaModelAdapterResolverInvoker.invoke(model, resolver);
                    if (resolved == null || resolved.isEmpty()) continue;
                    boolean conflict = resolved.entrySet().stream().anyMatch(entry ->
                        classMappings.containsKey(entry.getKey())
                            && !classMappings.get(entry.getKey()).equals(entry.getValue()));
                    long newBones = resolved.keySet().stream().filter(name -> !classMappings.containsKey(name)).count();
                    if (conflict || classMappings.size() + newBones > MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL) {
                        MLuaModelAdapterRegistry.disableResolver(resolver, new IllegalStateException(conflict
                            ? "the resolver conflicts with another adapter's bone mapping on this model class"
                            : "combined adapter mappings exceed " + MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL
                                + " bones for this model class"));
                        continue;
                    }
                    classMappings.putAll(resolved);
                }
            }
            merged.putAll(classMappings);
        }
        if (generation != MLuaModelAdapterCache.generation()) return retryAfterInvalidation(model, invalidationRetries);
        Map<String, String> result = merged.isEmpty() ? Map.of() : MLuaModelAdapterRegistry.orderedMap(merged);
        if (!MLuaModelAdapterCache.cacheInstanceMapping(model, generation, result)) {
            return retryAfterInvalidation(model, invalidationRetries);
        }
        return result.isEmpty() ? null : result;
    }

    private static List<MLuaModelAdapterRegistry.AdapterResolver> orderedResolvers(
            String modelClass, Map<MLuaModelAdapterRegistry.Owner, MLuaModelAdapterRegistry.AdapterResolver> resolvers) {
        long generation = MLuaModelAdapterCache.generation();
        MLuaModelAdapterRegistry.OrderedResolvers cached = MLuaModelAdapterRegistry.ORDERED_RESOLVERS.get(modelClass);
        if (cached != null && cached.generation() == generation) return cached.values();
        List<MLuaModelAdapterRegistry.AdapterResolver> ordered = resolvers.values().stream()
            .sorted(Comparator.comparingLong(MLuaModelAdapterRegistry.AdapterResolver::registrationOrder)).toList();
        if (generation == MLuaModelAdapterCache.generation()) {
            MLuaModelAdapterRegistry.ORDERED_RESOLVERS.put(modelClass,
                new MLuaModelAdapterRegistry.OrderedResolvers(generation, ordered));
        }
        return ordered;
    }

    private static Map<String, String> retryAfterInvalidation(Object model, int invalidationRetries) {
        return invalidationRetries < 2
            ? findMappingForModel(model, invalidationRetries + 1)
            : findMapping(model.getClass());
    }

    private static ArrayList<Class<?>> hierarchy(Class<?> modelClass) {
        ArrayList<Class<?>> types = new ArrayList<>();
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) types.add(type);
        Collections.reverse(types);
        return types;
    }
}
