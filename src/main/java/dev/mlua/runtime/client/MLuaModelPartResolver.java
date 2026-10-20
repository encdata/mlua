package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.util.MLuaWeakIdentityMap;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.geom.ModelPart;

/** Resolves addon bone paths and caches native parts without retaining model instances. */
final class MLuaModelPartResolver {
    private record ResolvedParts(long generation, Map<String, WeakReference<ModelPart>> parts) { }

    private static final MLuaWeakIdentityMap<Object, ResolvedParts> CACHE = new MLuaWeakIdentityMap<>();
    private static final Set<String> WARNED_MISSING_PATHS = ConcurrentHashMap.newKeySet();

    private MLuaModelPartResolver() { }

    static ModelPart resolvePath(Object root, String path) {
        Object current = root;
        for (String segment : MLuaModelFieldAccess.pathSegments(path)) {
            if (segment.isBlank()) return null;
            if (current instanceof ModelPart part) {
                try {
                    current = part.getChild(segment);
                } catch (RuntimeException missingChild) {
                    return null;
                }
            } else {
                current = MLuaModelFieldAccess.readField(current, segment);
            }
            if (current == null) return null;
        }
        return current instanceof ModelPart part ? part : null;
    }

    static Map<String, ModelPart> resolvedParts(Object model, Map<String, String> mappings,
                                                 AtomicLong currentGeneration) {
        long generation = currentGeneration.get();
        synchronized (CACHE) {
            ResolvedParts cached = CACHE.get(model);
            if (cached != null && cached.generation() == generation) {
                LinkedHashMap<String, ModelPart> liveParts = new LinkedHashMap<>();
                boolean complete = true;
                for (Map.Entry<String, WeakReference<ModelPart>> entry : cached.parts().entrySet()) {
                    ModelPart part = entry.getValue().get();
                    if (part == null) {
                        complete = false;
                        break;
                    }
                    liveParts.put(entry.getKey(), part);
                }
                if (complete) return Collections.unmodifiableMap(liveParts);
            }
        }

        LinkedHashMap<String, ModelPart> resolved = new LinkedHashMap<>();
        mappings.forEach((bone, path) -> {
            ModelPart part = resolvePath(model, path);
            if (part != null) resolved.put(bone, part);
            else reportMissingPath(model.getClass(), bone, path);
        });
        Map<String, ModelPart> result = Collections.unmodifiableMap(resolved);
        synchronized (CACHE) {
            if (generation == currentGeneration.get()) {
                LinkedHashMap<String, WeakReference<ModelPart>> weakParts = new LinkedHashMap<>();
                result.forEach((bone, part) -> weakParts.put(bone, new WeakReference<>(part)));
                CACHE.put(model, new ResolvedParts(generation, Collections.unmodifiableMap(weakParts)));
            }
        }
        return result;
    }

    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        WARNED_MISSING_PATHS.clear();
    }

    static void reportMissingPath(Class<?> modelClass, String boneName, String path) {
        String className = modelClass.getName();
        String key = className + '\u0000' + boneName + '\u0000' + String.valueOf(path);
        if (!WARNED_MISSING_PATHS.add(key)) return;
        if (path == null) {
            MLuaLogger.LOGGER.warn("[MLua] Render model {} has no registered adapter path for bone '{}'; register one with ModelService:RegisterAdapter",
                className, boneName);
        } else {
            MLuaLogger.LOGGER.warn("[MLua] Model adapter path '{}' for bone '{}' did not resolve on {}; check ModelService:RegisterAdapter",
                path, boneName, className);
        }
    }

    static String normalizeRendererFieldPath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Renderer model field path cannot be empty");
        if (normalized.length() > MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH) {
            throw new IllegalArgumentException("Renderer model field path cannot exceed "
                + MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH + " characters");
        }
        String[] segments = normalized.split("\\.", -1);
        if (segments.length > MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH) {
            throw new IllegalArgumentException("Renderer model field path cannot exceed "
                + MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH + " segments");
        }
        for (String segment : segments) validateIdentifierSegment(segment, normalized, "renderer model field path");
        return normalized;
    }

    static void validatePartPath(String boneName, String path) {
        if (boneName.length() > MLuaModelAdapterRegistry.MAX_BONE_NAME_LENGTH) {
            throw new IllegalArgumentException("Model adapter bone names cannot exceed "
                + MLuaModelAdapterRegistry.MAX_BONE_NAME_LENGTH + " characters");
        }
        if (path.length() > MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH) {
            throw new IllegalArgumentException("Model part paths cannot exceed "
                + MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH + " characters");
        }
        String[] segments = path.split("\\.", -1);
        if (segments.length > MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH) {
            throw new IllegalArgumentException("Model part paths cannot exceed "
                + MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH + " segments");
        }
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Model part path for bone '" + boneName
                    + "' must use non-empty dot-separated names");
            }
        }
    }

    private static void validateIdentifierSegment(String segment, String path, String label) {
        if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
            throw new IllegalArgumentException("Invalid " + label + ": " + path);
        }
        for (int index = 1; index < segment.length(); index++) {
            if (!Character.isJavaIdentifierPart(segment.charAt(index))) {
                throw new IllegalArgumentException("Invalid " + label + ": " + path);
            }
        }
    }
}
