package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.util.MLuaWeakIdentityMap;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.signal.MLuaConnection;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaInstructionBudget;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Lua-declared static paths and per-instance resolvers for native model parts. */
public final class MLuaModelAdapterRegistry {
    public static final long MAX_RESOLVER_LUA_INSTRUCTIONS = 50_000L;
    public static final int MAX_MODEL_CLASSES = 256;
    public static final int MAX_RENDERER_CLASSES = 256;
    /** Bounds registrations when several addons explicitly adapt the same renderer class. */
    public static final int MAX_RENDERER_ADAPTERS = 1_024;
    public static final int MAX_RENDERER_ADAPTERS_PER_OWNER = 64;
    public static final int MAX_RESOLVER_CLASSES = 256;
    /** Bounds the total number of live Lua resolver callbacks across all addons. */
    public static final int MAX_RESOLVERS = 4096;
    public static final int MAX_RESOLVERS_PER_OWNER = 64;
    public static final int MAX_CUSTOM_ADAPTER_CLASSES = 256;
    /** Bounds total live custom adapter callbacks; these may run for every rendered model instance. */
    public static final int MAX_CUSTOM_ADAPTERS = 1024;
    public static final int MAX_CUSTOM_ADAPTERS_PER_OWNER = 64;
    public static final int MAX_PARTS_PER_MODEL = 512;
    public static final int MAX_TOTAL_PARTS = 8192;
    /** Prevents static and custom adapters from letting one addon consume the shared bone budget. */
    public static final int MAX_PARTS_PER_OWNER = 2048;
    public static final int MAX_CLASS_NAME_LENGTH = 256;
    public static final int MAX_BONE_NAME_LENGTH = 128;
    public static final int MAX_PART_PATH_LENGTH = 512;
    public static final int MAX_PART_PATH_DEPTH = 32;
    private static final int MAX_RENDERER_WARNING_KEYS = 4096;

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
    private static final ConcurrentHashMap<String, Map<Owner, String>> RENDERER_ADAPTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<Owner, AdapterResolver>> ADAPTER_RESOLVERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<Owner, CustomAdapter>> CUSTOM_ADAPTERS = new ConcurrentHashMap<>();
    private static int registeredPartCount;
    private static int registeredCustomPartCount;
    private static final AtomicLong NEXT_RESOLVER_ORDER = new AtomicLong();
    private static final Set<String> REPORTED_RENDER_FAILURES =
        Collections.synchronizedSet(new LinkedHashSet<>());
    private static final ConcurrentMap<MLuaEntityScope, AppliedModel> APPLIED_MODELS = new ConcurrentHashMap<>();
    private static final AtomicLong MAPPING_GENERATION = new AtomicLong();
    private static final Map<Class<?>, Map<String, String>> RESOLVED_MAPPINGS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, CustomAdapterResolution> RESOLVED_CUSTOM_ADAPTERS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final MLuaWeakIdentityMap<Object, Map<String, String>> RESOLVED_INSTANCE_MAPPINGS =
        new MLuaWeakIdentityMap<>();
    private record AppliedModel(Class<?> modelClass, Map<String, String> mappings,
                                java.util.Set<String> resolvedBones, java.util.Set<String> customBones) { }
    private record CustomAdapterResolution(Map<CustomAdapter, List<String>> adapters,
                                           Map<String, CustomAdapter> byBone) {
        private static final CustomAdapterResolution EMPTY = new CustomAdapterResolution(Map.of(), Map.of());
    }

    private static final class AdapterResolver {
        private final String modelClass;
        private final LuaValue callback;
        private final MLuaContext context;
        private final Owner owner;
        private final long registrationOrder;
        private volatile boolean active = true;

        private AdapterResolver(String modelClass, LuaValue callback, MLuaContext context, Owner owner,
                                long registrationOrder) {
            this.modelClass = modelClass;
            this.callback = callback;
            this.context = context;
            this.owner = owner;
            this.registrationOrder = registrationOrder;
        }

        private String modelClass() { return modelClass; }
        private LuaValue callback() { return callback; }
        private MLuaContext context() { return context; }
        private Owner owner() { return owner; }
        private long registrationOrder() { return registrationOrder; }
    }
    /** Lua-defined adapter for model hierarchies that do not use Minecraft ModelPart. */
    private static final class CustomAdapter {
        private final String modelClass;
        private final List<String> bones;
        private final LuaValue apply;
        private final LuaValue applyPose;
        private final MLuaContext context;
        private final Owner owner;
        private final long registrationOrder;
        private volatile boolean active = true;

        private CustomAdapter(String modelClass, List<String> bones, LuaValue apply, LuaValue applyPose,
                              MLuaContext context, Owner owner, long registrationOrder) {
            this.modelClass = modelClass;
            this.bones = bones;
            this.apply = apply;
            this.applyPose = applyPose;
            this.context = context;
            this.owner = owner;
            this.registrationOrder = registrationOrder;
        }
    }
    public record AdapterSnapshot(String modelClass, Map<String, String> mappings,
                                  java.util.Set<String> resolvedBones, java.util.Set<String> customBones) { }

    private MLuaModelAdapterRegistry() {}

    /** Opens an entity model scope, masking any outer adapter while nested entity renders run. */
    public static void beginEntityRender(Entity entity, Object renderer) {
        if (entity == null) return;
        if (entity instanceof LivingEntity) {
            // ModelPart render hooks use the top frame. A living entity nested inside a
            // non-living renderer must mask that outer adapter while its own model draws.
            if (MLuaEntityModelRenderState.hasActiveFrame()) MLuaEntityModelRenderState.pushMask(entity);
            return;
        }
        if (!MLuaClientRenderThread.require("non-living model adapter")) return;
        Object model = null;
        IdentityHashMap<ModelPart, String> bones = new IdentityHashMap<>();
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        APPLIED_MODELS.remove(scope);
        try {
            model = findAdaptedModel(renderer);
            Map<String, String> mappings = model == null ? null : findMappingForModel(model);
            if (model != null && isAdaptedModel(model)) {
                Object adaptedModel = model;
                Map<String, String> safeMappings = mappings == null ? Map.of() : mappings;
                resolvedParts(model, safeMappings).forEach((bone, part) -> bones.put(part, bone));
                java.util.LinkedHashSet<String> customBones = new java.util.LinkedHashSet<>();
                customAdaptersForModel(adaptedModel.getClass()).adapters().forEach((adapter, names) -> {
                    if (invokeCustomApply(adapter, adaptedModel, scope, names)) customBones.addAll(names);
                });
                java.util.LinkedHashSet<String> resolved = new java.util.LinkedHashSet<>(bones.values());
                resolved.addAll(customBones);
                APPLIED_MODELS.put(scope, new AppliedModel(model.getClass(), safeMappings,
                    Collections.unmodifiableSet(resolved), Collections.unmodifiableSet(customBones)));
            } else {
                model = null;
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            String key = renderer == null ? entity.getType().toString() : renderer.getClass().getName();
            if (recordRenderFailureOnce(key)) {
                MLuaLogger.LOGGER.error("[MLua] Could not prepare adapted model rendering for {} (first affected entity: {})",
                    key, entity.getUUID(), failure);
            }
            model = null;
            bones.clear();
        }
        if (model != null) {
            MLuaEntityModelRenderState.push(entity, model, scope, MLuaBonePose.getRenderPose(scope), bones);
        } else if (MLuaEntityModelRenderState.hasActiveFrame()) {
            // Preserve nesting even when the inner renderer has no adapter; otherwise its RETURN hook
            // would pop and restore the adapted outer entity's model scope.
            MLuaEntityModelRenderState.pushMask(entity);
        }
    }

    /** Closes the matching entity model scope and restores any interrupted part transforms. */
    public static void endEntityRender(Entity entity) {
        MLuaEntityModelRenderState.endEntityRender(entity);
    }

    /** Closes only the matching top scope when a native renderer exits exceptionally. */
    public static void abortEntityRender(Entity entity) {
        MLuaEntityModelRenderState.abortEntityRender(entity);
    }

    /** Restores Lua model overrides when a native renderer exits exceptionally. */
    public static void abortEntityRender(Entity entity, Object renderer) {
        if (entity == null) return;
        if (entity instanceof LivingEntity) {
            Object model = findAdaptedModel(renderer);
            if (model != null) MLuaClientModelManager.restoreOverrides(model);
            abortEntityRender(entity);
            return;
        }
        abortEntityRender(entity);
    }

    /** Applies an entity's registered bone pose immediately before Minecraft submits that model part. */
    public static void beginModelPartRender(ModelPart part) {
        MLuaEntityModelRenderState.beginModelPartRender(part);
    }

    /** Restores the model part after its native draw, keeping per-frame state out of the shared model instance. */
    public static void endModelPartRender(ModelPart part) {
        MLuaEntityModelRenderState.endModelPartRender(part);
    }

    /** Adds or updates one mapping for an addon while preserving its other registered bones. */
    public static void registerBone(String modelClass, String boneName, String partPath, Owner owner) {
        LinkedHashMap<String, String> part = new LinkedHashMap<>();
        part.put(boneName, partPath);
        register(modelClass, part, owner);
    }

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
        Map<Owner, CustomAdapter> customForClass = CUSTOM_ADAPTERS.get(className);
        if (customForClass != null) {
            for (CustomAdapter adapter : customForClass.values()) {
                for (String bone : registeredParts.keySet()) {
                    if (adapter.bones.contains(bone)) {
                        throw new IllegalStateException("Native ModelPart bone '" + bone + "' for " + className
                            + " is already controlled by a custom model adapter");
                    }
                }
            }
        }
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
                        String existingPath = entry.getValue().get(bone);
                        String requestedPath = registeredParts.get(bone);
                        if (requestedPath.equals(existingPath)) continue;
                        String addon = entry.getKey().modId().isBlank() ? "another owner" : "addon '" + entry.getKey().modId() + "'";
                        throw new IllegalStateException("Model bone '" + bone + "' for " + className
                            + " maps to '" + existingPath + "' in " + addon
                            + " and cannot also map to '" + requestedPath + "'");
                    }
                }
            }
            int modelPartCount = owners.values().stream().mapToInt(Map::size).sum();
            if (customForClass != null) modelPartCount += customForClass.values().stream()
                .mapToInt(adapter -> adapter.bones.size()).sum();
            for (String bone : registeredParts.keySet()) {
                if (!ownedParts.containsKey(bone)) addedParts[0]++;
            }
            int ownerPartCount = registeredPartsForOwner(resolvedOwner);
            if (ownerPartCount + addedParts[0] > MAX_PARTS_PER_OWNER) {
                throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' already has the maximum of "
                    + MAX_PARTS_PER_OWNER + " model adapter bones across static and custom adapters");
            }
            if (modelPartCount + addedParts[0] > MAX_PARTS_PER_MODEL) {
                throw new IllegalStateException("Model adapter '" + className + "' is limited to "
                    + MAX_PARTS_PER_MODEL + " bones across all addons");
            }
            if (registeredPartCount + registeredCustomPartCount + addedParts[0] > MAX_TOTAL_PARTS) {
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

    /** Registers the field path that resolves a renderer instance to its adapted model. */
    public static synchronized void registerRendererAdapter(String rendererClass, String modelFieldPath, Owner owner) {
        String className = normalizeClassName(rendererClass);
        String path = normalizeFieldPath(modelFieldPath);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        Map<Owner, String> existing = RENDERER_ADAPTERS.get(className);
        if (existing == null || !existing.containsKey(resolvedOwner)) {
            long totalAdapters = RENDERER_ADAPTERS.values().stream().mapToLong(Map::size).sum();
            if (totalAdapters >= MAX_RENDERER_ADAPTERS) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_RENDERER_ADAPTERS
                    + " renderer model adapters registered across all addons");
            }
            long ownedAdapters = RENDERER_ADAPTERS.values().stream()
                .filter(adapters -> adapters.containsKey(resolvedOwner)).count();
            if (ownedAdapters >= MAX_RENDERER_ADAPTERS_PER_OWNER) {
                throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' already has the maximum of "
                    + MAX_RENDERER_ADAPTERS_PER_OWNER + " renderer model adapters");
            }
        }
        RENDERER_ADAPTERS.compute(className, (ignored, current) -> {
            if (current == null && RENDERER_ADAPTERS.size() >= MAX_RENDERER_CLASSES) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_RENDERER_CLASSES
                    + " renderer adapter classes registered");
            }
            LinkedHashMap<Owner, String> owners = current == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            for (Map.Entry<Owner, String> entry : owners.entrySet()) {
                if (!entry.getKey().equals(resolvedOwner) && !entry.getValue().equals(path)) {
                    String addon = entry.getKey().modId().isBlank() ? "another owner" : "addon '" + entry.getKey().modId() + "'";
                    throw new IllegalStateException("Renderer model field for " + className
                        + " is already registered differently by " + addon);
                }
            }
            owners.put(resolvedOwner, path);
            return Map.copyOf(owners);
        });
        clearMappingCaches();
    }

    public static synchronized boolean unregisterRendererAdapter(String rendererClass, Owner owner) {
        String className = normalizeClassName(rendererClass);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        RENDERER_ADAPTERS.computeIfPresent(className, (ignored, current) -> {
            if (!current.containsKey(resolvedOwner)) return current;
            LinkedHashMap<Owner, String> owners = new LinkedHashMap<>(current);
            owners.remove(resolvedOwner);
            removed.set(true);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    /** Registers a Lua function that resolves bone names to native model-part paths for a model instance. */
    public static synchronized MLuaConnection registerResolver(String modelClass, LuaValue callback,
            MLuaContext context, Owner owner) {
        String className = normalizeClassName(modelClass);
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Model adapter resolver must be a Lua function");
        }
        if (context == null) throw new IllegalArgumentException("Model adapter resolver needs its owning Lua context");
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        Map<Owner, AdapterResolver> existing = ADAPTER_RESOLVERS.get(className);
        if (existing == null || !existing.containsKey(resolvedOwner)) {
            long totalResolvers = ADAPTER_RESOLVERS.values().stream().mapToLong(Map::size).sum();
            if (totalResolvers >= MAX_RESOLVERS) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_RESOLVERS
                    + " Lua model adapter resolvers registered across all addons");
            }
            long ownedCount = ADAPTER_RESOLVERS.values().stream()
                .filter(resolvers -> resolvers.containsKey(resolvedOwner)).count();
            if (ownedCount >= MAX_RESOLVERS_PER_OWNER) {
                throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' already has the maximum of "
                    + MAX_RESOLVERS_PER_OWNER + " model adapter resolvers");
            }
        }
        java.util.concurrent.atomic.AtomicReference<AdapterResolver> registered = new java.util.concurrent.atomic.AtomicReference<>();
        ADAPTER_RESOLVERS.compute(className, (ignored, current) -> {
            if (current == null && ADAPTER_RESOLVERS.size() >= MAX_RESOLVER_CLASSES) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_RESOLVER_CLASSES
                    + " Lua model adapter resolvers registered");
            }
            LinkedHashMap<Owner, AdapterResolver> owners = current == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            AdapterResolver replacement = new AdapterResolver(className, callback, context, resolvedOwner,
                NEXT_RESOLVER_ORDER.getAndIncrement());
            AdapterResolver previous = owners.put(resolvedOwner, replacement);
            registered.set(replacement);
            if (previous != null) previous.active = false;
            return Map.copyOf(owners);
        });
        clearMappingCaches();
        AdapterResolver resolver = registered.get();
        return MLuaConnection.owned(resolver, MLuaModelAdapterRegistry::unregisterResolver,
            MLuaModelAdapterRegistry::isResolverActive);
    }

    /** Registers Lua render hooks for model classes whose parts are not Minecraft ModelPart instances. */
    public static synchronized MLuaConnection registerCustomAdapter(String modelClass, List<String> bones,
            LuaValue apply, LuaValue applyPose, MLuaContext context, Owner owner) {
        String className = normalizeClassName(modelClass);
        if (bones == null || bones.isEmpty() || bones.size() > MAX_PARTS_PER_MODEL) {
            throw new IllegalArgumentException("Custom model adapters need between 1 and "
                + MAX_PARTS_PER_MODEL + " named bones");
        }
        if (apply == null || !apply.isfunction()) {
            throw new IllegalArgumentException("Custom model adapters require an Apply Lua function");
        }
        if (applyPose != null && !applyPose.isnil() && !applyPose.isfunction()) {
            throw new IllegalArgumentException("Custom model adapter ApplyPose must be a Lua function when provided");
        }
        if (context == null) throw new IllegalArgumentException("Custom model adapter needs its owning Lua context");
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        LinkedHashSet<String> normalizedBones = new LinkedHashSet<>();
        for (String name : bones) {
            String bone = name == null ? "" : name.trim();
            validatePartPath(bone, "root");
            if (!normalizedBones.add(bone)) {
                throw new IllegalArgumentException("Custom model adapter bone is listed more than once: " + bone);
            }
        }
        Map<Owner, CustomAdapter> existing = CUSTOM_ADAPTERS.get(className);
        CustomAdapter previous = existing == null ? null : existing.get(resolvedOwner);
        if (existing == null && CUSTOM_ADAPTERS.size() >= MAX_CUSTOM_ADAPTER_CLASSES) {
            throw new IllegalStateException("MLua already has the maximum of " + MAX_CUSTOM_ADAPTER_CLASSES
                + " custom model adapter classes registered");
        }
        if (previous == null) {
            long totalAdapters = CUSTOM_ADAPTERS.values().stream().mapToLong(Map::size).sum();
            if (totalAdapters >= MAX_CUSTOM_ADAPTERS) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_CUSTOM_ADAPTERS
                    + " custom model adapters registered across all addons");
            }
            long ownedClasses = CUSTOM_ADAPTERS.values().stream()
                .filter(adapters -> adapters.containsKey(resolvedOwner)).count();
            if (ownedClasses >= MAX_CUSTOM_ADAPTERS_PER_OWNER) {
                throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' already has the maximum of "
                    + MAX_CUSTOM_ADAPTERS_PER_OWNER + " custom model adapters");
            }
        }
        int existingParts = existing == null ? 0 : existing.values().stream()
            .mapToInt(adapter -> adapter.bones.size()).sum();
        Map<Owner, Map<String, String>> nativeAdapters = ADAPTERS.get(className);
        int nativeParts = nativeAdapters == null ? 0 : nativeAdapters.values().stream().mapToInt(Map::size).sum();
        int replacementParts = existingParts + nativeParts
            - (previous == null ? 0 : previous.bones.size()) + normalizedBones.size();
        int ownerPartCount = registeredPartsForOwner(resolvedOwner)
            - (previous == null ? 0 : previous.bones.size()) + normalizedBones.size();
        if (ownerPartCount > MAX_PARTS_PER_OWNER) {
            throw new IllegalStateException("Addon '" + resolvedOwner.modId() + "' is limited to "
                + MAX_PARTS_PER_OWNER + " model adapter bones across static and custom adapters");
        }
        if (replacementParts > MAX_PARTS_PER_MODEL) {
            throw new IllegalStateException("Custom adapters for " + className + " are limited to "
                + MAX_PARTS_PER_MODEL + " bones across all addons");
        }
        if (registeredPartCount + registeredCustomPartCount - (previous == null ? 0 : previous.bones.size())
                + normalizedBones.size() > MAX_TOTAL_PARTS) {
            throw new IllegalStateException("MLua already has the maximum of " + MAX_TOTAL_PARTS
                + " custom model adapter bones registered");
        }
        if (nativeAdapters != null) {
            for (Map<String, String> nativeMapping : nativeAdapters.values()) {
                for (String bone : normalizedBones) {
                    if (nativeMapping.containsKey(bone)) {
                        throw new IllegalStateException("Custom model bone '" + bone + "' for " + className
                            + " already has a native ModelPart mapping");
                    }
                }
            }
        }
        for (Map.Entry<Owner, CustomAdapter> entry : existing == null ? Map.<Owner, CustomAdapter>of().entrySet()
                : existing.entrySet()) {
            if (entry.getKey().equals(resolvedOwner)) continue;
            for (String bone : normalizedBones) {
                if (entry.getValue().bones.contains(bone)) {
                    throw new IllegalStateException("Custom model bone '" + bone + "' for " + className
                        + " is already controlled by another addon adapter");
                }
            }
        }
        CustomAdapter replacement = new CustomAdapter(className, List.copyOf(normalizedBones), apply, applyPose,
            context, resolvedOwner, NEXT_RESOLVER_ORDER.getAndIncrement());
        CUSTOM_ADAPTERS.compute(className, (ignored, current) -> {
            LinkedHashMap<Owner, CustomAdapter> owners = current == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            if (previous != null) previous.active = false;
            owners.put(resolvedOwner, replacement);
            return Map.copyOf(owners);
        });
        registeredCustomPartCount += normalizedBones.size() - (previous == null ? 0 : previous.bones.size());
        clearMappingCaches();
        return MLuaConnection.owned(replacement, MLuaModelAdapterRegistry::unregisterCustomAdapter,
            MLuaModelAdapterRegistry::isCustomAdapterActive);
    }

    private static synchronized boolean isCustomAdapterActive(CustomAdapter expected) {
        if (expected == null || !expected.active) return false;
        Map<Owner, CustomAdapter> current = CUSTOM_ADAPTERS.get(expected.modelClass);
        return current != null && current.get(expected.owner) == expected;
    }

    private static synchronized boolean unregisterCustomAdapter(CustomAdapter expected) {
        if (expected == null) return false;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        CUSTOM_ADAPTERS.computeIfPresent(expected.modelClass, (ignored, current) -> {
            if (current.get(expected.owner) != expected) return current;
            expected.active = false;
            registeredCustomPartCount -= expected.bones.size();
            removed.set(true);
            LinkedHashMap<Owner, CustomAdapter> owners = new LinkedHashMap<>(current);
            owners.remove(expected.owner);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    public static synchronized boolean unregisterCustomAdapter(String modelClass, Owner owner) {
        String className = normalizeClassName(modelClass);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        CUSTOM_ADAPTERS.computeIfPresent(className, (ignored, current) -> {
            CustomAdapter adapter = current.get(resolvedOwner);
            if (adapter == null) return current;
            adapter.active = false;
            registeredCustomPartCount -= adapter.bones.size();
            removed.set(true);
            LinkedHashMap<Owner, CustomAdapter> owners = new LinkedHashMap<>(current);
            owners.remove(resolvedOwner);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    public static synchronized boolean unregisterResolver(String modelClass, Owner owner) {
        String className = normalizeClassName(modelClass);
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        ADAPTER_RESOLVERS.computeIfPresent(className, (ignored, current) -> {
            if (!current.containsKey(resolvedOwner)) return current;
            LinkedHashMap<Owner, AdapterResolver> owners = new LinkedHashMap<>(current);
            AdapterResolver removedResolver = owners.remove(resolvedOwner);
            if (removedResolver != null) removedResolver.active = false;
            removed.set(true);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    /** Removes a failed resolver only if it is still the current registration for its owner. */
    private static synchronized boolean unregisterResolver(AdapterResolver expected) {
        if (expected == null) return false;
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        ADAPTER_RESOLVERS.computeIfPresent(expected.modelClass(), (ignored, current) -> {
            if (current.get(expected.owner()) != expected) return current;
            expected.active = false;
            LinkedHashMap<Owner, AdapterResolver> owners = new LinkedHashMap<>(current);
            owners.remove(expected.owner());
            removed.set(true);
            return owners.isEmpty() ? null : Map.copyOf(owners);
        });
        if (removed.get()) clearMappingCaches();
        return removed.get();
    }

    private static synchronized boolean isResolverActive(AdapterResolver expected) {
        if (expected == null || !expected.active) return false;
        Map<Owner, AdapterResolver> current = ADAPTER_RESOLVERS.get(expected.modelClass());
        return current != null && current.get(expected.owner()) == expected;
    }

    public static String registeredRendererModelField(String rendererClass) {
        String className = normalizeClassName(rendererClass);
        Map<Owner, String> owners = RENDERER_ADAPTERS.get(className);
        return owners == null || owners.isEmpty() ? null : owners.values().iterator().next();
    }

    public static synchronized int registeredRendererClassCount() { return RENDERER_ADAPTERS.size(); }
    public static synchronized int registeredRendererAdapterCount() {
        return RENDERER_ADAPTERS.values().stream().mapToInt(Map::size).sum();
    }
    public static synchronized int registeredRendererAdapterCount(Owner owner) {
        if (owner == null) return 0;
        return (int) RENDERER_ADAPTERS.values().stream()
            .filter(adapters -> adapters.containsKey(owner)).count();
    }
    public static synchronized int registeredResolverClassCount() { return ADAPTER_RESOLVERS.size(); }
    public static synchronized int registeredCustomAdapterClassCount() { return CUSTOM_ADAPTERS.size(); }
    public static synchronized int registeredCustomAdapterCount(Owner owner) {
        if (owner == null) return 0;
        return (int) CUSTOM_ADAPTERS.values().stream()
            .filter(adapters -> adapters.containsKey(owner)).count();
    }
    public static synchronized int registeredCustomPartCount() { return registeredCustomPartCount; }
    public static synchronized int registeredResolverCount(Owner owner) {
        if (owner == null) return 0;
        return (int) ADAPTER_RESOLVERS.values().stream()
            .filter(resolvers -> resolvers.containsKey(owner)).count();
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
        for (String rendererClass : RENDERER_ADAPTERS.keySet()) {
            RENDERER_ADAPTERS.computeIfPresent(rendererClass, (ignored, current) -> {
                LinkedHashMap<Owner, String> owners = new LinkedHashMap<>(current);
                if (!owners.keySet().removeIf(predicate)) return current;
                removed.set(true);
                return owners.isEmpty() ? null : Map.copyOf(owners);
            });
        }
        for (String modelClass : ADAPTER_RESOLVERS.keySet()) {
            ADAPTER_RESOLVERS.computeIfPresent(modelClass, (ignored, current) -> {
                LinkedHashMap<Owner, AdapterResolver> owners = new LinkedHashMap<>(current);
                boolean changed = owners.entrySet().removeIf(entry -> {
                    if (!predicate.test(entry.getKey())) return false;
                    entry.getValue().active = false;
                    return true;
                });
                if (!changed) return current;
                removed.set(true);
                return owners.isEmpty() ? null : Map.copyOf(owners);
            });
        }
        for (String modelClass : CUSTOM_ADAPTERS.keySet()) {
            CUSTOM_ADAPTERS.computeIfPresent(modelClass, (ignored, current) -> {
                LinkedHashMap<Owner, CustomAdapter> owners = new LinkedHashMap<>(current);
                boolean changed = owners.entrySet().removeIf(entry -> {
                    if (!predicate.test(entry.getKey())) return false;
                    CustomAdapter adapter = entry.getValue();
                    adapter.active = false;
                    registeredCustomPartCount -= adapter.bones.size();
                    return true;
                });
                if (!changed) return current;
                removed.set(true);
                return owners.isEmpty() ? null : Map.copyOf(owners);
            });
        }
        return removed.get();
    }

    public static synchronized int registeredModelClassCount() { return ADAPTERS.size(); }

    public static synchronized int registeredPartCount() { return registeredPartCount; }

    /** Counts the calling addon's static and custom bone mappings against their shared budget. */
    public static synchronized int registeredPartsForOwner(Owner owner) {
        if (owner == null) return 0;
        int count = ADAPTERS.values().stream()
            .mapToInt(adapters -> adapters.getOrDefault(owner, Map.of()).size()).sum();
        return count + CUSTOM_ADAPTERS.values().stream()
            .mapToInt(adapters -> {
                CustomAdapter adapter = adapters.get(owner);
                return adapter == null ? 0 : adapter.bones.size();
            }).sum();
    }

    /** Returns adapter bones that resolved on the model used by this entity's latest render. */
    public static java.util.Set<String> appliedBoneNames(MLuaEntityScope scope) {
        if (scope == null) return java.util.Set.of();
        AppliedModel applied = APPLIED_MODELS.get(scope);
        if (applied == null) return java.util.Set.of();
        java.util.LinkedHashSet<String> current = new java.util.LinkedHashSet<>(applied.customBones());
        for (String bone : applied.resolvedBones()) if (applied.mappings().containsKey(bone)) current.add(bone);
        return Collections.unmodifiableSet(current);
    }

    /** Returns the adapter and bone paths seen on an entity's most recently rendered model. */
    public static AdapterSnapshot appliedAdapter(MLuaEntityScope scope) {
        if (scope == null) return null;
        AppliedModel applied = APPLIED_MODELS.get(scope);
        if (applied == null) return null;
        java.util.TreeSet<String> resolved = new java.util.TreeSet<>(applied.customBones());
        for (String bone : applied.resolvedBones()) {
            if (applied.mappings().containsKey(bone)) resolved.add(bone);
        }
        return new AdapterSnapshot(applied.modelClass().getName(), applied.mappings(),
            Collections.unmodifiableSet(resolved), applied.customBones());
    }

    /** Records a mapped model resolved through a non-living renderer for API inspection and attachments. */
    public static java.util.Set<String> recordResolvedModel(Object model, MLuaEntityScope scope) {
        if (scope == null) return java.util.Set.of();
        if (model == null) {
            APPLIED_MODELS.remove(scope);
            return java.util.Set.of();
        }
        Map<String, String> mappings = findMappingForModel(model);
        if (!isAdaptedModel(model)) {
            APPLIED_MODELS.remove(scope);
            return java.util.Set.of();
        }
        if (mappings == null) mappings = Map.of();
        java.util.TreeSet<String> resolved = new java.util.TreeSet<>();
        Map<String, ModelPart> parts = resolvedParts(model, mappings);
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (parts.containsKey(entry.getKey())) resolved.add(entry.getKey());
            else reportMissingPath(model.getClass(), entry.getKey(), entry.getValue());
        }
        java.util.LinkedHashSet<String> customBones = new java.util.LinkedHashSet<>();
        customAdaptersForModel(model.getClass()).adapters().values().forEach(customBones::addAll);
        resolved.addAll(customBones);
        java.util.Set<String> snapshot = Collections.unmodifiableSet(resolved);
        APPLIED_MODELS.put(scope, new AppliedModel(model.getClass(), mappings, snapshot,
            Collections.unmodifiableSet(customBones)));
        return snapshot;
    }

    public static void clearEntity(MLuaEntityScope scope) {
        if (scope != null) APPLIED_MODELS.remove(scope);
    }

    /** Drops cached model snapshots for entities in the unloaded logical world. */
    public static void clearDimension(String dimension, Side side) {
        if (dimension == null || side == null) return;
        APPLIED_MODELS.keySet().removeIf(scope ->
            scope.side() == side && scope.dimension().equals(dimension));
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

    /** Returns declared custom bones for one exact class, in adapter registration order. */
    public static synchronized List<String> registeredCustomBones(String modelClass) {
        String className = normalizeClassName(modelClass);
        Map<Owner, CustomAdapter> adapters = CUSTOM_ADAPTERS.get(className);
        if (adapters == null || adapters.isEmpty()) return List.of();
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        adapters.values().stream().sorted(Comparator.comparingLong(adapter -> adapter.registrationOrder))
            .forEach(adapter -> adapter.bones.forEach(bone -> names.putIfAbsent(bone, Boolean.TRUE)));
        return List.copyOf(names.keySet());
    }

    private static void clearMappingCaches() {
        MAPPING_GENERATION.incrementAndGet();
        RESOLVED_MAPPINGS.clear();
        RESOLVED_CUSTOM_ADAPTERS.clear();
        RESOLVED_INSTANCE_MAPPINGS.clear();
        APPLIED_MODELS.clear();
        MLuaModelFieldAccess.clearPathSegments();
        MLuaModelPartResolver.clear();
        REPORTED_RENDER_FAILURES.clear();
    }

    /** Keeps per-class failure deduplication from retaining an unbounded list of renderer class names. */
    private static boolean recordRenderFailureOnce(String key) {
        synchronized (REPORTED_RENDER_FAILURES) {
            if (!REPORTED_RENDER_FAILURES.add(key)) return false;
            if (REPORTED_RENDER_FAILURES.size() > MAX_RENDERER_WARNING_KEYS) {
                var oldest = REPORTED_RENDER_FAILURES.iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            return true;
        }
    }

    private static boolean isAdaptedModel(Object model) {
        return model != null && (findMappingForModel(model) != null
            || !customAdaptersForModel(model.getClass()).adapters().isEmpty());
    }

    private static CustomAdapterResolution customAdaptersForModel(Class<?> modelClass) {
        if (modelClass == null) return CustomAdapterResolution.EMPTY;
        synchronized (RESOLVED_CUSTOM_ADAPTERS) {
            CustomAdapterResolution cached = RESOLVED_CUSTOM_ADAPTERS.get(modelClass);
            if (cached != null) return cached;
            ArrayList<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) hierarchy.add(type);
            Collections.reverse(hierarchy);
            LinkedHashMap<String, CustomAdapter> winners = new LinkedHashMap<>();
            ArrayList<CustomAdapter> ordered = new ArrayList<>();
            for (Class<?> type : hierarchy) {
                Map<Owner, CustomAdapter> registered = CUSTOM_ADAPTERS.get(type.getName());
                if (registered == null) continue;
                registered.values().stream().sorted(Comparator.comparingLong(adapter -> adapter.registrationOrder))
                    .forEach(adapter -> {
                        if (!adapter.active) return;
                        ordered.add(adapter);
                        for (String bone : adapter.bones) winners.put(bone, adapter);
                    });
            }
            LinkedHashMap<CustomAdapter, List<String>> selected = new LinkedHashMap<>();
            for (CustomAdapter adapter : ordered) {
                ArrayList<String> bones = new ArrayList<>();
                for (String bone : adapter.bones) if (winners.get(bone) == adapter) bones.add(bone);
                if (!bones.isEmpty()) selected.put(adapter, List.copyOf(bones));
            }
            CustomAdapterResolution result = selected.isEmpty() ? CustomAdapterResolution.EMPTY
                : new CustomAdapterResolution(Collections.unmodifiableMap(selected),
                    Collections.unmodifiableMap(new LinkedHashMap<>(winners)));
            RESOLVED_CUSTOM_ADAPTERS.put(modelClass, result);
            return result;
        }
    }

    private static CustomAdapter customAdapterForBone(Class<?> modelClass, String boneName) {
        return customAdaptersForModel(modelClass).byBone().get(boneName);
    }

    private static LuaTable customBoneStates(MLuaEntityScope scope, List<String> bones) {
        LuaTable result = new LuaTable();
        Map<String, MLuaCFrame> animationPoses = renderPose(scope);
        Map<String, MLuaClientModelManager.PartState> partStates =
            MLuaClientModelManager.getMergedPartStates(scope, bones);
        for (String bone : bones) {
            LuaTable state = new LuaTable();
            MLuaCFrame animation = animationPoses.get(bone);
            if (animation != null) state.set("Animation", animation.toLua());
            MLuaClientModelManager.PartState override =
                partStates.get(MLuaClientModelManager.normalizePartName(bone));
            if (override != null) {
                if (override.cframe() != null) state.set("CFrame", override.cframe().toLua());
                if (override.offset() != null) state.set("Offset", override.offset());
                if (override.scale() != null) state.set("Scale", override.scale());
                if (override.visible() != null) state.set("Visible", LuaValue.valueOf(override.visible()));
            }
            result.set(bone, state);
        }
        return result;
    }

    private static Map<String, MLuaCFrame> renderPose(MLuaEntityScope scope) {
        return MLuaEntityModelRenderState.renderPose(scope);
    }

    private static Entity entityForScope(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope == null || minecraft.level == null
                || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static boolean invokeCustomApply(CustomAdapter adapter, Object model,
            MLuaEntityScope scope, List<String> bones) {
        Entity entity = entityForScope(scope);
        LuaValue entityValue = entity == null ? LuaValue.NIL
            : dev.mlua.modules.players.classes.MLuaEntityWrapper.entityTable(entity, adapter.context.registration());
        LuaValue modelValue;
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            modelValue = LuaValue.userdataOf(frameScope.view().scopedReference(model));
            LuaTable states = customBoneStates(scope, bones);
            invokeCustomCallback(adapter, adapter.apply,
                LuaValue.varargsOf(new LuaValue[]{modelValue, entityValue, states}), frameScope);
            return adapter.active;
        } finally {
            frameScope.close();
        }
    }

    private static boolean invokeCustomApplyPose(CustomAdapter adapter, Object model, String bone,
            PoseStack poseStack, MLuaEntityScope scope) {
        if (adapter == null || !adapter.active || adapter.applyPose == null || adapter.applyPose.isnil()
                || !adapter.bones.contains(bone)) return false;
        Entity entity = entityForScope(scope);
        LuaValue entityValue = entity == null ? LuaValue.NIL
            : dev.mlua.modules.players.classes.MLuaEntityWrapper.entityTable(entity, adapter.context.registration());
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            LuaValue modelValue = LuaValue.userdataOf(frameScope.view().scopedReference(model));
            LuaValue poseValue = LuaValue.userdataOf(frameScope.view().scopedReference(poseStack));
            LuaTable state = customBoneStates(scope, List.of(bone)).get(bone).checktable();
            Varargs result = invokeCustomCallback(adapter, adapter.applyPose,
                LuaValue.varargsOf(new LuaValue[]{modelValue, LuaValue.valueOf(bone), poseValue,
                    entityValue, state}), frameScope);
            return adapter.active && result.arg1().isboolean() && result.arg1().toboolean();
        } finally {
            frameScope.close();
        }
    }

    private static Varargs invokeCustomCallback(CustomAdapter adapter, LuaValue callback, Varargs args,
            MLuaRenderFrameScope frameScope) {
        if (!adapter.active) return LuaValue.NONE;
        try {
            try (MLuaRenderFrameScope.ScopeBinding ignored = frameScope.view().enter()) {
                return MLuaLuaBindings.withContext(adapter.context, () -> {
                    if (!adapter.active) return LuaValue.NONE;
                    if (adapter.context.globals() == null) return callback.invoke(args);
                    try (LuaInstructionBudget.Scope instructionBudget = adapter.context.globals()
                            .beginInstructionBudget(MAX_RESOLVER_LUA_INSTRUCTIONS)) {
                        return callback.invoke(args);
                    }
                });
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableCustomAdapter(adapter, failure);
            return LuaValue.NONE;
        }
    }

    private static void disableCustomAdapter(CustomAdapter adapter, Throwable failure) {
        if (adapter == null || !adapter.active) return;
        synchronized (MLuaModelAdapterRegistry.class) {
            if (!adapter.active) return;
            adapter.active = false;
            CUSTOM_ADAPTERS.computeIfPresent(adapter.modelClass, (ignored, current) -> {
                if (current.get(adapter.owner) != adapter) return current;
                LinkedHashMap<Owner, CustomAdapter> owners = new LinkedHashMap<>(current);
                owners.remove(adapter.owner);
                registeredCustomPartCount -= adapter.bones.size();
                return owners.isEmpty() ? null : Map.copyOf(owners);
            });
            clearMappingCaches();
        }
        MLuaLogger.LOGGER.error("[MLua] Disabled custom model adapter for addon '{}' on {} after an error",
            adapter.owner.modId(), adapter.modelClass, failure);
    }

    /** Called by loader render hooks on the client render thread. Lua resolvers run once per live model instance. */
    public static java.util.Set<String> apply(Object model, MLuaEntityScope scope) {
        if (model == null || scope == null) return java.util.Set.of();
        if (!MLuaClientRenderThread.require("model adapter")) return java.util.Set.of();
        Map<String, String> mapping = findMappingForModel(model);
        Map<CustomAdapter, List<String>> customAdapters = customAdaptersForModel(model.getClass()).adapters();
        if (mapping == null && customAdapters.isEmpty()) {
            APPLIED_MODELS.remove(scope);
            return java.util.Set.of();
        }
        if (mapping == null) mapping = Map.of();
        Map<String, MLuaCFrame> animation = MLuaBonePose.getRenderPose(scope);
        java.util.LinkedHashSet<String> applied = new java.util.LinkedHashSet<>();
        Map<String, ModelPart> resolvedParts = resolvedParts(model, mapping);
        Map<String, MLuaClientModelManager.PartState> partStates =
            MLuaClientModelManager.getMergedPartStates(scope, mapping.keySet());
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            ModelPart part = resolvedParts.get(entry.getKey());
            if (part == null) {
                reportMissingPath(model.getClass(), entry.getKey(), entry.getValue());
                continue;
            }
            applied.add(entry.getKey());
            MLuaCFrame pose = animation == null ? null : animation.get(entry.getKey());
            if (pose != null) {
                MLuaClientModelManager.rememberOverridePose(model, part);
                applyPose(part, pose);
            }
            MLuaClientModelManager.applyState(model, part,
                partStates.get(MLuaClientModelManager.normalizePartName(entry.getKey())));
        }
        java.util.LinkedHashSet<String> customBones = new java.util.LinkedHashSet<>();
        customAdapters.forEach((adapter, bones) -> {
            if (invokeCustomApply(adapter, model, scope, bones)) customBones.addAll(bones);
        });
        applied.addAll(customBones);
        java.util.Set<String> resolved = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(applied));
        APPLIED_MODELS.put(scope, new AppliedModel(model.getClass(), mapping, resolved,
            Collections.unmodifiableSet(customBones)));
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
            java.util.TreeMap<String, String> merged = new java.util.TreeMap<>();
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

    /** Resolves static paths and Lua adapter paths for this model instance. */
    public static Map<String, String> findMappingForModel(Object model) {
        if (model != null && !MLuaClientRenderThread.require("Lua model adapter resolver")) {
            return findMapping(model.getClass());
        }
        return findMappingForModel(model, 0);
    }

    private static Map<String, String> findMappingForModel(Object model, int invalidationRetries) {
        if (model == null) return null;
        long generation = MAPPING_GENERATION.get();
        synchronized (RESOLVED_INSTANCE_MAPPINGS) {
            Map<String, String> cached = RESOLVED_INSTANCE_MAPPINGS.get(model);
            if (cached != null && generation == MAPPING_GENERATION.get()) return cached.isEmpty() ? null : cached;
        }
        ArrayList<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) hierarchy.add(type);
        Collections.reverse(hierarchy);
        java.util.TreeMap<String, String> merged = new java.util.TreeMap<>();
        for (Class<?> type : hierarchy) {
            Map<Owner, Map<String, String>> staticMappings = ADAPTERS.get(type.getName());
            java.util.TreeMap<String, String> classMappings = new java.util.TreeMap<>();
            if (staticMappings != null) {
                staticMappings.values().forEach(classMappings::putAll);
            }
            Map<Owner, AdapterResolver> resolvers = ADAPTER_RESOLVERS.get(type.getName());
            if (resolvers != null && !resolvers.isEmpty()) {
                List<AdapterResolver> ordered = resolvers.values().stream()
                    .sorted(Comparator.comparingLong(AdapterResolver::registrationOrder)).toList();
                for (AdapterResolver resolver : ordered) {
                    Map<String, String> resolved = invokeResolver(model, resolver);
                    if (resolved == null || resolved.isEmpty()) continue;
                    boolean conflict = resolved.entrySet().stream().anyMatch(entry ->
                        classMappings.containsKey(entry.getKey())
                            && !classMappings.get(entry.getKey()).equals(entry.getValue()));
                    long newBones = resolved.keySet().stream().filter(name -> !classMappings.containsKey(name)).count();
                    if (conflict || classMappings.size() + newBones > MAX_PARTS_PER_MODEL) {
                        disableResolver(resolver, new IllegalStateException(conflict
                            ? "the resolver conflicts with another adapter's bone mapping on this model class"
                            : "combined adapter mappings exceed " + MAX_PARTS_PER_MODEL + " bones for this model class"));
                        continue;
                    }
                    classMappings.putAll(resolved);
                }
            }
            merged.putAll(classMappings);
        }
        if (generation != MAPPING_GENERATION.get()) {
            return retryAfterInvalidation(model, invalidationRetries);
        }
        Map<String, String> result = merged.isEmpty() ? Map.of() : orderedMap(merged);
        boolean stable;
        synchronized (RESOLVED_INSTANCE_MAPPINGS) {
            stable = generation == MAPPING_GENERATION.get();
            if (stable) RESOLVED_INSTANCE_MAPPINGS.put(model, result);
        }
        if (!stable) return retryAfterInvalidation(model, invalidationRetries);
        return result.isEmpty() ? null : result;
    }

    private static Map<String, String> retryAfterInvalidation(Object model, int invalidationRetries) {
        // A resolver can disable itself or another addon can change mappings while this lookup is running.
        // Retry so healthy instance resolvers survive a sibling resolver failure, but bound retries if a
        // resolver continually mutates registrations from inside its own callback.
        return invalidationRetries < 2
            ? findMappingForModel(model, invalidationRetries + 1)
            : findMapping(model.getClass());
    }

    private static Map<String, String> invokeResolver(Object model, AdapterResolver resolver) {
        if (!resolver.active) return Map.of();
        MLuaRenderFrameScope scope = new MLuaRenderFrameScope();
        MLuaContext context = resolver.context();
        try {
            LuaValue modelValue = LuaValue.userdataOf(scope.view().scopedReference(model));
            final LuaValue[] result = {LuaValue.NIL};
            try (MLuaRenderFrameScope.ScopeBinding ignored = scope.view().enter()) {
                MLuaLuaBindings.withContext(context, () -> {
                    if (!resolver.active) return null;
                    if (context.globals() == null) {
                        result[0] = resolver.callback().call(modelValue);
                    } else {
                        try (LuaInstructionBudget.Scope instructionBudget = context.globals()
                                .beginInstructionBudget(MAX_RESOLVER_LUA_INSTRUCTIONS)) {
                            result[0] = resolver.callback().call(modelValue);
                        }
                    }
                    return null;
                });
            }
            if (!resolver.active) return Map.of();
            if (!result[0].istable()) {
                throw new LuaError("[MLua] Model adapter resolver for " + model.getClass().getName()
                    + " must return a table of bone names to model-part paths.");
            }
            LuaTable table = result[0].checktable();
            if (table.keyCount() > MAX_PARTS_PER_MODEL) {
                throw new LuaError("[MLua] Model adapter resolver returned more than "
                    + MAX_PARTS_PER_MODEL + " bone mappings.");
            }
            LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                LuaValue value = next.arg(2);
                if (!key.isstring() || !value.isstring()) {
                    throw new LuaError("[MLua] Model adapter resolver must return string bone names and paths.");
                }
                String bone = key.tojstring().trim();
                String path = value.tojstring().trim();
                if (bone.isEmpty() || path.isEmpty()) {
                    throw new LuaError("[MLua] Model adapter resolver returned an empty bone name or path.");
                }
                validatePartPath(bone, path);
                mappings.put(bone, path);
            }
            return orderedMap(mappings);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableResolver(resolver, failure);
            return Map.of();
        } finally {
            scope.close();
        }
    }

    private static void disableResolver(AdapterResolver resolver, Throwable failure) {
        unregisterResolver(resolver);
        MLuaLogger.LOGGER.error("[MLua] Disabled model adapter resolver for addon '{}' on {} after an error",
            resolver.owner().modId(), resolver.modelClass(), failure);
    }

    /** Resolves an adapter's Lua-facing bone name to the native model part. */
    public static ModelPart resolvePart(Object model, String boneName) {
        if (model == null || boneName == null || boneName.isBlank()) return null;
        Map<String, String> mapping = findMappingForModel(model);
        String path = mapping == null ? null : mapping.get(boneName);
        return path == null ? null : resolvePath(model, path);
    }

    /**
     * Finds an addon-adapted model held by an entity renderer. Explicit field paths can resolve nested fields. Used after
     * non-living renderers have posed and submitted their model, where Minecraft
     * does not expose a common typed model accessor.
     */
    public static Object findAdaptedModel(Object renderer) {
        if (renderer == null || (ADAPTERS.isEmpty() && ADAPTER_RESOLVERS.isEmpty()
                && CUSTOM_ADAPTERS.isEmpty())) return null;
        String configuredPath = findRendererModelField(renderer.getClass());
        return MLuaRendererModelResolver.findModel(renderer, configuredPath,
            MLuaModelAdapterRegistry::isAdaptedModel);
    }

    private static String findRendererModelField(Class<?> rendererClass) {
        return MLuaModelFieldAccess.rendererModelFieldPath(rendererClass, MAPPING_GENERATION.get(),
            () -> findRendererModelFieldUncached(rendererClass));
    }

    private static String findRendererModelFieldUncached(Class<?> rendererClass) {
        for (Class<?> type = rendererClass; type != null; type = type.getSuperclass()) {
            Map<Owner, String> owners = RENDERER_ADAPTERS.get(type.getName());
            if (owners != null && !owners.isEmpty()) return owners.values().iterator().next();
        }
        return null;
    }

    /** Applies the registered model-part hierarchy to a pose stack for attachments. */
    public static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack) {
        return applyPartTransform(model, boneName, poseStack, null);
    }

    /** Applies the live entity's mapped animation and part state while resolving an attachment bone. */
    public static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack,
                                             MLuaEntityScope scope) {
        if (model == null || boneName == null || boneName.isBlank() || poseStack == null) return false;
        Map<String, String> mapping = findMappingForModel(model);
        String path = mapping == null ? null : mapping.get(boneName);
        if (path == null) {
            CustomAdapter custom = customAdapterForBone(model.getClass(), boneName);
            if (custom != null) return invokeCustomApplyPose(custom, model, boneName, poseStack, scope);
            reportMissingPath(model.getClass(), boneName, null);
            return false;
        }
        return MLuaModelPartTransforms.applyMapped(model, boneName, path, poseStack, scope,
            mapping == null ? Map.of() : mapping);
    }

    /** Applies the transform already present on a rendered model part without adding MLua poses again. */
    public static boolean applyCurrentPartTransform(Object model, String boneName, PoseStack poseStack,
                                                     MLuaEntityScope scope) {
        if (model == null || boneName == null || boneName.isBlank() || poseStack == null) return false;
        Map<String, String> mapping = findMappingForModel(model);
        String path = mapping == null ? null : mapping.get(boneName);
        if (path == null) {
            CustomAdapter custom = customAdapterForBone(model.getClass(), boneName);
            if (custom != null) return invokeCustomApplyPose(custom, model, boneName, poseStack, scope);
            reportMissingPath(model.getClass(), boneName, path);
            return false;
        }
        return MLuaModelPartTransforms.applyCurrent(model, boneName, path, poseStack);
    }

    private static void reportMissingPath(Class<?> modelClass, String boneName, String path) {
        MLuaModelPartResolver.reportMissingPath(modelClass, boneName, path);
    }

    private static ModelPart resolvePath(Object root, String path) {
        return MLuaModelPartResolver.resolvePath(root, path);
    }

    static Map<String, ModelPart> resolvedParts(Object model, Map<String, String> mappings) {
        return MLuaModelPartResolver.resolvedParts(model, mappings, MAPPING_GENERATION);
    }

    static void applyPose(ModelPart part, MLuaCFrame cframe) {
        MLuaClientModelManager.applyCFrameDelta(part, cframe);
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

    private static String normalizeFieldPath(String path) {
        return MLuaModelPartResolver.normalizeRendererFieldPath(path);
    }

    private static void validatePartPath(String boneName, String path) {
        MLuaModelPartResolver.validatePartPath(boneName, path);
    }
}
