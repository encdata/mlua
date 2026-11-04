package dev.mlua.runtime.registry;

import dev.mlua.loaderapi.EntityVisualInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns generated item, block, and entity visual metadata for Forge registrations. */
final class MLuaForgeVisualMetadata {
    private final Map<String, MLuaTextureDescriptor> itemTextures = new HashMap<>();
    private final Map<String, MLuaTextureDescriptor> blockTextures = new HashMap<>();
    private final Map<String, String> itemModels = new HashMap<>();
    private final Map<String, String> blockModels = new HashMap<>();
    private final Map<String, String> itemCopies = new HashMap<>();
    private final Map<String, String> blockCopies = new HashMap<>();
    private final Map<String, Boolean> itemCopyModels = new HashMap<>();
    private final Map<String, Boolean> blockCopyModels = new HashMap<>();
    private final Map<String, List<String>> itemDescriptions = new HashMap<>();
    private final Map<String, List<String>> blockDescriptions = new HashMap<>();
    private final Map<String, MLuaEntityVisualDescriptor> entityVisuals = new HashMap<>();

    void setItemTexture(String id, MLuaTextureDescriptor texture) {
        set(itemTextures, id, texture == null || texture.isEmpty() ? null : texture);
    }

    void setItemModel(String id, String model) { set(itemModels, id, model == null || model.isBlank() ? null : model); }
    void setItemCopy(String id, String copy) { set(itemCopies, id, copy == null || copy.isBlank() ? null : copy); }
    void setItemCopyModel(String id, boolean copy) { setFlag(itemCopyModels, id, copy); }

    void setBlockTexture(String id, MLuaTextureDescriptor texture) {
        set(blockTextures, id, texture == null || texture.isEmpty() ? null : texture);
    }

    void setBlockModel(String id, String model) { set(blockModels, id, model == null || model.isBlank() ? null : model); }
    void setBlockCopy(String id, String copy) { set(blockCopies, id, copy == null || copy.isBlank() ? null : copy); }
    void setBlockCopyModel(String id, boolean copy) { setFlag(blockCopyModels, id, copy); }

    void setItemDescription(String id, List<String> description) {
        set(itemDescriptions, id, description == null ? null : List.copyOf(description));
    }

    List<String> itemDescription(String namespace, String path) {
        return get(itemDescriptions, namespace, path, null);
    }

    void setBlockDescription(String id, List<String> description) {
        set(blockDescriptions, id, description == null ? null : List.copyOf(description));
    }

    List<String> blockDescription(String namespace, String path) {
        return get(blockDescriptions, namespace, path, null);
    }

    MLuaTextureDescriptor itemTexture(String namespace, String path) {
        return get(itemTextures, namespace, path, MLuaTextureDescriptor.empty());
    }

    MLuaTextureDescriptor blockTexture(String namespace, String path) {
        return get(blockTextures, namespace, path, MLuaTextureDescriptor.empty());
    }

    String itemModel(String namespace, String path) { return get(itemModels, namespace, path, null); }
    String blockModel(String namespace, String path) { return get(blockModels, namespace, path, null); }

    Map<String, String> itemModels() { return itemModels; }
    Map<String, String> blockModels() { return blockModels; }
    Map<String, String> itemCopies() { return itemCopies; }
    Map<String, String> blockCopies() { return blockCopies; }
    Map<String, Boolean> itemCopyModels() { return itemCopyModels; }
    Map<String, Boolean> blockCopyModels() { return blockCopyModels; }

    void setEntityVisual(String id, MLuaEntityVisualDescriptor descriptor) { entityVisuals.put(id, descriptor); }
    MLuaEntityVisualDescriptor entityVisual(String id) { return entityVisuals.get(id); }

    boolean restoreEntityVisual(String id, EntityVisualInfo expected, EntityVisualInfo previous) {
        MLuaEntityVisualDescriptor current = entityVisuals.get(id);
        if (current == null || expected == null
                || !java.util.Objects.equals(current.texture(), expected.texture())
                || !java.util.Objects.equals(current.model(), expected.model())
                || Float.compare(current.shadowRadius(), expected.shadowRadius()) != 0) return false;
        if (previous == null) return entityVisuals.remove(id, current);
        entityVisuals.put(id, new MLuaEntityVisualDescriptor(
                previous.texture(), previous.model(), previous.shadowRadius()));
        return true;
    }

    private static <T> void set(Map<String, T> values, String id, T value) {
        if (value == null) values.remove(id);
        else values.put(id, value);
    }

    private static void setFlag(Map<String, Boolean> values, String id, boolean enabled) {
        if (enabled) values.put(id, true);
        else values.remove(id);
    }

    private static <T> T get(Map<String, T> values, String namespace, String path, T fallback) {
        String direct = namespace + ":" + path;
        T value = values.get(direct);
        if (value != null) return value;
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        value = values.get(namespace + ":" + name);
        return value == null ? fallback : value;
    }
}
