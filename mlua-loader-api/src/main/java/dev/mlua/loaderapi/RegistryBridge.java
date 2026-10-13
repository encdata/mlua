package dev.mlua.loaderapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public interface RegistryBridge {
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
    void registerGeneratedAsset(GeneratedAssetKind kind, String modId, String name, JsonObject definition);
    Map<String, JsonObject> getGeneratedAssets(GeneratedAssetKind kind);
    void registerTag(RegistryKind registryKind, String modId, String name, boolean replace, JsonArray values);
    Set<String> getRegisteredIds(RegistryKind registryKind);
    Map<String, RegistryHandle<?>> getRegisteredHandles(RegistryKind registryKind);
    boolean isRegistered(RegistryKind registryKind, String namespace, String path);
    void freeze();
    Object createCreativeTabBuilder();
}
