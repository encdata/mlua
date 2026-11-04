package dev.mlua.loaderapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public interface RegistryBridge {
    static Map<String, JsonObject> snapshotGeneratedAssets(Map<String, JsonObject> assets) {
        Map<String, JsonObject> snapshot = new LinkedHashMap<>();
        assets.forEach((id, definition) -> snapshot.put(id, definition.deepCopy()));
        return Collections.unmodifiableMap(snapshot);
    }

    static RegistryKind normalizeVisualRegistry(RegistryKind registryKind) {
        if (registryKind == RegistryKind.BLOCK_ITEM) return RegistryKind.ITEM;
        if (registryKind == RegistryKind.ITEM || registryKind == RegistryKind.BLOCK) return registryKind;
        throw new IllegalArgumentException("Visual metadata is supported only for ITEM and BLOCK registries; got " + registryKind);
    }

    static GeneratedAssetKind requireTagKind(RegistryKind registryKind) {
        return switch (registryKind) {
            case ITEM -> GeneratedAssetKind.ITEM_TAG;
            case BLOCK -> GeneratedAssetKind.BLOCK_TAG;
            case ENTITY -> GeneratedAssetKind.ENTITY_TYPE_TAG;
            default -> throw new IllegalArgumentException("Unsupported tag registry: " + registryKind);
        };
    }

    <T> RegistryHandle<T> register(RegistryKind registryKind, String modId, String name, Supplier<T> supplier);
    <T> RegistryHandle<T> get(RegistryKind registryKind, String modId, String name);
    <T> RegistryHandle<T> registerEntity(String modId, String name, Supplier<T> supplier, Supplier<?> attributes);
    RegistryHandle<?> registerSpawnEgg(String modId, String name, RegistryHandle<?> entityType, int primaryColor, int secondaryColor);
    RegistryHandle<?> registerParticle(String modId, String name, boolean overrideLimiter);
    Supplier<Object> visualBlockEntitySupplier(String modId);
    void registerVisualBlockEntityTypes();
    void setTexture(RegistryKind registryKind, String modId, String name, Map<String, String> textures);
    Map<String, String> getTexture(RegistryKind registryKind, String namespace, String path);
    void setModel(RegistryKind registryKind, String modId, String name, String model);
    String getModel(RegistryKind registryKind, String namespace, String path);
    void setCopy(RegistryKind registryKind, String modId, String name, String copy);
    String getCopy(RegistryKind registryKind, String namespace, String path);
    void setCopyModel(RegistryKind registryKind, String modId, String name, boolean copyModel);
    boolean getCopyModel(RegistryKind registryKind, String namespace, String path);
    void setDescription(RegistryKind registryKind, String modId, String name, List<String> description);
    List<String> getDescription(RegistryKind registryKind, String namespace, String path);
    void setEntityVisual(String modId, String name, String texture, String model, float shadowRadius);
    EntityVisualInfo getEntityVisual(String key);
    default boolean restoreEntityVisual(String modId, String name, EntityVisualInfo expectedCurrent,
                                       EntityVisualInfo previousVisual) {
        return false;
    }
    void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition);
    default boolean unregisterGeneratedAsset(GeneratedAssetKind kind, String modId, String name,
                                             JsonObject expectedDefinition) {
        return false;
    }
    default boolean restoreGeneratedAsset(GeneratedAssetKind kind, String modId, String name,
                                          JsonObject expectedCurrent, JsonObject previousDefinition) {
        return false;
    }
    Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind);
    void registerTag(RegistryKind registryKind, String modId, String name, boolean replace, JsonArray values);
    Set<String> getRegisteredIds(RegistryKind registryKind);
    Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind registryKind);
    boolean isRegistered(RegistryKind registryKind, String namespace, String path);
    void freeze();
    /** Whether native Minecraft registry entries can still be submitted. */
    default boolean isFrozen() { return false; }
    Object createCreativeTabBuilder();
}
