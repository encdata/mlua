package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
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
    private static final Map<String, SoundType> SOUNDS = namedValues(SoundType.class, SoundType.class);
    private static final Map<String, MapColor> MAP_COLORS = mapColors();

    public static java.util.List<String> soundNames() {
        return SOUNDS.keySet().stream().sorted().toList();
    }

    public static java.util.List<String> mapColorNames() {
        return MAP_COLORS.keySet().stream().sorted().toList();
    }

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
        if (properties == null) return;
        validateNumber(properties, "strength", -1.0, Float.MAX_VALUE);
        validateNumber(properties, "hardness", -1.0, Float.MAX_VALUE);
        validateNumber(properties, "resistance", 0.0, Float.MAX_VALUE);
        validateNumber(properties, "explosionResistance", 0.0, Float.MAX_VALUE);
        validateNumber(properties, "lightLevel", 0.0, 15.0);
        for (String property : new String[]{"requiresCorrectTool", "collision", "noCollision", "noOcclusion", "randomTicks"}) {
            Object value = properties.get(property);
            if (value != null && !(value instanceof Boolean))
                throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a boolean");
        }
        if (properties.get("sound") instanceof String value) sound(value);
        if (properties.get("mapColor") instanceof String value) color(value);
    }

    private static void validateNumber(Map<String, Object> properties, String name, double minimum, double maximum) {
        Object raw = properties.get(name);
        if (raw == null) return;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
            || number.doubleValue() < minimum || number.doubleValue() > maximum) {
            throw new IllegalArgumentException("[MLua] Block property '" + name + "' must be a finite number from " + minimum + " to " + maximum);
        }
    }

    private static Map<String, MapColor> mapColors() {
        Map<String, MapColor> values = new TreeMap<>(namedValues(MapColor.class, MapColor.class));
        for (Map.Entry<String, MapColor> entry : new java.util.ArrayList<>(values.entrySet())) {
            String name = entry.getKey();
            if (name.startsWith("color_")) values.putIfAbsent(name.substring("color_".length()), entry.getValue());
            if (name.startsWith("terracotta_")) values.putIfAbsent(name.substring("terracotta_".length()) + "_terracotta", entry.getValue());
            if (name.endsWith("_nylium")) values.putIfAbsent(name.substring(0, name.length() - "_nylium".length()), entry.getValue());
        }
        MapColor lime = values.get("color_light_green");
        if (lime != null) values.putIfAbsent("lime", lime);
        return Collections.unmodifiableMap(values);
    }

    private static <T> Map<String, T> namedValues(Class<?> owner, Class<T> valueType) {
        Map<String, T> values = new TreeMap<>();
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !valueType.isAssignableFrom(field.getType())) continue;
            try {
                Object value = field.get(null);
                if (value != null) values.put(field.getName().toLowerCase(Locale.ROOT), valueType.cast(value));
            } catch (IllegalAccessException ignored) {
                // Omit inaccessible fields from the current version's supported values.
            }
        }
        return Collections.unmodifiableMap(values);
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
        Object strength = properties.containsKey("hardness") ? properties.get("hardness") : properties.get("strength");
        Object resistance = properties.containsKey("resistance") ? properties.get("resistance") : properties.get("explosionResistance");
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
        SoundType value = SOUNDS.get(normalizeValue(name));
        if (value == null) value = SOUNDS.get(normalizeValue(name).replace("_", ""));
        if (value == null) throw new IllegalArgumentException("[MLua] Unknown block sound: " + name
            + ". Use Registry.Blocks:GetSounds() to list values available in this Minecraft version.");
        return value;
    }

    private MapColor color(String name) {
        String normalized = normalizeValue(name);
        MapColor value = MAP_COLORS.get(normalized);
        if (value == null) value = MAP_COLORS.get(normalized.replace("_", ""));
        if (value == null) throw new IllegalArgumentException("[MLua] Unknown block mapColor: " + name
            + ". Use Registry.Blocks:GetMapColors() to list values available in this Minecraft version.");
        return value;
    }

    private static String normalizeValue(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
