package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

/** Lua-declared mappings from animation bone names to native model part paths. */
public final class MLuaModelAdapterRegistry {
    public record Owner(Object runtimeSession, String modId) {
        public Owner {
            modId = modId == null ? "" : modId;
        }
    }

    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private static final ConcurrentHashMap<String, Map<Owner, Map<String, String>>> ADAPTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, String>> RESOLVED_MAPPINGS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private MLuaModelAdapterRegistry() {}

    public static void register(String modelClass, Map<String, String> parts, Owner owner) {
        String className = normalizeClassName(modelClass);
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("Adapter parts cannot be empty");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        parts.forEach((bone, path) -> {
            String normalizedBone = bone == null ? "" : bone.trim();
            String normalizedPath = path == null ? "" : path.trim();
            if (normalizedBone.isEmpty() || normalizedPath.isEmpty()) {
                throw new IllegalArgumentException("Adapter bone names and model part paths cannot be empty");
            }
            validatePartPath(normalizedBone, normalizedPath);
            if (copy.putIfAbsent(normalizedBone, normalizedPath) != null) {
                throw new IllegalArgumentException("Adapter bone name is registered more than once: " + normalizedBone);
            }
        });
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        Map<String, String> registeredParts = Map.copyOf(copy);
        ADAPTERS.compute(className, (ignored, current) -> {
            LinkedHashMap<Owner, Map<String, String>> owners = current == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            LinkedHashMap<String, String> ownedParts = new LinkedHashMap<>(owners.getOrDefault(resolvedOwner, Map.of()));
            for (Map.Entry<Owner, Map<String, String>> entry : owners.entrySet()) {
                if (entry.getKey().equals(resolvedOwner)) continue;
                for (String bone : registeredParts.keySet()) {
                    if (entry.getValue().containsKey(bone)) {
                        String addon = entry.getKey().modId().isBlank() ? "another owner" : "addon '" + entry.getKey().modId() + "'";
                        throw new IllegalStateException("Model bone '" + bone + "' for " + className
                            + " is already registered by " + addon);
                    }
                }
            }
            ownedParts.putAll(registeredParts);
            owners.put(resolvedOwner, Map.copyOf(ownedParts));
            return Map.copyOf(owners);
        });
        RESOLVED_MAPPINGS.clear();
    }

    public static boolean unregister(String modelClass, Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        String className = normalizeClassName(modelClass);
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        ADAPTERS.computeIfPresent(className, (ignored, current) -> {
            if (!current.containsKey(resolvedOwner)) return current;
            LinkedHashMap<Owner, Map<String, String>> owners = new LinkedHashMap<>(current);
            owners.remove(resolvedOwner);
            removed.set(true);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) RESOLVED_MAPPINGS.clear();
        return removed.get();
    }

    public static void clearOwned(Owner owner) {
        if (owner == null) return;
        boolean removed = removeOwned((registeredOwner) -> registeredOwner.equals(owner));
        if (removed) RESOLVED_MAPPINGS.clear();
    }

    public static void clearRuntime(Object runtimeSession) {
        if (runtimeSession == null) return;
        boolean removed = removeOwned((owner) -> owner.runtimeSession() == runtimeSession);
        if (removed) RESOLVED_MAPPINGS.clear();
    }

    private static boolean removeOwned(java.util.function.Predicate<Owner> predicate) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        for (String className : ADAPTERS.keySet()) {
            ADAPTERS.computeIfPresent(className, (ignored, current) -> {
                LinkedHashMap<Owner, Map<String, String>> owners = new LinkedHashMap<>(current);
                if (owners.keySet().removeIf(predicate)) removed.set(true);
                return owners.isEmpty() ? null : Map.copyOf(owners);
            });
        }
        return removed.get();
    }

    /** Called by loader render hooks; it reads registered data and never invokes Lua. */
    public static java.util.Set<String> apply(Object model, MLuaEntityScope scope) {
        if (model == null || scope == null) return java.util.Set.of();
        Map<String, String> mapping = findMapping(model.getClass());
        if (mapping == null) return java.util.Set.of();
        Map<String, MLuaCFrame> animation = MLuaBonePose.getPose(scope);
        java.util.LinkedHashSet<String> applied = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            ModelPart part = resolvePath(model, entry.getValue());
            if (part == null) continue;
            applied.add(entry.getKey());
            MLuaCFrame pose = animation == null ? null : animation.get(entry.getKey());
            if (pose != null) applyPose(part, pose);
            MLuaClientModelManager.applyState(part, MLuaClientModelManager.getPartState(scope, entry.getKey()));
        }
        return java.util.Set.copyOf(applied);
    }

    public static Map<String, String> findMapping(Class<?> modelClass) {
        if (modelClass == null) return null;
        synchronized (RESOLVED_MAPPINGS) {
            Map<String, String> cached = RESOLVED_MAPPINGS.get(modelClass);
            if (cached != null) return cached.isEmpty() ? null : cached;

            ArrayList<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) hierarchy.add(type);
            Collections.reverse(hierarchy);
            LinkedHashMap<String, String> merged = new LinkedHashMap<>();
            for (Class<?> type : hierarchy) {
                Map<Owner, Map<String, String>> adapters = ADAPTERS.get(type.getName());
                if (adapters != null) {
                    for (Map<String, String> parts : adapters.values()) merged.putAll(parts);
                }
            }
            Map<String, String> resolved = merged.isEmpty() ? Map.of() : Map.copyOf(merged);
            RESOLVED_MAPPINGS.put(modelClass, resolved);
            return resolved.isEmpty() ? null : resolved;
        }
    }

    /** Resolves an adapter's Lua-facing bone name to the native model part. */
    public static ModelPart resolvePart(Object model, String boneName) {
        if (model == null || boneName == null || boneName.isBlank()) return null;
        Map<String, String> mapping = findMapping(model.getClass());
        String path = mapping == null ? null : mapping.get(boneName);
        return path == null ? null : resolvePath(model, path);
    }

    /** Applies the registered model-part hierarchy to a pose stack for attachments. */
    public static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack) {
        if (model == null || boneName == null || boneName.isBlank() || poseStack == null) return false;
        Map<String, String> mapping = findMapping(model.getClass());
        String path = mapping == null ? null : mapping.get(boneName);
        if (path == null) return false;
        Object current = model;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) return false;
            if (current instanceof ModelPart parent) {
                try {
                    current = parent.getChild(segment);
                } catch (RuntimeException missingChild) {
                    return false;
                }
            } else {
                current = readField(current, segment);
            }
            if (!(current instanceof ModelPart part)) return false;
            part.translateAndRotate(poseStack);
        }
        return true;
    }

    private static ModelPart resolvePath(Object root, String path) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) return null;
            if (current instanceof ModelPart part) {
                try {
                    current = part.getChild(segment);
                } catch (RuntimeException missingChild) {
                    return null;
                }
            } else {
                current = readField(current, segment);
            }
            if (current == null) return null;
        }
        return current instanceof ModelPart part ? part : null;
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }

    private static void applyPose(ModelPart part, MLuaCFrame cframe) {
        part.x += (float) (cframe.x() * 16.0);
        part.y += (float) (cframe.y() * 16.0);
        part.z += (float) (cframe.z() * 16.0);
        double[] angles = cframe.toEulerAnglesXYZ();
        part.xRot = (float) angles[0];
        part.yRot = (float) angles[1];
        part.zRot = (float) angles[2];
    }

    private static String normalizeClassName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Model class name cannot be empty");
        String className = value.trim();
        if (!className.contains(".")) {
            throw new IllegalArgumentException("Use the model class's full Java class name");
        }
        for (String segment : className.split("\\.", -1)) {
            if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
                throw new IllegalArgumentException("Invalid Java model class name: " + className);
            }
            for (int index = 1; index < segment.length(); index++) {
                if (!Character.isJavaIdentifierPart(segment.charAt(index))) {
                    throw new IllegalArgumentException("Invalid Java model class name: " + className);
                }
            }
        }
        return className;
    }

    private static void validatePartPath(String boneName, String path) {
        for (String segment : path.split("\\.", -1)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Model part path for bone '" + boneName
                    + "' must use non-empty dot-separated names");
            }
        }
    }
}
