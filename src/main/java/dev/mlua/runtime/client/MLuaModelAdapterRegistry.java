package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.ModelPart;

/** Lua-declared mappings from animation bone names to native model part paths. */
public final class MLuaModelAdapterRegistry {
    public record Owner(Object runtimeSession, String modId) {
        public Owner {
            modId = modId == null ? "" : modId;
        }
    }

    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private record RegisteredAdapter(Owner owner, Map<String, String> parts) {}
    private static final ConcurrentHashMap<String, RegisteredAdapter> ADAPTERS = new ConcurrentHashMap<>();

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
            copy.put(normalizedBone, normalizedPath);
        });
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        RegisteredAdapter next = new RegisteredAdapter(resolvedOwner, Map.copyOf(copy));
        ADAPTERS.compute(className, (ignored, current) -> {
            if (current != null && !current.owner().equals(resolvedOwner)) {
                throw new IllegalStateException("A model adapter is already registered for " + className);
            }
            return next;
        });
    }

    public static boolean unregister(String modelClass, Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        String className = normalizeClassName(modelClass);
        RegisteredAdapter adapter = ADAPTERS.get(className);
        return adapter != null && adapter.owner().equals(resolvedOwner) && ADAPTERS.remove(className, adapter);
    }

    public static void clearOwned(Owner owner) {
        if (owner != null) ADAPTERS.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    public static void clearRuntime(Object runtimeSession) {
        if (runtimeSession == null) return;
        ADAPTERS.entrySet().removeIf(entry -> entry.getValue().owner().runtimeSession() == runtimeSession);
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
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) {
            String canonical = type.getName();
            RegisteredAdapter adapter = ADAPTERS.get(canonical);
            if (adapter != null) return adapter.parts();
        }
        return null;
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
        return className;
    }
}
