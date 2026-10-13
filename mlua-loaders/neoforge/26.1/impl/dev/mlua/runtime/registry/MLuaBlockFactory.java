package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Dedicated 26.1 block-property adapter. */
public final class MLuaBlockFactory {
    private static final ConcurrentMap<String, RegisteredType> REGISTERED_TYPES = new ConcurrentHashMap<>();

    public static void registerType(String name, String description, TypeProvider provider) {
        String key = normalizeTypeName(name);
        if ("default".equals(key)) throw new IllegalArgumentException("[MLua] Block type 'default' is reserved by MLua");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("[MLua] Block type description must be non-empty");
        if (provider == null) throw new IllegalArgumentException("[MLua] Block type provider is required");
        if (REGISTERED_TYPES.putIfAbsent(key, new RegisteredType(description.trim(), provider)) != null)
            throw new IllegalStateException("[MLua] Block type '" + key + "' is already registered");
    }

    public static Map<String, String> registeredTypes() {
        Map<String, String> types = new TreeMap<>();
        types.put("default", "A standard block with configurable Minecraft properties and scripted callbacks.");
        REGISTERED_TYPES.forEach((name, type) -> types.put(name, type.description()));
        return Map.copyOf(types);
    }

    public static void validateTypeDefinition(Map<String, Object> properties) {
        String name = selectedType(properties);
        if (name != null && !"default".equals(name) && !REGISTERED_TYPES.containsKey(name))
            throw new IllegalArgumentException("[MLua] Unknown block type: " + name);
    }

    public Block create(Map<String, Object> properties, String registryName,
                        Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        RegisteredType custom = registeredType(properties);
        if (custom != null) return custom.provider().createNamed(Map.copyOf(properties), makeProperties(properties), registryName, blockEntityType);
        return new Block(makeProperties(properties));
    }

    public Block createScripted(Map<String, Object> properties, String behaviorKey,
                                Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        RegisteredType custom = registeredType(properties);
        if (custom != null) return custom.provider().createScripted(Map.copyOf(properties), makeProperties(properties), behaviorKey, blockEntityType);
        return MLuaScriptedBlock.create(makeProperties(properties), behaviorKey, blockEntityType);
    }

    public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
        if (properties == null) properties = Map.of();
        BlockBehaviour.Properties props = copyProperties(properties);
        Object strength = properties.get("strength");
        Object resistance = properties.get("explosionResistance");
        if (strength instanceof Number s && resistance instanceof Number r) props.strength(s.floatValue(), r.floatValue());
        else if (strength instanceof Number s) props.strength(s.floatValue());
        if (Boolean.TRUE.equals(properties.get("requiresCorrectTool"))) props.requiresCorrectToolForDrops();
        if (Boolean.FALSE.equals(properties.get("collision")) || Boolean.TRUE.equals(properties.get("noCollision"))) props.noCollision();
        if (Boolean.TRUE.equals(properties.get("noOcclusion"))) props.noOcclusion();
        if (Boolean.TRUE.equals(properties.get("randomTicks"))) props.randomTicks();
        if (properties.get("lightLevel") instanceof Number light) props.lightLevel(state -> Math.max(0, Math.min(15, light.intValue())));
        if (properties.get("friction") instanceof Number value) props.friction(Math.max(0.0F, value.floatValue()));
        if (properties.get("speedFactor") instanceof Number value) props.speedFactor(Math.max(0.0F, value.floatValue()));
        if (properties.get("jumpFactor") instanceof Number value) props.jumpFactor(Math.max(0.0F, value.floatValue()));
        if (properties.get("sound") instanceof String sound) props.sound(sound(sound));
        if (properties.get("mapColor") instanceof String color) props.mapColor(color(color));
        return props;
    }

    private static RegisteredType registeredType(Map<String, Object> properties) {
        String type = selectedType(properties);
        return type == null || "default".equals(type) ? null : REGISTERED_TYPES.get(type);
    }

    private static String selectedType(Map<String, Object> properties) {
        if (properties == null) return null;
        Object value = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
        if (value == null) return null;
        if (!(value instanceof String name) || name.isBlank()) throw new IllegalArgumentException("[MLua] Block type must be a non-empty string");
        return normalizeTypeName(name);
    }

    private static String normalizeTypeName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("[MLua] Block type name must be non-empty");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("[MLua] Invalid block type name: " + name);
        return normalized;
    }

    private record RegisteredType(String description, TypeProvider provider) { }

    public interface TypeProvider {
        Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties, String registryName,
                          Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
        Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties, String behaviorKey,
                             Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
    }

    private BlockBehaviour.Properties copyProperties(Map<String, Object> properties) {
        Object copy = properties.get("copy");
        if (copy instanceof String id) {
            Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
            if (block != null) return BlockBehaviour.Properties.ofFullCopy(block);
        }
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
    }

    private SoundType sound(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "wood" -> SoundType.WOOD;
            case "glass" -> SoundType.GLASS;
            case "metal" -> SoundType.METAL;
            case "sand" -> SoundType.SAND;
            case "snow", "powder_snow", "powdersnow" -> SoundType.SNOW;
            case "wool" -> SoundType.WOOL;
            case "gravel" -> SoundType.GRAVEL;
            default -> SoundType.STONE;
        };
    }

    private MapColor color(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "grass" -> MapColor.GRASS;
            case "sand" -> MapColor.SAND;
            case "wood" -> MapColor.WOOD;
            case "water" -> MapColor.WATER;
            case "metal", "gold" -> MapColor.METAL;
            case "red" -> MapColor.COLOR_RED;
            case "blue" -> MapColor.COLOR_BLUE;
            case "green" -> MapColor.COLOR_GREEN;
            case "white", "snow" -> MapColor.SNOW;
            default -> MapColor.STONE;
        };
    }
}
