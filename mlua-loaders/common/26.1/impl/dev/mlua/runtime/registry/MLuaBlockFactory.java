package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Dedicated 26.1 block-property adapter. */
public final class MLuaBlockFactory {
    private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
    private static final MLuaBlockProperties BLOCK_PROPERTIES = new MLuaBlockProperties();

    public static java.util.List<String> soundNames() {
        return MLuaBlockProperties.soundNames();
    }

    public static java.util.List<String> mapColorNames() {
        return MLuaBlockProperties.mapColorNames();
    }

    public static void registerType(String name, String description, TypeProvider provider) {
        registerType("mlua", name, description, provider);
    }

    public static void registerType(String owner, String name, String description, TypeProvider provider) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
        if ("default".equals(key)) throw new IllegalArgumentException("[MLua] Block type 'default' is reserved by MLua");
        if (provider == null) throw new IllegalArgumentException("[MLua] Block type provider is required");
        REGISTERED_TYPES.register(owner, key, description, provider, "Block");
    }

    public static boolean unregisterType(String owner, String name) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
        if ("default".equals(key)) return false;
        return REGISTERED_TYPES.remove(owner, key, "Block");
    }

    public static int unregisterTypes(String owner) {
        return REGISTERED_TYPES.removeOwner(owner);
    }

    public static Map<String, String> registeredTypes() {
        Map<String, String> types = new TreeMap<>();
        types.put("default", "A standard block with configurable Minecraft properties and scripted callbacks.");
        return REGISTERED_TYPES.descriptions(types);
    }

    public static void validateTypeDefinition(Map<String, Object> properties) {
        String name = selectedType(properties);
        if (name != null && !"default".equals(name) && !REGISTERED_TYPES.contains(name, "Block"))
            throw new IllegalArgumentException("[MLua] Unknown block type: " + name);
        if (properties == null) return;
        MLuaBlockProperties.validateCopyDefinition(properties);
        MLuaBlockProperties.validateBlockProperties(properties);
        TypeProvider provider = registeredType(properties);
        if (provider != null) provider.validateDefinition(MLuaDefinitionSnapshot.freeze(properties));
    }

    public Block create(Map<String, Object> properties, String registryName,
                        Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        TypeProvider custom = registeredType(properties);
        BlockBehaviour.Properties registered = makeRegisteredProperties(properties, registryName);
        if (custom != null) {
            Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
                properties, custom::validateDefinition);
            return custom.createNamed(definition, registered, registryName, blockEntityType);
        }
        return new Block(registered);
    }

    public Block createScripted(Map<String, Object> properties, String behaviorKey,
                                Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        TypeProvider custom = registeredType(properties);
        BlockBehaviour.Properties registered = makeRegisteredProperties(properties, behaviorKey);
        if (custom != null) {
            Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
                properties, custom::validateDefinition);
            return custom.createScripted(definition, registered, behaviorKey, blockEntityType);
        }
        return MLuaScriptedBlock.create(registered, behaviorKey, blockEntityType);
    }

    public BlockBehaviour.Properties makeRegisteredProperties(Map<String, Object> properties, String registryName) {
        BlockBehaviour.Properties result = makeProperties(properties);
        result.setId(ResourceKey.create(Registries.BLOCK, MLuaResourceLocations.parse(registryName)));
        return result;
    }

    public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
        return BLOCK_PROPERTIES.makeProperties(properties);
    }

    private static TypeProvider registeredType(Map<String, Object> properties) {
        String type = selectedType(properties);
        return type == null || "default".equals(type) ? null : REGISTERED_TYPES.get(type, "Block");
    }

    private static String selectedType(Map<String, Object> properties) {
        if (properties == null) return null;
        Object value = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
        if (value == null) return null;
        if (!(value instanceof String name) || name.isBlank()) throw new IllegalArgumentException("[MLua] Block type must be a non-empty string");
        return MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
    }


    public interface TypeProvider {
        default void validateDefinition(Map<String, Object> definition) { }
        Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties, String registryName,
                          Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
        Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties, String behaviorKey,
                             Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
    }

}
