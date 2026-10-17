package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

/** Lua-declared mappings from animation bone names to native model part paths. */
public final class MLuaModelAdapterRegistry {
    public static final int MAX_MODEL_CLASSES = 256;
    public static final int MAX_PARTS_PER_MODEL = 512;
    public static final int MAX_TOTAL_PARTS = 8192;
    public static final int MAX_CLASS_NAME_LENGTH = 256;
    public static final int MAX_BONE_NAME_LENGTH = 128;
    public static final int MAX_PART_PATH_LENGTH = 512;
    public static final int MAX_PART_PATH_DEPTH = 32;

    public record Owner(Object runtimeSession, String modId) {
        public Owner {
            modId = modId == null ? "" : modId;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Owner owner && runtimeSession == owner.runtimeSession
                && modId.equals(owner.modId);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(runtimeSession) + modId.hashCode();
        }
    }

    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private static final ConcurrentHashMap<String, Map<Owner, Map<String, String>>> ADAPTERS = new ConcurrentHashMap<>();
    private static int registeredPartCount;
    private static final ConcurrentMap<String, java.util.List<String>> PART_PATHS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MLuaEntityScope, AppliedModel> APPLIED_MODELS = new ConcurrentHashMap<>();
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> MODEL_FIELDS = new ClassValue<>() {
        @Override protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final Map<Class<?>, Map<String, String>> RESOLVED_MAPPINGS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private record AppliedModel(Class<?> modelClass, java.util.Set<String> resolvedBones) { }

    private MLuaModelAdapterRegistry() {}

    public static synchronized void register(String modelClass, Map<String, String> parts, Owner owner) {
        String className = normalizeClassName(modelClass);
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("Adapter parts cannot be empty");
        if (parts.size() > MAX_PARTS_PER_MODEL) {
            throw new IllegalArgumentException("A model adapter can register at most " + MAX_PARTS_PER_MODEL + " bones");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        ArrayList<Map.Entry<String, String>> orderedParts = new ArrayList<>(parts.entrySet());
        orderedParts.sort(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())));
        orderedParts.forEach(entry -> {
            String bone = entry.getKey();
            String path = entry.getValue();
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
        Map<String, String> registeredParts = orderedMap(copy);
        int[] addedParts = {0};
        ADAPTERS.compute(className, (ignored, current) -> {
            if (current == null && ADAPTERS.size() >= MAX_MODEL_CLASSES) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_MODEL_CLASSES
                    + " model adapter classes registered");
            }
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
            int modelPartCount = owners.values().stream().mapToInt(Map::size).sum();
            for (String bone : registeredParts.keySet()) {
                if (!ownedParts.containsKey(bone)) addedParts[0]++;
            }
            if (modelPartCount + addedParts[0] > MAX_PARTS_PER_MODEL) {
                throw new IllegalStateException("Model adapter '" + className + "' is limited to "
                    + MAX_PARTS_PER_MODEL + " bones across all addons");
            }
            if (registeredPartCount + addedParts[0] > MAX_TOTAL_PARTS) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_TOTAL_PARTS
                    + " model adapter bones registered");
            }
            ownedParts.putAll(registeredParts);
            owners.put(resolvedOwner, orderedMap(ownedParts));
            return orderedMap(owners);
        });
        registeredPartCount += addedParts[0];
        clearMappingCaches();
    }

    public static synchronized boolean unregister(String modelClass, Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        String className = normalizeClassName(modelClass);
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        int[] removedParts = {0};
        ADAPTERS.computeIfPresent(className, (ignored, current) -> {
            if (!current.containsKey(resolvedOwner)) return current;
            LinkedHashMap<Owner, Map<String, String>> owners = new LinkedHashMap<>(current);
            removedParts[0] = owners.remove(resolvedOwner).size();
            removed.set(true);
            return owners.isEmpty() ? null : orderedMap(owners);
        });
        registeredPartCount -= removedParts[0];
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    /** Removes one mapping owned by an addon without disturbing mappings from other addons. */
    public static synchronized boolean unregisterBone(String modelClass, String boneName, Owner owner) {
        String className = normalizeClassName(modelClass);
        String bone = boneName == null ? "" : boneName.trim();
        if (bone.isEmpty()) throw new IllegalArgumentException("Model adapter bone name cannot be empty");
        if (bone.length() > MAX_BONE_NAME_LENGTH) {
            throw new IllegalArgumentException("Model adapter bone names cannot exceed " + MAX_BONE_NAME_LENGTH + " characters");
        }
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        ADAPTERS.computeIfPresent(className, (ignored, current) -> {
            Map<String, String> owned = current.get(resolvedOwner);
            if (owned == null || !owned.containsKey(bone)) return current;
            LinkedHashMap<Owner, Map<String, String>> owners = new LinkedHashMap<>(current);
            LinkedHashMap<String, String> remaining = new LinkedHashMap<>(owned);
            remaining.remove(bone);
            if (remaining.isEmpty()) owners.remove(resolvedOwner);
            else owners.put(resolvedOwner, orderedMap(remaining));
            removed.set(true);
            return owners.isEmpty() ? null : orderedMap(owners);
        });
        if (removed.get()) {
            registeredPartCount--;
            clearMappingCaches();
        }
        return removed.get();
    }

    public static synchronized void clearOwned(Owner owner) {
        if (owner == null) return;
        boolean removed = removeOwned((registeredOwner) -> registeredOwner.equals(owner));
        if (removed) {
            clearMappingCaches();
            clearAppliedEntities();
        }
    }

    public static synchronized void clearRuntime(Object runtimeSession) {
        if (runtimeSession == null) return;
        boolean removed = removeOwned((owner) -> owner.runtimeSession() == runtimeSession);
        if (removed) {
            clearMappingCaches();
            clearAppliedEntities();
        }
    }

    private static synchronized boolean removeOwned(java.util.function.Predicate<Owner> predicate) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        int[] removedParts = {0};
        for (String className : ADAPTERS.keySet()) {
            ADAPTERS.computeIfPresent(className, (ignored, current) -> {
                LinkedHashMap<Owner, Map<String, String>> owners = new LinkedHashMap<>(current);
                int before = owners.values().stream().mapToInt(Map::size).sum();
                if (owners.keySet().removeIf(predicate)) {
                    int remaining = owners.values().stream().mapToInt(Map::size).sum();
                    removedParts[0] += before - remaining;
                    removed.set(true);
                }
                return owners.isEmpty() ? null : orderedMap(owners);
            });
        }
        registeredPartCount -= removedParts[0];
        return removed.get();
    }

    public static synchronized int registeredModelClassCount() { return ADAPTERS.size(); }

    public static synchronized int registeredPartCount() { return registeredPartCount; }

    /** Returns adapter bones that resolved on the model used by this entity's latest render. */
    public static java.util.Set<String> appliedBoneNames(MLuaEntityScope scope) {
        if (scope == null) return java.util.Set.of();
        AppliedModel applied = APPLIED_MODELS.get(scope);
        if (applied == null) return java.util.Set.of();
        Map<String, String> currentMapping = findMapping(applied.modelClass());
        if (currentMapping == null) return java.util.Set.of();
        java.util.LinkedHashSet<String> current = new java.util.LinkedHashSet<>();
        for (String bone : applied.resolvedBones()) {
            if (currentMapping.containsKey(bone)) current.add(bone);
        }
        return Collections.unmodifiableSet(current);
    }

    public static void clearEntity(MLuaEntityScope scope) {
        if (scope != null) APPLIED_MODELS.remove(scope);
    }

    public static void clearAppliedEntities() {
        APPLIED_MODELS.clear();
    }

    /** Returns the merged, deterministic mapping currently registered for one model class. */
    public static synchronized Map<String, String> registeredMappings(String modelClass) {
        String className = normalizeClassName(modelClass);
        Map<Owner, Map<String, String>> owners = ADAPTERS.get(className);
        if (owners == null || owners.isEmpty()) return Map.of();
        java.util.TreeMap<String, String> merged = new java.util.TreeMap<>();
        owners.values().forEach(merged::putAll);
        return Collections.unmodifiableMap(new LinkedHashMap<>(merged));
    }

    private static void clearMappingCaches() {
        RESOLVED_MAPPINGS.clear();
        PART_PATHS.clear();
    }

    /** Called by loader render hooks; it reads registered data and never invokes Lua. */
    public static java.util.Set<String> apply(Object model, MLuaEntityScope scope) {
        if (model == null || scope == null) return java.util.Set.of();
        Map<String, String> mapping = findMapping(model.getClass());
        if (mapping == null) {
            APPLIED_MODELS.remove(scope);
            return java.util.Set.of();
        }
        Map<String, MLuaCFrame> animation = MLuaBonePose.getPose(scope);
        java.util.LinkedHashSet<String> applied = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            ModelPart part = resolvePath(model, entry.getValue());
            if (part == null) continue;
            applied.add(entry.getKey());
            MLuaCFrame pose = animation == null ? null : animation.get(entry.getKey());
            if (pose != null) {
                MLuaClientModelManager.rememberOverridePose(model, part);
                applyPose(part, pose);
            }
            MLuaClientModelManager.applyState(model, part, MLuaClientModelManager.getPartState(scope, entry.getKey()));
        }
        java.util.Set<String> resolved = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(applied));
        APPLIED_MODELS.put(scope, new AppliedModel(model.getClass(), resolved));
        return resolved;
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
            Map<String, String> resolved = merged.isEmpty() ? Map.of() : orderedMap(merged);
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
        for (String segment : pathSegments(path)) {
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
        for (String segment : pathSegments(path)) {
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
        Optional<Field> field = MODEL_FIELDS.get(target.getClass()).computeIfAbsent(name,
            fieldName -> findField(target.getClass(), fieldName));
        if (field.isEmpty()) return null;
        try {
            return field.get().get(target);
        } catch (IllegalAccessException | RuntimeException inaccessible) {
            return null;
        }
    }

    private static Optional<Field> findField(Class<?> modelClass, String name) {
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
            } catch (RuntimeException inaccessible) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static java.util.List<String> pathSegments(String path) {
        return PART_PATHS.computeIfAbsent(path, value -> java.util.List.of(value.split("\\.", -1)));
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
        if (className.length() > MAX_CLASS_NAME_LENGTH) {
            throw new IllegalArgumentException("Model class name cannot exceed " + MAX_CLASS_NAME_LENGTH + " characters");
        }
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

    private static <K, V> Map<K, V> orderedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static void validatePartPath(String boneName, String path) {
        if (boneName.length() > MAX_BONE_NAME_LENGTH) {
            throw new IllegalArgumentException("Model adapter bone names cannot exceed " + MAX_BONE_NAME_LENGTH + " characters");
        }
        if (path.length() > MAX_PART_PATH_LENGTH) {
            throw new IllegalArgumentException("Model part paths cannot exceed " + MAX_PART_PATH_LENGTH + " characters");
        }
        String[] segments = path.split("\\.", -1);
        if (segments.length > MAX_PART_PATH_DEPTH) {
            throw new IllegalArgumentException("Model part paths cannot exceed " + MAX_PART_PATH_DEPTH + " segments");
        }
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Model part path for bone '" + boneName
                    + "' must use non-empty dot-separated names");
            }
        }
    }
}
