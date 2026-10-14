package dev.mlua.loader.impl.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryBridge;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaForgeRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.world.entity.EntityType;

public class ForgeRegistryBridge implements RegistryBridge {
    private final MLuaForgeRegistry forgeRegistry = MLuaForgeRegistry.get();

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryHandle<T> register(RegistryKind registryKind, String modId, String name, Supplier<T> supplier) {
        return (RegistryHandle<T>) switch (registryKind) {
            case ITEM -> wrap(forgeRegistry.registerItem(modId, name, (Supplier) supplier));
            case BLOCK -> wrap(forgeRegistry.registerBlock(modId, name, (Supplier) supplier));
            case BLOCK_ITEM -> wrap(forgeRegistry.registerBlockItem(modId, name));
            case ENTITY -> wrap(forgeRegistry.registerEntity(modId, name, (Supplier) supplier, () -> null));
            case PARTICLE -> wrap(forgeRegistry.registerParticle(modId, name, false));
            case POTION -> wrap(forgeRegistry.registerPotion(modId, name, (Supplier) supplier));
            case MOB_EFFECT -> wrap(forgeRegistry.registerMobEffect(modId, name, (Supplier) supplier));
            case CREATIVE_TAB -> wrap(forgeRegistry.registerCreativeTab(modId, name, (Supplier) supplier));
        };
    }

    @Override
    public <T> RegistryHandle<T> get(RegistryKind registryKind, String modId, String name) {
        return switch (registryKind) {
            case ITEM, BLOCK_ITEM -> (RegistryHandle<T>) wrap(forgeRegistry.getItem(modId, name));
            case BLOCK -> (RegistryHandle<T>) wrap(forgeRegistry.getBlock(modId, name));
            case ENTITY -> (RegistryHandle<T>) wrap(forgeRegistry.getEntity(modId, name));
            case PARTICLE -> (RegistryHandle<T>) wrap(forgeRegistry.getParticle(modId, name));
            case POTION -> (RegistryHandle<T>) wrap(forgeRegistry.getPotion(modId, name));
            case MOB_EFFECT -> (RegistryHandle<T>) wrap(forgeRegistry.getMobEffect(modId, name));
            case CREATIVE_TAB -> (RegistryHandle<T>) wrap(forgeRegistry.getCreativeTab(modId, name));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryHandle<T> registerEntity(String modId, String name, Supplier<T> supplier, Supplier<?> attributes) {
        return (RegistryHandle<T>) wrap(forgeRegistry.registerEntity(modId, name, (Supplier) supplier, (Supplier) attributes));
    }

    @Override
    public RegistryHandle<?> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor) {
        return wrap(forgeRegistry.registerSpawnEgg(modId, name, () -> (EntityType<?>) entityType.get(), primaryColor, secondaryColor));
    }

    @Override
    public RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter) {
        return wrap(forgeRegistry.registerParticle(modId, name, overrideLimiter));
    }

    @Override
    public Supplier<Object> visualBlockEntitySupplier(String modId) {
        return () -> forgeRegistry.visualBlockEntitySupplier(modId).get();
    }

    @Override
    public void registerVisualBlockEntityTypes() {
        forgeRegistry.registerVisualBlockEntityTypes();
    }

    @Override
    public void setTexture(RegistryKind registryKind, String modId, String name, Map<String, String> textures) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        MLuaTextureDescriptor texture = new MLuaTextureDescriptor(textures == null ? Map.of() : textures);
        switch (registryKind) {
            case ITEM -> forgeRegistry.setItemVisuals(modId, name, texture, null);
            case BLOCK -> forgeRegistry.setBlockVisuals(modId, name, texture, null);
        }
    }

    @Override
    public Map<String, String> getTexture(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> forgeRegistry.getItemTexture(namespace, path).faces();
            case BLOCK -> forgeRegistry.getBlockTexture(namespace, path).faces();
            default -> Map.of();
        };
    }

    @Override
    public void setModel(RegistryKind registryKind, String modId, String name, String model) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> forgeRegistry.setItemVisuals(modId, name, MLuaTextureDescriptor.empty(), model);
            case BLOCK -> forgeRegistry.setBlockVisuals(modId, name, MLuaTextureDescriptor.empty(), model);
        }
    }

    @Override
    public String getModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> forgeRegistry.getItemModel(namespace, path);
            case BLOCK -> forgeRegistry.getBlockModel(namespace, path);
            default -> null;
        };
    }

    @Override
    public void setCopy(RegistryKind registryKind, String modId, String name, String copy) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> forgeRegistry.setItemCopy(modId, name, copy);
            case BLOCK -> forgeRegistry.setBlockCopy(modId, name, copy);
        }
    }

    @Override
    public String getCopy(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> getByPathOrName(forgeRegistry.getItemCopies(), namespace, path);
            case BLOCK -> getByPathOrName(forgeRegistry.getBlockCopies(), namespace, path);
            default -> null;
        };
    }

    @Override
    public void setCopyModel(RegistryKind registryKind, String modId, String name, boolean copyModel) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> forgeRegistry.setItemCopyModel(modId, name, copyModel);
            case BLOCK -> forgeRegistry.setBlockCopyModel(modId, name, copyModel);
        }
    }

    @Override
    public boolean getCopyModel(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> hasByPathOrName(forgeRegistry.getItemCopyModels(), namespace, path);
            case BLOCK -> hasByPathOrName(forgeRegistry.getBlockCopyModels(), namespace, path);
            default -> false;
        };
    }

    @Override
    public void setDescription(RegistryKind registryKind, String modId, String name, List<String> description) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        switch (registryKind) {
            case ITEM -> forgeRegistry.setItemDescription(modId, name, description);
            case BLOCK -> forgeRegistry.setBlockDescription(modId, name, description);
        }
    }

    @Override
    public List<String> getDescription(RegistryKind registryKind, String namespace, String path) {
        registryKind = RegistryBridge.normalizeVisualRegistry(registryKind);
        return switch (registryKind) {
            case ITEM -> forgeRegistry.getItemDescription(namespace, path);
            case BLOCK -> forgeRegistry.getBlockDescription(namespace, path);
            default -> null;
        };
    }

    @Override
    public void setEntityVisual(String modId, String name, String texture, String model, float shadowRadius) {
        forgeRegistry.setEntityVisuals(modId, name, texture, model, shadowRadius);
    }

    @Override
    public EntityVisualInfo getEntityVisual(String key) {
        var visual = forgeRegistry.getEntityVisual(key);
        return visual == null ? null : new EntityVisualInfo(visual.texture(), visual.model(), visual.shadowRadius());
    }

    @Override
    public void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition) {
        switch (kind) {
            case RECIPE -> forgeRegistry.registerRecipe(modId, name, definition);
            case LOOT_TABLE -> forgeRegistry.registerLootTable(modId, name, definition);
            case BLOCKSTATE -> forgeRegistry.registerBlockState(modId, name, definition);
            case ITEM_TAG, BLOCK_TAG, ENTITY_TYPE_TAG -> throw new IllegalArgumentException("Use registerTag for tag assets.");
        }
    }

    @Override
    public Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind) {
        return switch (kind) {
            case RECIPE -> forgeRegistry.getRecipes();
            case LOOT_TABLE -> forgeRegistry.getLootTables();
            case BLOCKSTATE -> forgeRegistry.getBlockStates();
            case ITEM_TAG -> forgeRegistry.getItemTags();
            case BLOCK_TAG -> forgeRegistry.getBlockTags();
            case ENTITY_TYPE_TAG -> forgeRegistry.getEntityTypeTags();
        };
    }

    @Override
    public void registerTag(RegistryKind registryKind, String modId, String name, boolean replace, JsonArray values) {
        switch (registryKind) {
            case ITEM -> forgeRegistry.registerItemTag(modId, name, replace, values);
            case BLOCK -> forgeRegistry.registerBlockTag(modId, name, replace, values);
            case ENTITY -> forgeRegistry.registerEntityTypeTag(modId, name, replace, values);
        }
    }

    @Override
    public Set<String> getRegisteredIds(RegistryKind registryKind) {
        return Set.copyOf(switch (registryKind) {
            case ITEM, BLOCK_ITEM -> forgeRegistry.getRegisteredItems().keySet();
            case BLOCK -> forgeRegistry.getRegisteredBlocks().keySet();
            case ENTITY -> forgeRegistry.getEntities().keySet();
            case PARTICLE -> forgeRegistry.getParticles().keySet();
            case POTION -> forgeRegistry.getPotions().keySet();
            case MOB_EFFECT -> forgeRegistry.getMobEffects().keySet();
            case CREATIVE_TAB -> forgeRegistry.getCreativeTabs().keySet();
        });
    }

    @Override
    public Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind registryKind) {
        return switch (registryKind) {
            case ITEM, BLOCK_ITEM -> forgeRegistry.getRegisteredItems().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case BLOCK -> forgeRegistry.getRegisteredBlocks().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case ENTITY -> forgeRegistry.getEntities().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case PARTICLE -> forgeRegistry.getParticles().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case POTION -> forgeRegistry.getPotions().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case MOB_EFFECT -> forgeRegistry.getMobEffects().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
            case CREATIVE_TAB -> forgeRegistry.getCreativeTabs().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> wrap(entry.getValue())));
        };
    }

    @Override
    public boolean isRegistered(RegistryKind registryKind, String namespace, String path) {
        return switch (registryKind) {
            case ITEM, BLOCK_ITEM -> forgeRegistry.isRegisteredItem(namespace, path);
            case BLOCK -> forgeRegistry.isRegisteredBlock(namespace, path);
            case ENTITY -> forgeRegistry.getEntities().containsKey(namespace + ":" + path);
            case PARTICLE -> forgeRegistry.getParticles().containsKey(namespace + ":" + path);
            case POTION -> forgeRegistry.getPotions().containsKey(namespace + ":" + path);
            case MOB_EFFECT -> forgeRegistry.getMobEffects().containsKey(namespace + ":" + path);
            case CREATIVE_TAB -> forgeRegistry.getCreativeTabs().containsKey(namespace + ":" + path);
        };
    }

    @Override
    public void freeze() {
        forgeRegistry.freeze();
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

    private RegistryHandle<?> wrap(net.minecraftforge.registries.RegistryObject<?> obj) {
        return obj == null ? null : obj::get;
    }
}
