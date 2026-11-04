package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.signal.MLuaConnection;
import org.luaj.vm2.LuaValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

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
    static final ConcurrentHashMap<String, Map<Owner, Map<String, String>>> ADAPTERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, Map<Owner, AdapterResolver>> ADAPTER_RESOLVERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, OrderedResolvers> ORDERED_RESOLVERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, Map<Owner, CustomAdapter>> CUSTOM_ADAPTERS = new ConcurrentHashMap<>();
    private static int registeredPartCount;
    private static int registeredCustomPartCount;
    private static final AtomicLong NEXT_RESOLVER_ORDER = new AtomicLong();
    static final Map<Class<?>, CustomAdapterResolution> RESOLVED_CUSTOM_ADAPTERS =
        Collections.synchronizedMap(new WeakHashMap<>());
    static record OrderedResolvers(long generation, List<AdapterResolver> values) { }
    static record CustomAdapterResolution(Map<CustomAdapter, List<String>> adapters,
                                          Map<String, CustomAdapter> byBone) {
        static final CustomAdapterResolution EMPTY = new CustomAdapterResolution(Map.of(), Map.of());
    }

    static final class AdapterResolver {
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

        String modelClass() { return modelClass; }
        LuaValue callback() { return callback; }
        MLuaContext context() { return context; }
        Owner owner() { return owner; }
        long registrationOrder() { return registrationOrder; }
        boolean active() { return active; }
    }
    /** Lua-defined adapter for model hierarchies that do not use Minecraft ModelPart. */
    static final class CustomAdapter {
        final String modelClass;
        final List<String> bones;
        final LuaValue apply;
        final LuaValue applyPose;
        final MLuaContext context;
        final Owner owner;
        final long registrationOrder;
        volatile boolean active = true;

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
        MLuaModelAdapterRenderBridge.beginEntityRender(entity, renderer);
    }

    /** Closes the matching entity model scope and restores any interrupted part transforms. */
    public static void endEntityRender(Entity entity) {
        MLuaModelAdapterRenderBridge.endEntityRender(entity);
    }

    /** Closes only the matching top scope when a native renderer exits exceptionally. */
    public static void abortEntityRender(Entity entity) {
        MLuaModelAdapterRenderBridge.abortEntityRender(entity);
    }

    /** Restores Lua model overrides when a native renderer exits exceptionally. */
    public static void abortEntityRender(Entity entity, Object renderer) {
        MLuaModelAdapterRenderBridge.abortEntityRender(entity, renderer);
    }

    /** Applies an entity's registered bone pose immediately before Minecraft submits that model part. */
    public static void beginModelPartRender(ModelPart part) {
        MLuaModelAdapterRenderBridge.beginModelPartRender(part);
    }

    /** Restores the model part after its native draw, keeping per-frame state out of the shared model instance. */
    public static void endModelPartRender(ModelPart part) {
        MLuaModelAdapterRenderBridge.endModelPartRender(part);
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
        MLuaRendererAdapterRegistry.register(rendererClass, modelFieldPath, owner);
        clearMappingCaches();
    }

    public static synchronized boolean unregisterRendererAdapter(String rendererClass, Owner owner) {
        boolean removed = MLuaRendererAdapterRegistry.unregister(rendererClass, owner);
        if (removed) clearMappingCaches();
        return removed;
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
        return MLuaRendererAdapterRegistry.registeredField(rendererClass);
    }

    public static synchronized int registeredRendererClassCount() { return MLuaRendererAdapterRegistry.classCount(); }
    public static synchronized int registeredRendererAdapterCount() {
        return MLuaRendererAdapterRegistry.adapterCount();
    }
    public static synchronized int registeredRendererAdapterCount(Owner owner) {
        return MLuaRendererAdapterRegistry.adapterCount(owner);
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
        removed.set(MLuaRendererAdapterRegistry.removeOwned(predicate) || removed.get());
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
        return MLuaModelAdapterInspection.appliedBoneNames(scope);
    }

    /** Returns the adapter and bone paths seen on an entity's most recently rendered model. */
    public static AdapterSnapshot appliedAdapter(MLuaEntityScope scope) {
        return MLuaModelAdapterInspection.appliedAdapter(scope);
    }

    /** Records a mapped model resolved through a non-living renderer for API inspection and attachments. */
    public static java.util.Set<String> recordResolvedModel(Object model, MLuaEntityScope scope) {
        return MLuaModelAdapterInspection.recordResolvedModel(model, scope);
    }

    public static void clearEntity(MLuaEntityScope scope) {
        MLuaAppliedModelRegistry.remove(scope);
    }

    /** Drops cached model snapshots for entities in the unloaded logical world. */
    public static void clearDimension(String dimension, Side side) {
        if (dimension == null || side == null) return;
        MLuaAppliedModelRegistry.clearDimension(dimension, side);
    }

    public static void clearAppliedEntities() {
        MLuaAppliedModelRegistry.clear();
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
        MLuaModelAdapterCache.invalidate();
        RESOLVED_CUSTOM_ADAPTERS.clear();
        ORDERED_RESOLVERS.clear();
        MLuaAppliedModelRegistry.clear();
        MLuaModelFieldAccess.clearPathSegments();
        MLuaModelPartResolver.clear();
        MLuaModelAdapterRenderBridge.clearFailureCache();
    }

    static boolean isAdaptedModel(Object model) { return MLuaModelAdapterLookup.isAdaptedModel(model); }

    static CustomAdapterResolution customAdaptersForModel(Class<?> modelClass) {
        return MLuaModelAdapterLookup.customAdaptersForModel(modelClass);
    }

    static CustomAdapter customAdapterForBone(Class<?> modelClass, String boneName) {
        return MLuaModelAdapterLookup.customAdapterForBone(modelClass, boneName);
    }

    static void disableCustomAdapter(CustomAdapter adapter, Throwable failure) {
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
        return MLuaModelAdapterApplication.apply(model, scope);
    }

    public static Map<String, String> findMapping(Class<?> modelClass) {
        return MLuaModelAdapterLookup.findMapping(modelClass);
    }

    /** Resolves static paths and Lua adapter paths for this model instance. */
    public static Map<String, String> findMappingForModel(Object model) {
        return MLuaModelAdapterLookup.findMappingForModel(model);
    }

    static void disableResolver(AdapterResolver resolver, Throwable failure) {
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

    /** Resolves the primary native model so its first render callback can register an adapter. */
    public static Object findRendererModel(Object renderer) {
        if (renderer == null) return null;
        String configuredPath = findRendererModelField(renderer.getClass());
        return MLuaRendererModelResolver.findModelCandidate(renderer, configuredPath);
    }

    /** Checks the renderer's actual model fields without guessing between age-specific models. */
    public static boolean isRendererModelCandidate(Object renderer, Object model) {
        if (renderer == null) return false;
        String configuredPath = findRendererModelField(renderer.getClass());
        return MLuaRendererModelResolver.isModelCandidate(renderer, model, configuredPath);
    }

    private static String findRendererModelField(Class<?> rendererClass) {
        while (true) {
            long generation = MLuaModelAdapterCache.generation();
            String path = MLuaModelFieldAccess.rendererModelFieldPath(rendererClass, generation,
                () -> findRendererModelFieldUncached(rendererClass));
            if (generation == MLuaModelAdapterCache.generation()) return path;
        }
    }

    private static String findRendererModelFieldUncached(Class<?> rendererClass) {
        return MLuaRendererAdapterRegistry.modelFieldFor(rendererClass);
    }

    /** Applies the registered model-part hierarchy to a pose stack for attachments. */
    public static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack) {
        return MLuaModelAdapterApplication.applyPartTransform(model, boneName, poseStack, null);
    }

    /** Applies the live entity's mapped animation and part state while resolving an attachment bone. */
    public static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack,
                                             MLuaEntityScope scope) {
        return MLuaModelAdapterApplication.applyPartTransform(model, boneName, poseStack, scope);
    }

    /** Applies the transform already present on a rendered model part without adding MLua poses again. */
    public static boolean applyCurrentPartTransform(Object model, String boneName, PoseStack poseStack,
                                                     MLuaEntityScope scope) {
        return MLuaModelAdapterApplication.applyCurrentPartTransform(model, boneName, poseStack, scope);
    }

    static void reportMissingPath(Class<?> modelClass, String boneName, String path) {
        MLuaModelPartResolver.reportMissingPath(modelClass, boneName, path);
    }

    static void recordAppliedModel(Object model, MLuaEntityScope scope, Map<String, String> mappings,
                                   java.util.Set<String> resolvedBones, java.util.Set<String> customBones) {
        MLuaAppliedModelRegistry.record(model.getClass(), mappings, resolvedBones, customBones, scope);
    }

    static void clearAppliedModel(MLuaEntityScope scope) {
        MLuaAppliedModelRegistry.remove(scope);
    }

    private static ModelPart resolvePath(Object root, String path) {
        return MLuaModelPartResolver.resolvePath(root, path);
    }

    static Map<String, ModelPart> resolvedParts(Object model, Map<String, String> mappings) {
        return MLuaModelPartResolver.resolvedParts(model, mappings, MLuaModelAdapterCache.generationClock());
    }

    static void applyPose(ModelPart part, MLuaCFrame cframe) {
        MLuaClientModelManager.applyCFrameDelta(part, cframe);
    }

    static String normalizeClassName(String value) {
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

    static <K, V> Map<K, V> orderedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static void validatePartPath(String boneName, String path) {
        MLuaModelPartResolver.validatePartPath(boneName, path);
    }
}
