package dev.mlua.loader.impl.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryBridge;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.neoforge.MLuaNeoForgeRegistry;
import dev.mlua.runtime.registry.MLuaResourceNameValidator;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class NeoForgeRegistryBridge implements RegistryBridge {
    private final MLuaNeoForgeRegistry neoForgeRegistry = MLuaNeoForgeRegistry.get();

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryHandle<T> register(RegistryKind registryKind, String modId, String name, Supplier<T> supplier) {
        Objects.requireNonNull(registryKind, "registryKind");
        MLuaResourceNameValidator.validate(modId);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("[MLua] Invalid registry name: " + name);
        MLuaResourceNameValidator.validate(name);
        if (registryKind != RegistryKind.BLOCK_ITEM) Objects.requireNonNull(supplier, "supplier");
        return (RegistryHandle<T>) switch (registryKind) {
            case ITEM -> wrap(neoForgeRegistry.registerItem(modId, name, (Supplier) supplier));
            case BLOCK -> wrap(neoForgeRegistry.registerBlock(modId, name, (Supplier) supplier));
            case BLOCK_ITEM -> wrap(supplier == null
                ? neoForgeRegistry.registerBlockItem(modId, name)
                : neoForgeRegistry.registerItem(modId, name, (Supplier<Item>) () -> {
                    Object item = supplier.get();
                    if (!(item instanceof BlockItem blockItem)) {
                        throw new IllegalStateException("[MLua] Block item for '" + modId + ":" + name
                            + "' must be a BlockItem");
                    }
                    return blockItem;
                }));
            case ENTITY -> wrap(neoForgeRegistry.registerEntity(modId, name, (Supplier) supplier, null));
            case PARTICLE -> wrap(neoForgeRegistry.registerParticle(modId, name, (Supplier) supplier));
            case POTION -> wrap(neoForgeRegistry.registerPotion(modId, name, (Supplier) supplier));
            case MOB_EFFECT -> wrap(neoForgeRegistry.registerMobEffect(modId, name, (Supplier) supplier));
            case CREATIVE_TAB -> wrap(neoForgeRegistry.registerCreativeTab(modId, name, (Supplier) supplier));
        };
    }

    @Override
    public <T> RegistryHandle<T> get(RegistryKind registryKind, String modId, String name) {
        return switch (registryKind) {
            case ITEM, BLOCK_ITEM -> (RegistryHandle<T>) wrap(neoForgeRegistry.getItem(modId, name));
            case BLOCK -> (RegistryHandle<T>) wrap(neoForgeRegistry.getBlock(modId, name));
            case ENTITY -> (RegistryHandle<T>) wrap(neoForgeRegistry.getEntity(modId, name));
            case PARTICLE -> (RegistryHandle<T>) wrap(neoForgeRegistry.getParticle(modId, name));
            case POTION -> (RegistryHandle<T>) wrap(neoForgeRegistry.getPotion(modId, name));
            case MOB_EFFECT -> (RegistryHandle<T>) wrap(neoForgeRegistry.getMobEffect(modId, name));
            case CREATIVE_TAB -> (RegistryHandle<T>) wrap(neoForgeRegistry.getCreativeTab(modId, name));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryHandle<T> registerEntity(String modId, String name, Supplier<T> supplier, Supplier<?> attributes) {
        Objects.requireNonNull(supplier, "supplier");
        Supplier<AttributeSupplier> checkedAttributes = attributes == null ? null : () -> {
            Object value = attributes.get();
            if (!(value instanceof AttributeSupplier attributeSupplier)) {
                throw new IllegalStateException("[MLua] Entity attributes for '" + modId + ":" + name
                    + "' must return an AttributeSupplier");
            }
            return attributeSupplier;
        };
        return (RegistryHandle<T>) wrap(neoForgeRegistry.registerEntity(modId, name, (Supplier) supplier, checkedAttributes));
    }

    @Override
    public RegistryHandle<?> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor) {
        return wrap(neoForgeRegistry.registerSpawnEgg(modId, name, () -> (EntityType<?>) entityType.get(), primaryColor, secondaryColor));
    }

    @Override
    public RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter) {
        return wrap(neoForgeRegistry.registerParticle(modId, name, overrideLimiter));
    }

    @Override
    public Supplier<Object> visualBlockEntitySupplier(String modId) {
        return () -> neoForgeRegistry.visualBlockEntitySupplier(modId).get();
    }

    @Override
    public void registerVisualBlockEntityTypes() {
        neoForgeRegistry.registerVisualBlockEntityTypes();
    }

    @Override
    public void setTexture(RegistryKind registryKind, String modId, String name, Map<String, String> textures) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        MLuaTextureDescriptor texture = new MLuaTextureDescriptor(textures == null ? Map.of() : textures);
        switch (registryKind) {
            case ITEM -> neoForgeRegistry.setItemTexture(modId, name, texture);
            case BLOCK -> neoForgeRegistry.setBlockTexture(modId, name, texture);
        }
    }

    @Override
    public Map<String, String> getTexture(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> neoForgeRegistry.getItemTexture(namespace, path).faces();
            case BLOCK -> neoForgeRegistry.getBlockTexture(namespace, path).faces();
            default -> Map.of();
        };
    }

    @Override
    public void setModel(RegistryKind registryKind, String modId, String name, String model) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> neoForgeRegistry.setItemModel(modId, name, model);
            case BLOCK -> neoForgeRegistry.setBlockModel(modId, name, model);
        }
    }

    @Override
    public String getModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> neoForgeRegistry.getItemModel(namespace, path);
            case BLOCK -> neoForgeRegistry.getBlockModel(namespace, path);
            default -> null;
        };
    }

    @Override
    public void setCopy(RegistryKind registryKind, String modId, String name, String copy) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> neoForgeRegistry.setItemCopy(modId, name, copy);
            case BLOCK -> neoForgeRegistry.setBlockCopy(modId, name, copy);
        }
    }

    @Override
    public String getCopy(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> getByPathOrName(neoForgeRegistry.getItemCopies(), namespace, path);
            case BLOCK -> getByPathOrName(neoForgeRegistry.getBlockCopies(), namespace, path);
            default -> null;
        };
    }

    @Override
    public void setCopyModel(RegistryKind registryKind, String modId, String name, boolean copyModel) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> neoForgeRegistry.setItemCopyModel(modId, name, copyModel);
            case BLOCK -> neoForgeRegistry.setBlockCopyModel(modId, name, copyModel);
        }
    }

    @Override
    public boolean getCopyModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> hasByPathOrName(neoForgeRegistry.getItemCopyModels(), namespace, path);
            case BLOCK -> hasByPathOrName(neoForgeRegistry.getBlockCopyModels(), namespace, path);
            default -> false;
        };
    }

    @Override
    public void setDescription(RegistryKind registryKind, String modId, String name, List<String> description) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> neoForgeRegistry.setItemDescription(modId, name, description);
            case BLOCK -> neoForgeRegistry.setBlockDescription(modId, name, description);
        }
    }

    @Override
    public List<String> getDescription(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> neoForgeRegistry.getItemDescription(namespace, path);
            case BLOCK -> neoForgeRegistry.getBlockDescription(namespace, path);
            default -> null;
        };
    }

    @Override
    public void setEntityVisual(String modId, String name, String texture, String model, float shadowRadius) {
        neoForgeRegistry.setEntityVisuals(modId, name, texture, model, shadowRadius);
    }

    @Override
    public EntityVisualInfo getEntityVisual(String key) {
        var visual = neoForgeRegistry.getEntityVisual(key);
        return visual == null ? null : new EntityVisualInfo(visual.texture(), visual.model(), visual.shadowRadius());
    }

    @Override
    public void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition) {
        switch (kind) {
            case RECIPE -> neoForgeRegistry.registerRecipe(modId, name, definition);
            case LOOT_TABLE -> neoForgeRegistry.registerLootTable(modId, name, definition);
            case BLOCKSTATE -> neoForgeRegistry.registerBlockState(modId, name, definition);
            case ITEM_TAG, BLOCK_TAG, ENTITY_TYPE_TAG -> throw new IllegalArgumentException("Use registerTag for tag assets.");
        }
    }

    @Override
    public boolean unregisterGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject expectedDefinition) {
        if (kind == GeneratedAssetKind.ITEM_TAG || kind == GeneratedAssetKind.BLOCK_TAG
            || kind == GeneratedAssetKind.ENTITY_TYPE_TAG) {
            throw new IllegalArgumentException("Use tag rollback for tag assets.");
        }
        return neoForgeRegistry.unregisterGeneratedAsset(kind, modId, name, expectedDefinition);
    }

    @Override
    public Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind) {
        Map<String, JsonObject> assets = switch (kind) {
            case RECIPE -> neoForgeRegistry.getRecipes();
            case LOOT_TABLE -> neoForgeRegistry.getLootTables();
            case BLOCKSTATE -> neoForgeRegistry.getBlockStates();
            case ITEM_TAG -> neoForgeRegistry.getItemTags();
            case BLOCK_TAG -> neoForgeRegistry.getBlockTags();
            case ENTITY_TYPE_TAG -> neoForgeRegistry.getEntityTypeTags();
        };
        return RegistryBridge.snapshotGeneratedAssets(assets);
    }

    @Override
    public void registerTag(RegistryKind registryKind, String modId, String name, boolean replace, JsonArray values) {
        Objects.requireNonNull(values, "values");
        JsonArray snapshot = values.deepCopy();
        switch (RegistryBridge.requireTagKind(registryKind)) {
            case ITEM_TAG -> neoForgeRegistry.registerItemTag(modId, name, replace, snapshot);
            case BLOCK_TAG -> neoForgeRegistry.registerBlockTag(modId, name, replace, snapshot);
            case ENTITY_TYPE_TAG -> neoForgeRegistry.registerEntityTypeTag(modId, name, replace, snapshot);
            default -> throw new IllegalStateException("Unsupported generated tag kind");
        }
    }

    @Override
    public Set<String> getRegisteredIds(RegistryKind registryKind) {
        return Set.copyOf(switch (registryKind) {
            case ITEM, BLOCK_ITEM -> neoForgeRegistry.getRegisteredItems().keySet();
            case BLOCK -> neoForgeRegistry.getRegisteredBlocks().keySet();
            case ENTITY -> neoForgeRegistry.getEntities().keySet();
            case PARTICLE -> neoForgeRegistry.getParticles().keySet();
            case POTION -> neoForgeRegistry.getPotions().keySet();
            case MOB_EFFECT -> neoForgeRegistry.getMobEffects().keySet();
            case CREATIVE_TAB -> neoForgeRegistry.getCreativeTabs().keySet();
        });
    }

    @Override
    public Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind registryKind) {
        Map<String, RegistryHandle<?>> handles = switch (registryKind) {
            case ITEM, BLOCK_ITEM -> neoForgeRegistry.getRegisteredItems().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case BLOCK -> neoForgeRegistry.getRegisteredBlocks().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case ENTITY -> neoForgeRegistry.getEntities().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case PARTICLE -> neoForgeRegistry.getParticles().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case POTION -> neoForgeRegistry.getPotions().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case MOB_EFFECT -> neoForgeRegistry.getMobEffects().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case CREATIVE_TAB -> neoForgeRegistry.getCreativeTabs().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
        };
        return Map.copyOf(handles);
    }

    @Override
    public boolean isRegistered(RegistryKind registryKind, String namespace, String path) {
        return switch (registryKind) {
            case ITEM, BLOCK_ITEM -> neoForgeRegistry.isRegisteredItem(namespace, path);
            case BLOCK -> neoForgeRegistry.isRegisteredBlock(namespace, path);
            case ENTITY -> neoForgeRegistry.getEntities().containsKey(namespace + ":" + path);
            case PARTICLE -> neoForgeRegistry.getParticles().containsKey(namespace + ":" + path);
            case POTION -> neoForgeRegistry.getPotions().containsKey(namespace + ":" + path);
            case MOB_EFFECT -> neoForgeRegistry.getMobEffects().containsKey(namespace + ":" + path);
            case CREATIVE_TAB -> neoForgeRegistry.getCreativeTabs().containsKey(namespace + ":" + path);
        };
    }

    @Override
    public void freeze() {
        neoForgeRegistry.freeze();
    }

    private String getByPathOrName(Map<String, String> values, String namespace, String path) {
        String direct = namespace + ":" + path;
        String value = values.get(direct);
        if (value != null) {
            return value;
        }
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return values.get(namespace + ":" + name);
    }

    private boolean hasByPathOrName(Map<String, Boolean> values, String namespace, String path) {
        String direct = namespace + ":" + path;
        if (Boolean.TRUE.equals(values.get(direct))) {
            return true;
        }
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return Boolean.TRUE.equals(values.get(namespace + ":" + name));
    }

    @Override
    public Object createCreativeTabBuilder() {
        return net.minecraft.world.item.CreativeModeTab.builder();
    }

    private RegistryHandle<?> wrap(Supplier<?> obj) {
        return obj == null ? null : obj::get;
    }
}
