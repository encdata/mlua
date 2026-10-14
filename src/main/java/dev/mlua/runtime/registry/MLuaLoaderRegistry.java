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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MLuaLoaderRegistry {
    private MLuaLoaderRegistry() {
    }

    public static <T> RegistryHandle<T> register(RegistryKind kind, String modId, String name, Supplier<T> supplier) {
        return LoaderApi.registry().register(kind, modId, name, supplier);
    }

    public static RegistryHandle<EntityType<?>> registerEntity(String modId, String name, Supplier<EntityType<?>> supplier, Supplier<AttributeSupplier> attributes) {
        return (RegistryHandle<EntityType<?>>) LoaderApi.registry().registerEntity(modId, name, supplier, attributes);
    }

    public static RegistryHandle<Item> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor) {
        return (RegistryHandle<Item>) LoaderApi.registry().registerSpawnEgg(modId, name, entityType, primaryColor, secondaryColor);
    }

    public static RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter) {
        return LoaderApi.registry().registerParticle(modId, name, overrideLimiter);
    }

    public static <T> RegistryHandle<T> get(RegistryKind kind, String modId, String name) {
        return LoaderApi.registry().get(kind, modId, name);
    }

    public static Supplier<BlockEntityType<MLuaVisualBlockEntity>> visualBlockEntitySupplier(String modId) {
        Supplier<Object> supplier = LoaderApi.registry().visualBlockEntitySupplier(modId);
        return () -> (BlockEntityType<MLuaVisualBlockEntity>) supplier.get();
    }

    public static void registerVisualBlockEntityTypes() {
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

    public static void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition) {
        LoaderApi.registry().registerGeneratedAsset(kind, modId, name, definition);
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

    public static Map<String, JsonObject> getGeneratedDataResources() {
        return GeneratedDataResources.snapshot();
    }

    public static void registerGeneratedAssetResource(String owner, String path, JsonObject definition) {
        GeneratedAssetResources.register(owner, path, definition);
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
        return LoaderApi.registry().getRegisteredIds(kind);
    }

    public static Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind kind) {
        return LoaderApi.registry().getRegisteredHandles(kind);
    }

    public static boolean isRegistered(RegistryKind kind, String namespace, String path) {
        return LoaderApi.registry().isRegistered(kind, namespace, path);
    }
}
