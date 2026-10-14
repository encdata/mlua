package dev.mlua.loader.impl.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryBridge;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaNamedBlockItem;
import dev.mlua.runtime.registry.MLuaNamedSpawnEggItem;
import dev.mlua.runtime.registry.MLuaResourceNameValidator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;

public final class FabricRegistryBridge implements RegistryBridge {
    private final List<PendingRegistration> pending = new ArrayList<>();
    private final Map<RegistryKind, Map<String, RegistryHandle<?>>> registeredHandles = new EnumMap<>(RegistryKind.class);
    private final Map<RegistryKind, Map<String, Map<String, String>>> textures = new EnumMap<>(RegistryKind.class);
    private final Map<RegistryKind, Map<String, String>> models = new EnumMap<>(RegistryKind.class);
    private final Map<RegistryKind, Map<String, String>> copies = new EnumMap<>(RegistryKind.class);
    private final Map<RegistryKind, Map<String, Boolean>> copyModels = new EnumMap<>(RegistryKind.class);
    private final Map<RegistryKind, Map<String, List<String>>> descriptions = new EnumMap<>(RegistryKind.class);
    private final Map<GeneratedAssetKind, Map<String, JsonObject>> generatedAssets = new EnumMap<>(GeneratedAssetKind.class);
    private final Map<String, EntityVisualInfo> entityVisuals = new LinkedHashMap<>();
    private final Map<String, AtomicReference<BlockEntityType<MLuaVisualBlockEntity>>> visualBlockEntityTypes = new LinkedHashMap<>();
    private final Set<String> requestedVisualBlockEntityMods = new LinkedHashSet<>();
    private boolean frozen;

    public record PendingRegistration(RegistryKind kind, String modId, String name, Supplier<?> supplier, Supplier<?> attributes) {
        public String key() {
            return modId + ":" + name;
        }
    }

    @Override
    public <T> RegistryHandle<T> register(RegistryKind registryKind, String modId, String name, Supplier<T> supplier) {
        return registerPending(registryKind, modId, name, supplier, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryHandle<T> get(RegistryKind registryKind, String modId, String name) {
        return (RegistryHandle<T>) registeredHandles
            .getOrDefault(registryKey(registryKind), Map.of())
            .get(resourceId(modId, name));
    }

    @Override
    public void freeze() {
        frozen = true;
    }

    @Override
    public <T> RegistryHandle<T> registerEntity(String modId, String name, Supplier<T> supplier, Supplier<?> attributes) {
        return registerPending(RegistryKind.ENTITY, modId, name, supplier, attributes);
    }

    private <T> RegistryHandle<T> registerPending(RegistryKind kind, String modId, String name,
                                                  Supplier<T> supplier, Supplier<?> attributes) {
        Objects.requireNonNull(kind, "registryKind");
        MLuaResourceNameValidator.validate(modId);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("[MLua] Invalid registry name: " + name);
        MLuaResourceNameValidator.validate(name);
        if (kind != RegistryKind.BLOCK_ITEM) Objects.requireNonNull(supplier, "supplier");
        if (frozen) throw new IllegalStateException("[MLua] Registries are frozen");
        PendingRegistration registration = new PendingRegistration(kind, modId, name, supplier, attributes);
        String key = registration.key();
        Map<String, RegistryHandle<?>> byKind = registeredHandles.computeIfAbsent(
            registryKey(kind), ignored -> new LinkedHashMap<>());
        if (byKind.containsKey(key)) throw new IllegalArgumentException("[MLua] Duplicate registration: " + key);

        AtomicReference<RegistryHandle<?>> self = new AtomicReference<>();
        RegistryHandle<T> handle = () -> {
            RegistryHandle<?> resolved = byKind.get(key);
            if (resolved == null || resolved == self.get()) {
                throw new IllegalStateException("[MLua] Registry entry is not ready: " + key);
            }
            @SuppressWarnings("unchecked")
            RegistryHandle<T> cast = (RegistryHandle<T>) resolved;
            return cast.get();
        };
        self.set(handle);
        byKind.put(key, handle);
        pending.add(registration);
        return handle;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RegistryHandle<?> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor) {
        Supplier<Item> supplier = () -> new MLuaNamedSpawnEggItem((EntityType<? extends Mob>) entityType.get(), primaryColor, secondaryColor, new Item.Properties(), modId, name);
        RegistryHandle<?> handle = register(RegistryKind.ITEM, modId, name, supplier);
        setModel(RegistryKind.ITEM, modId, name, "minecraft:item/template_spawn_egg");
        return handle;
    }

    @Override
    public RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter) {
        return register(RegistryKind.PARTICLE, modId, name, () -> FabricParticleTypes.simple(overrideLimiter));
    }

    @Override
    public void setTexture(RegistryKind registryKind, String modId, String name, Map<String, String> values) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        putMetadata(textures, registryKind, modId, name,
            values == null || values.isEmpty() ? null : Map.copyOf(values));
    }

    @Override
    public Map<String, String> getTexture(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        Map<String, String> value = getByPathOrName(textures.getOrDefault(registryKind, Map.of()), namespace, path);
        return value == null ? Map.of() : value;
    }

    @Override
    public void setModel(RegistryKind registryKind, String modId, String name, String model) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        putMetadata(models, registryKind, modId, name, model == null || model.isBlank() ? null : model);
    }

    @Override
    public String getModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return getByPathOrName(models.getOrDefault(registryKind, Map.of()), namespace, path);
    }

    @Override
    public void setCopy(RegistryKind registryKind, String modId, String name, String copy) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        putMetadata(copies, registryKind, modId, name, copy == null || copy.isBlank() ? null : copy);
    }

    @Override
    public String getCopy(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return getByPathOrName(copies.getOrDefault(registryKind, Map.of()), namespace, path);
    }

    @Override
    public void setCopyModel(RegistryKind registryKind, String modId, String name, boolean copyModel) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        putMetadata(copyModels, registryKind, modId, name, copyModel ? Boolean.TRUE : null);
    }

    @Override
    public boolean getCopyModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return Boolean.TRUE.equals(getBooleanByPathOrName(copyModels.getOrDefault(registryKind, Map.of()), namespace, path));
    }

    @Override
    public Supplier<Object> visualBlockEntitySupplier(String modId) {
        requestedVisualBlockEntityMods.add(modId);
        AtomicReference<BlockEntityType<MLuaVisualBlockEntity>> reference = visualBlockEntityTypes.computeIfAbsent(modId, ignored -> new AtomicReference<>());
        return reference::get;
    }

    @Override
    public void registerVisualBlockEntityTypes() {
        pending.stream()
            .filter(reg -> reg.kind() == RegistryKind.BLOCK)
            .map(PendingRegistration::modId)
            .forEach(requestedVisualBlockEntityMods::add);
    }

    @Override
    public void setDescription(RegistryKind registryKind, String modId, String name, List<String> description) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        putMetadata(descriptions, registryKind, modId, name, description == null ? null : List.copyOf(description));
    }

    @Override
    public List<String> getDescription(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return getByPathOrName(descriptions.getOrDefault(registryKind, Map.of()), namespace, path);
    }

    @Override
    public void setEntityVisual(String modId, String name, String texture, String model, float shadowRadius) {
        entityVisuals.put(resourceId(modId, name), new EntityVisualInfo(texture, model, shadowRadius));
    }

    @Override
    public EntityVisualInfo getEntityVisual(String key) {
        return entityVisuals.get(key);
    }

    @Override
    public void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition) {
        Objects.requireNonNull(kind, "kind");
        if (kind == GeneratedAssetKind.ITEM_TAG || kind == GeneratedAssetKind.BLOCK_TAG
            || kind == GeneratedAssetKind.ENTITY_TYPE_TAG) {
            throw new IllegalArgumentException("[MLua] Use registerTag for tag assets");
        }
        String key = assetKey(modId, name);
        if (frozen) throw new IllegalStateException("[MLua] Registries are frozen");
        Objects.requireNonNull(definition, "definition");
        JsonObject previous = generatedAssets.computeIfAbsent(kind, ignored -> new LinkedHashMap<>())
            .putIfAbsent(key, definition.deepCopy());
        if (previous != null) throw new IllegalArgumentException("[MLua] Duplicate " + kind + " asset: " + key);
    }

    @Override
    public Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind) {
        return RegistryBridge.snapshotGeneratedAssets(generatedAssets.getOrDefault(kind, Map.of()));
    }

    @Override
    public void registerTag(RegistryKind registryKind, String modId, String name, boolean replace, JsonArray values) {
        Objects.requireNonNull(values, "values");
        GeneratedAssetKind tagKind = RegistryBridge.requireTagKind(registryKind);
        String key = assetKey(modId, name);
        if (frozen) throw new IllegalStateException("[MLua] Registries are frozen");
        JsonObject definition = new JsonObject();
        definition.addProperty("replace", replace);
        definition.add("values", values.deepCopy());
        mergeTagAsset(tagKind, key, definition);
    }

    @Override
    public Set<String> getRegisteredIds(RegistryKind registryKind) {
        return Set.copyOf(registeredHandles.getOrDefault(registryKey(registryKind), Map.of()).keySet());
    }

    @Override
    public Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind registryKind) {
        return Map.copyOf(registeredHandles.getOrDefault(registryKey(registryKind), Map.of()));
    }

    @Override
    public boolean isRegistered(RegistryKind registryKind, String namespace, String path) {
        return registeredHandles.getOrDefault(registryKey(registryKind), Map.of()).containsKey(namespace + ":" + path);
    }

    public void flush(Consumer<PendingRegistration> action) {
        List<PendingRegistration> copy = new ArrayList<>(pending);
        for (PendingRegistration reg : copy) {
            action.accept(reg);
            pending.remove(reg);
        }
    }

    public <T> void bindHandle(RegistryKind kind, String key, RegistryHandle<T> resolved) {
        Map<String, RegistryHandle<?>> byKind = registeredHandles.get(registryKey(kind));
        if (byKind == null || !byKind.containsKey(key)) {
            throw new IllegalStateException("[MLua] No pending registry entry for " + kind + " " + key);
        }
        byKind.put(key, Objects.requireNonNull(resolved, "resolved"));
    }

    @Override
    public Object createCreativeTabBuilder() {
        return net.minecraft.world.item.CreativeModeTab.builder(net.minecraft.world.item.CreativeModeTab.Row.TOP, 0);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void registerPendingVisualBlockEntityTypes() {
        for (String modId : requestedVisualBlockEntityMods) {
            AtomicReference<BlockEntityType<MLuaVisualBlockEntity>> reference = visualBlockEntityTypes.computeIfAbsent(modId, ignored -> new AtomicReference<>());
            if (reference.get() != null) {
                continue;
            }

            Block[] modBlocks = registeredHandles
                .getOrDefault(RegistryKind.BLOCK, Map.of())
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(modId + ":"))
                .map(Map.Entry::getValue)
                .map(RegistryHandle::get)
                .filter(Block.class::isInstance)
                .map(Block.class::cast)
                .toArray(Block[]::new);

            if (modBlocks.length == 0) {
                continue;
            }

            ResourceLocation id = ResourceLocation.of(modId + ":visual_block_entity", ':');
            BlockEntityType<MLuaVisualBlockEntity> type = BlockEntityType.Builder.of(MLuaVisualBlockEntity::new, modBlocks).build(null);
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
            reference.set(type);
        }
    }

    private <T> void putMetadata(Map<RegistryKind, Map<String, T>> target, RegistryKind registryKind, String modId, String name, T value) {
        String key = resourceId(modId, name);
        Map<String, T> values = target.computeIfAbsent(registryKind, ignored -> new LinkedHashMap<>());
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    private static String resourceId(String modId, String name) {
        MLuaResourceNameValidator.validate(modId);
        MLuaResourceNameValidator.validate(name);
        return modId + ":" + name;
    }

    private void mergeTagAsset(GeneratedAssetKind kind, String key, JsonObject definition) {
        Map<String, JsonObject> tags = generatedAssets.computeIfAbsent(kind, ignored -> new LinkedHashMap<>());
        JsonObject existing = tags.get(key);
        if (existing == null || definition.get("replace").getAsBoolean()) {
            tags.put(key, definition);
            return;
        }
        JsonArray existingValues = existing.getAsJsonArray("values");
        definition.getAsJsonArray("values").forEach(existingValues::add);
    }

    private static String assetKey(String modId, String name) {
        if (modId == null || modId.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("[MLua] Asset mod id and name must be non-empty");
        }
        MLuaResourceNameValidator.validate(modId);
        return MLuaResourceLocations.parse(name.contains(":") ? name : modId + ":" + name).toString();
    }

    private RegistryKind registryKey(RegistryKind kind) {
        Objects.requireNonNull(kind, "registryKind");
        return kind == RegistryKind.BLOCK_ITEM ? RegistryKind.ITEM : kind;
    }

    private <T> T getByPathOrName(Map<String, T> values, String namespace, String path) {
        String direct = namespace + ":" + path;
        T value = values.get(direct);
        if (value != null) {
            return value;
        }
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return values.get(namespace + ":" + name);
    }

    private Boolean getBooleanByPathOrName(Map<String, Boolean> values, String namespace, String path) {
        String direct = namespace + ":" + path;
        Boolean value = values.get(direct);
        if (value != null) {
            return value;
        }
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return values.get(namespace + ":" + name);
    }

}
