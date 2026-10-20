package dev.mlua.runtime.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.loaderapi.GeneratedDataResources;
import dev.mlua.loaderapi.GeneratedAssetResources;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MLuaLoaderRegistry {
    private static final ThreadLocal<RegistrationTransaction> ACTIVE_TRANSACTION = new ThreadLocal<>();

    private MLuaLoaderRegistry() {
    }

    /** Starts a script-scoped staging queue for native registry entries. */
    public static RegistrationTransaction beginRegistrationTransaction() {
        return new RegistrationTransaction();
    }

    public static <T> RegistryHandle<T> register(RegistryKind kind, String modId, String name, Supplier<T> supplier) {
        requireRegistriesOpen(kind, modId, name);
        if (kind != RegistryKind.BLOCK_ITEM) java.util.Objects.requireNonNull(supplier, "supplier");
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) {
            return transaction.stage(kind, modId, name,
                () -> checkedRegistrationHandle(kind, modId, name,
                    LoaderApi.registry().register(kind, modId, name, supplier)));
        }
        return checkedRegistrationHandle(kind, modId, name, LoaderApi.registry().register(kind, modId, name, supplier));
    }

    public static RegistryHandle<EntityType<?>> registerEntity(String modId, String name, Supplier<EntityType<?>> supplier, Supplier<AttributeSupplier> attributes) {
        requireRegistriesOpen(RegistryKind.ENTITY, modId, name);
        java.util.Objects.requireNonNull(supplier, "supplier");
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) {
            return transaction.stage(RegistryKind.ENTITY, modId, name,
                () -> checkedRegistrationHandle(RegistryKind.ENTITY, modId, name,
                    LoaderApi.registry().registerEntity(modId, name, supplier, attributes)));
        }
        return checkedRegistrationHandle(RegistryKind.ENTITY, modId, name,
            (RegistryHandle<EntityType<?>>) LoaderApi.registry().registerEntity(modId, name, supplier, attributes));
    }

    public static RegistryHandle<Item> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor) {
        requireRegistriesOpen(RegistryKind.ITEM, modId, name);
        java.util.Objects.requireNonNull(entityType, "entityType");
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) {
            return transaction.stage(RegistryKind.ITEM, modId, name,
                () -> checkedRegistrationHandle(RegistryKind.ITEM, modId, name,
                    (RegistryHandle<Item>) LoaderApi.registry().registerSpawnEgg(
                        modId, name, entityType, primaryColor, secondaryColor)));
        }
        return checkedRegistrationHandle(RegistryKind.ITEM, modId, name,
            (RegistryHandle<Item>) LoaderApi.registry().registerSpawnEgg(
                modId, name, entityType, primaryColor, secondaryColor));
    }

    public static RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter) {
        requireRegistriesOpen(RegistryKind.PARTICLE, modId, name);
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) {
            return transaction.stage(RegistryKind.PARTICLE, modId, name,
                () -> checkedRegistrationHandle(RegistryKind.PARTICLE, modId, name,
                    LoaderApi.registry().registerParticle(modId, name, overrideLimiter)));
        }
        return checkedRegistrationHandle(RegistryKind.PARTICLE, modId, name,
            LoaderApi.registry().registerParticle(modId, name, overrideLimiter));
    }

    private static void requireRegistriesOpen(RegistryKind kind, String modId, String name) {
        validateKey(kind, modId, name);
        requireRegistriesOpen(kind.name().toLowerCase(java.util.Locale.ROOT), modId, name);
    }

    private static void validateKey(RegistryKind kind, String modId, String name) {
        java.util.Objects.requireNonNull(kind, "registryKind");
        MLuaResourceNameValidator.validate(modId);
        MLuaResourceNameValidator.validate(name);
    }

    private static void requireRegistriesOpen(String kind, String modId, String name) {
        if (LoaderApi.registry().isFrozen()) {
            throw new IllegalStateException("[MLua] Cannot register native " + kind
                + " '" + modId + ":" + name + "': Minecraft registries are frozen. Native content must be registered during startup in a .registry.lua script.");
        }
    }

    public static <T> RegistryHandle<T> get(RegistryKind kind, String modId, String name) {
        validateKey(kind, modId, name);
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) {
            RegistryHandle<T> staged = transaction.get(kind, modId, name);
            if (staged != null) return staged;
        }
        return checkedHandle(kind, modId, name, LoaderApi.registry().get(kind, modId, name));
    }

    private static <T> RegistryHandle<T> checkedHandle(RegistryKind kind, String modId, String name,
                                                        RegistryHandle<T> handle) {
        if (handle == null) return null;
        return () -> {
            T value;
            try {
                value = handle.get();
            } catch (RuntimeException failure) {
                throw unavailableHandle(kind, modId, name, failure);
            }
            if (value == null) throw unavailableHandle(kind, modId, name, null);
            return value;
        };
    }

    private static IllegalStateException unavailableHandle(RegistryKind kind, String modId, String name,
                                                            RuntimeException cause) {
        String message = "[MLua] Registry entry is not ready or could not be resolved: "
            + kind.name().toLowerCase(java.util.Locale.ROOT) + " '" + modId + ":" + name + "'.";
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private static <T> RegistryHandle<T> checkedRegistrationHandle(RegistryKind kind, String modId, String name,
                                                                     RegistryHandle<T> handle) {
        if (handle == null) {
            throw new IllegalStateException("[MLua] Loader registry returned no handle for "
                + kind.name().toLowerCase(java.util.Locale.ROOT) + " '" + modId + ":" + name + "'.");
        }
        return checkedHandle(kind, modId, name, handle);
    }

    public static Supplier<BlockEntityType<MLuaVisualBlockEntity>> visualBlockEntitySupplier(String modId) {
        Supplier<Object> supplier = LoaderApi.registry().visualBlockEntitySupplier(modId);
        return () -> (BlockEntityType<MLuaVisualBlockEntity>) supplier.get();
    }

    public static void registerVisualBlockEntityTypes() {
        requireRegistriesOpen("block entity type", "mlua", "visual_block_entity");
        LoaderApi.registry().registerVisualBlockEntityTypes();
    }

    public static void setItemVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
        LoaderApi.registry().setTexture(RegistryKind.ITEM, modId, name, texture.faces());
        LoaderApi.registry().setModel(RegistryKind.ITEM, modId, name, model);
    }

    public static void setBlockVisuals(String modId, String name, MLuaTextureDescriptor texture, String model) {
        LoaderApi.registry().setTexture(RegistryKind.BLOCK, modId, name, texture.faces());
        LoaderApi.registry().setModel(RegistryKind.BLOCK, modId, name, model);
    }

    public static MLuaTextureDescriptor getItemTexture(String namespace, String path) {
        return new MLuaTextureDescriptor(LoaderApi.registry().getTexture(RegistryKind.ITEM, namespace, path));
    }

    public static MLuaTextureDescriptor getBlockTexture(String namespace, String path) {
        return new MLuaTextureDescriptor(LoaderApi.registry().getTexture(RegistryKind.BLOCK, namespace, path));
    }

    public static String getItemModel(String namespace, String path) {
        return LoaderApi.registry().getModel(RegistryKind.ITEM, namespace, path);
    }

    public static String getBlockModel(String namespace, String path) {
        return LoaderApi.registry().getModel(RegistryKind.BLOCK, namespace, path);
    }

    public static void setItemCopy(String modId, String name, String copy) {
        LoaderApi.registry().setCopy(RegistryKind.ITEM, modId, name, copy);
    }

    public static void setBlockCopy(String modId, String name, String copy) {
        LoaderApi.registry().setCopy(RegistryKind.BLOCK, modId, name, copy);
    }

    public static String getItemCopy(String namespace, String path) {
        return LoaderApi.registry().getCopy(RegistryKind.ITEM, namespace, path);
    }

    public static String getBlockCopy(String namespace, String path) {
        return LoaderApi.registry().getCopy(RegistryKind.BLOCK, namespace, path);
    }

    public static void setItemCopyModel(String modId, String name, boolean copyModel) {
        LoaderApi.registry().setCopyModel(RegistryKind.ITEM, modId, name, copyModel);
    }

    public static void setBlockCopyModel(String modId, String name, boolean copyModel) {
        LoaderApi.registry().setCopyModel(RegistryKind.BLOCK, modId, name, copyModel);
    }

    public static boolean getItemCopyModel(String namespace, String path) {
        return LoaderApi.registry().getCopyModel(RegistryKind.ITEM, namespace, path);
    }

    public static boolean getBlockCopyModel(String namespace, String path) {
        return LoaderApi.registry().getCopyModel(RegistryKind.BLOCK, namespace, path);
    }

    public static void setItemDescription(String modId, String name, List<String> description) {
        LoaderApi.registry().setDescription(RegistryKind.ITEM, modId, name, description);
    }

    public static void setBlockDescription(String modId, String name, List<String> description) {
        LoaderApi.registry().setDescription(RegistryKind.BLOCK, modId, name, description);
    }

    public static List<String> getItemDescription(String namespace, String path) {
        return LoaderApi.registry().getDescription(RegistryKind.ITEM, namespace, path);
    }

    public static List<String> getBlockDescription(String namespace, String path) {
        return LoaderApi.registry().getDescription(RegistryKind.BLOCK, namespace, path);
    }

    public static void setEntityVisual(String modId, String name, String texture, String model, float shadowRadius) {
        LoaderApi.registry().setEntityVisual(modId, name, texture, model, shadowRadius);
    }

    public static MLuaEntityVisualDescriptor getEntityVisual(String key) {
        EntityVisualInfo info = LoaderApi.registry().getEntityVisual(key);
        return info == null ? null : new MLuaEntityVisualDescriptor(info.texture(), info.model(), info.shadowRadius());
    }

    public static boolean restoreEntityVisual(String modId, String name, EntityVisualInfo expectedCurrent,
                                              EntityVisualInfo previousVisual) {
        return LoaderApi.registry().restoreEntityVisual(modId, name, expectedCurrent, previousVisual);
    }

    public static void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition) {
        LoaderApi.registry().registerGeneratedAsset(kind, modId, name, definition);
    }

    public static boolean unregisterGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject expectedDefinition) {
        return LoaderApi.registry().unregisterGeneratedAsset(kind, modId, name, expectedDefinition);
    }

    public static boolean restoreGeneratedAsset(GeneratedAssetKind kind, String modId, String name,
                                                JsonObject expectedCurrent, JsonObject previousDefinition) {
        return LoaderApi.registry().restoreGeneratedAsset(kind, modId, name, expectedCurrent, previousDefinition);
    }

    public static Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind) {
        return LoaderApi.registry().getGeneratedAssets(kind);
    }

    public static void registerGeneratedDataResource(String owner, String resourceType, String id, JsonObject definition) {
        GeneratedDataResources.register(owner, resourceType, id, definition);
    }

    public static boolean unregisterGeneratedDataResource(String owner, String resourceType, String id) {
        return GeneratedDataResources.unregister(owner, resourceType, id);
    }

    public static boolean unregisterGeneratedDataResource(String owner, String resourceType, String id,
                                                          JsonObject expectedDefinition) {
        return GeneratedDataResources.unregister(owner, resourceType, id, expectedDefinition);
    }

    public static Map<String, JsonObject> getGeneratedDataResources() {
        return GeneratedDataResources.snapshot();
    }

    public static JsonObject getGeneratedDataResource(String resourceType, String id) {
        return GeneratedDataResources.get(resourceType, id);
    }

    public static boolean hasGeneratedDataResource(String resourceType, String id) {
        return GeneratedDataResources.contains(resourceType, id);
    }

    public static List<String> getGeneratedDataResourceIds(String resourceType, String namespace) {
        return GeneratedDataResources.ids(resourceType, namespace);
    }

    public static void registerGeneratedAssetResource(String owner, String path, JsonObject definition) {
        GeneratedAssetResources.register(owner, path, definition);
    }

    public static boolean unregisterGeneratedAssetResource(String owner, String path, JsonObject contribution) {
        return GeneratedAssetResources.unregister(owner, path, contribution);
    }

    public static Map<String, JsonObject> getGeneratedAssetResources() {
        return GeneratedAssetResources.snapshot();
    }

    public static boolean clearGeneratedAssetResources(String owner) {
        return GeneratedAssetResources.removeOwner(owner);
    }

    public static boolean clearGeneratedDataResources(String owner) {
        return GeneratedDataResources.removeOwner(owner);
    }

    public static void registerTag(RegistryKind kind, String modId, String name, boolean replace, JsonArray values) {
        LoaderApi.registry().registerTag(kind, modId, name, replace, values);
    }

    public static Set<String> getRegisteredIds(RegistryKind kind) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(LoaderApi.registry().getRegisteredIds(kind));
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) ids.addAll(transaction.registeredIds(kind));
        return Collections.unmodifiableSet(ids);
    }

    public static Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind kind) {
        LinkedHashMap<String, RegistryHandle<?>> handles = new LinkedHashMap<>(
            LoaderApi.registry().getRegisteredHandles(kind));
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null) handles.putAll(transaction.registeredHandles(kind));
        return Collections.unmodifiableMap(handles);
    }

    public static boolean isRegistered(RegistryKind kind, String namespace, String path) {
        RegistrationTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction != null && transaction.isRegistered(kind, namespace, path)) return true;
        return LoaderApi.registry().isRegistered(kind, namespace, path);
    }

    private static RegistryKind registryKey(RegistryKind kind) {
        return kind == RegistryKind.BLOCK_ITEM ? RegistryKind.ITEM : kind;
    }

    private record RegistrationKey(RegistryKind kind, String modId, String name) {
        private String id() { return modId + ":" + name; }
    }

    private static final class StagedRegistration<T> {
        private final RegistrationKey key;
        private final Supplier<RegistryHandle<T>> submit;
        private final StagedHandle<T> handle;

        private StagedRegistration(RegistrationKey key, Supplier<RegistryHandle<T>> submit) {
            this.key = key;
            this.submit = submit;
            this.handle = new StagedHandle<>(key.id());
        }
    }

    private static final class StagedHandle<T> implements RegistryHandle<T> {
        private final String id;
        private RegistryHandle<T> delegate;
        private Throwable failure;

        private StagedHandle(String id) { this.id = id; }

        private synchronized void resolve(RegistryHandle<T> resolved) {
            if (delegate != null || failure != null) {
                throw new IllegalStateException("[MLua] Staged registry handle already completed: " + id);
            }
            delegate = java.util.Objects.requireNonNull(resolved, "resolved");
        }

        private synchronized void fail(Throwable cause) {
            if (delegate == null && failure == null) failure = cause;
        }

        @Override public synchronized T get() {
            if (failure != null) throw new IllegalStateException("[MLua] Staged registry entry failed: " + id, failure);
            if (delegate == null) throw new IllegalStateException("[MLua] Registry entry is not committed yet: " + id);
            return delegate.get();
        }
    }

    /**
     * A script-scoped queue. Execution failures discard staged entries before submission; once commit starts,
     * native loader registries may be irreversible, so any partial submission is reported to the caller.
     */
    public static final class RegistrationTransaction implements AutoCloseable {
        private final Thread thread = Thread.currentThread();
        private final LinkedHashMap<RegistrationKey, StagedRegistration<?>> registrations = new LinkedHashMap<>();
        private boolean complete;

        private RegistrationTransaction() {
            if (ACTIVE_TRANSACTION.get() != null) {
                throw new IllegalStateException("[MLua] Registry registration transactions cannot be nested");
            }
            ACTIVE_TRANSACTION.set(this);
        }

        private <T> RegistryHandle<T> stage(RegistryKind kind, String modId, String name,
                Supplier<RegistryHandle<T>> submit) {
            requireActive();
            RegistrationKey key = new RegistrationKey(registryKey(kind), modId, name);
            if (registrations.containsKey(key)) {
                throw new IllegalArgumentException("[MLua] Duplicate staged native registration: " + key.id());
            }
            if (LoaderApi.registry().isRegistered(key.kind(), key.modId(), key.name())) {
                throw new IllegalArgumentException("[MLua] Native registry entry already exists: " + key.id());
            }
            StagedRegistration<T> staged = new StagedRegistration<>(key, submit);
            registrations.put(key, staged);
            return staged.handle;
        }

        @SuppressWarnings("unchecked")
        private <T> RegistryHandle<T> get(RegistryKind kind, String modId, String name) {
            StagedRegistration<?> staged = registrations.get(new RegistrationKey(registryKey(kind), modId, name));
            return staged == null ? null : (RegistryHandle<T>) staged.handle;
        }

        private boolean isRegistered(RegistryKind kind, String namespace, String path) {
            return registrations.containsKey(new RegistrationKey(registryKey(kind), namespace, path));
        }

        private Set<String> registeredIds(RegistryKind kind) {
            RegistryKind normalized = registryKey(kind);
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            registrations.keySet().stream().filter(key -> key.kind() == normalized)
                .map(RegistrationKey::id).forEach(ids::add);
            return Collections.unmodifiableSet(ids);
        }

        private Map<String, RegistryHandle<?>> registeredHandles(RegistryKind kind) {
            RegistryKind normalized = registryKey(kind);
            LinkedHashMap<String, RegistryHandle<?>> handles = new LinkedHashMap<>();
            registrations.values().stream().filter(staged -> staged.key.kind() == normalized)
                .forEach(staged -> handles.put(staged.key.id(), staged.handle));
            return Collections.unmodifiableMap(handles);
        }

        /** Validates duplicates before submitting entries to the loader in source order. */
        public void commit() {
            requireActive();
            List<String> submitted = new ArrayList<>();
            String[] inFlight = {null};
            try {
                if (LoaderApi.registry().isFrozen() && !registrations.isEmpty()) {
                    throw new IllegalStateException("[MLua] Cannot commit native registry registrations: Minecraft registries froze after the registry script staged its entries. Native content must be submitted before registries freeze.");
                }
                for (RegistrationKey key : registrations.keySet()) {
                    if (LoaderApi.registry().isRegistered(key.kind(), key.modId(), key.name())) {
                        throw new IllegalArgumentException("[MLua] Native registry entry already exists: " + key.id());
                    }
                }
                for (StagedRegistration<?> registration : registrations.values()) {
                    if (LoaderApi.registry().isFrozen()) {
                        throw new IllegalStateException("[MLua] Cannot submit native registry entry '"
                            + registration.key.id() + "': Minecraft registries froze during the script's registration transaction.");
                    }
                    inFlight[0] = registration.key.id();
                    submit(registration);
                    submitted.add(registration.key.id());
                    inFlight[0] = null;
                }
                complete(null);
            } catch (RuntimeException failure) {
                RuntimeException reported = submitted.isEmpty() && inFlight[0] == null
                    ? failure : partialCommitFailure(submitted, inFlight[0], failure);
                complete(reported);
                throw reported;
            } catch (Error failure) {
                complete(failure);
                if (!submitted.isEmpty() || inFlight[0] != null) {
                    failure.addSuppressed(partialCommitDetails(submitted, inFlight[0]));
                }
                throw failure;
            }
        }

        private static RuntimeException partialCommitFailure(List<String> submitted, String inFlight,
                                                              RuntimeException cause) {
            return new IllegalStateException(partialCommitDetails(submitted, inFlight).getMessage()
                + " Loader error: " + cause.getMessage(), cause);
        }

        private static IllegalStateException partialCommitDetails(List<String> submitted, String inFlight) {
            List<String> affected = new ArrayList<>(submitted);
            if (inFlight != null) affected.add(inFlight + " (loader acceptance unknown)");
            int shownCount = Math.min(affected.size(), 16);
            String listed = String.join(", ", affected.subList(0, shownCount));
            String remainder = affected.size() == shownCount ? ""
                : " (and " + (affected.size() - shownCount) + " more)";
            return new IllegalStateException("[MLua] Registry commit failed after the loader accepted "
                + submitted.size() + " entries" + (inFlight == null ? "" : " and while submitting another")
                + ": " + listed + remainder
                + ". Minecraft registry submissions cannot be rolled back safely; restart after fixing the failing registration.");
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static <T> void submit(StagedRegistration<T> registration) {
            registration.handle.resolve(registration.submit.get());
        }

        /** Discards uncommitted registrations and invalidates handles handed to the current script. */
        public void rollback() {
            if (complete) return;
            requireActive();
            complete(new IllegalStateException("registry script did not complete successfully"));
        }

        private void complete(Throwable failure) {
            if (complete) return;
            if (failure != null) {
                registrations.values().forEach(registration -> registration.handle.fail(failure));
            }
            complete = true;
            ACTIVE_TRANSACTION.remove();
        }

        private void requireActive() {
            if (complete || ACTIVE_TRANSACTION.get() != this) {
                throw new IllegalStateException("[MLua] Registry transaction is no longer active");
            }
            if (Thread.currentThread() != thread) {
                throw new IllegalStateException("[MLua] Registry transaction must finish on its creating thread");
            }
        }

        @Override public void close() { rollback(); }
    }
}
