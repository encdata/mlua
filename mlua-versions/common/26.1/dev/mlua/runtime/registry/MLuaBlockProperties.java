package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Minecraft 26.1 adapter for shared block definition and property handling. */
final class MLuaBlockProperties {
    private static final Map<String, SoundType> SOUNDS = namedValues(SoundType.class, SoundType.class);
    private static final Map<String, MapColor> MAP_COLORS = mapColors();

    static java.util.List<String> soundNames() {
        return SOUNDS.keySet().stream().sorted().toList();
    }

    static java.util.List<String> mapColorNames() {
        return MAP_COLORS.keySet().stream().sorted().toList();
    }

    static void validateCopyDefinition(Map<String, Object> properties) {
        Object copy = properties.get("copy");
        if (copy != null && (!(copy instanceof String id) || id.isBlank())) {
            throw new IllegalArgumentException("[MLua] Block copy must be a non-empty block identifier");
        }
        if (copy instanceof String id) {
            var location = MLuaResourceLocations.tryParseOrNull(id);
            if (location == null) throw new IllegalArgumentException("[MLua] Invalid block copy identifier '" + id + "'");
            if (BuiltInRegistries.BLOCK.getOptional(location).isEmpty()) {
                throw new IllegalArgumentException("[MLua] Unknown block in copy property: " + id);
            }
        }
    }

    static void validateBlockProperties(Map<String, Object> properties) {
        Object strength = aliased(properties, "hardness", "strength");
        Object resistance = aliased(properties, "resistance", "explosionResistance");
        validateNumber(strength, "hardness", -1.0, Float.MAX_VALUE);
        validateNumber(resistance, "resistance", 0.0, Float.MAX_VALUE);
        if (resistance != null && strength == null) {
            throw new IllegalArgumentException("[MLua] Block resistance requires a hardness value");
        }
        validateNumber(properties.get("lightLevel"), "lightLevel", 0.0, 15.0, true);
        validateNumber(properties.get("friction"), "friction", 0.0, Float.MAX_VALUE);
        validateNumber(properties.get("speedFactor"), "speedFactor", 0.0, Float.MAX_VALUE);
        validateNumber(properties.get("jumpFactor"), "jumpFactor", 0.0, Float.MAX_VALUE);
        for (String property : new String[]{"requiresCorrectTool", "collision", "noCollision", "noOcclusion",
                "randomTicks", "ignitedByLava", "replaceable"}) {
            Object value = properties.get(property);
            if (value != null && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a boolean");
            }
        }
        if (Boolean.TRUE.equals(properties.get("collision")) && Boolean.TRUE.equals(properties.get("noCollision"))) {
            throw new IllegalArgumentException("[MLua] Block properties 'collision' and 'noCollision' conflict");
        }
        for (String property : new String[]{"sound", "mapColor"}) {
            Object value = properties.get(property);
            if (value != null && (!(value instanceof String name) || name.isBlank())) {
                throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a non-empty string");
            }
        }
        if (properties.get("sound") instanceof String name) sound(name);
        if (properties.get("mapColor") instanceof String name) color(name);
        validateEnum(properties, "pushReaction", PushReaction.class);
        validateEnum(properties, "instrument", NoteBlockInstrument.class);
    }

    BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
        if (properties == null) properties = Map.of();
        validateCopyDefinition(properties);
        validateBlockProperties(properties);
        BlockBehaviour.Properties result = this.copyProperties(properties);
        Object strength = aliased(properties, "hardness", "strength");
        Object resistance = aliased(properties, "resistance", "explosionResistance");
        if (strength instanceof Number value && resistance instanceof Number blast) {
            result.strength(value.floatValue(), blast.floatValue());
        } else if (strength instanceof Number value) {
            result.strength(value.floatValue());
        }
        if (Boolean.TRUE.equals(properties.get("requiresCorrectTool"))) result.requiresCorrectToolForDrops();
        if (Boolean.FALSE.equals(properties.get("collision")) || Boolean.TRUE.equals(properties.get("noCollision"))) {
            result.noCollision();
        }
        if (Boolean.TRUE.equals(properties.get("noOcclusion"))) result.noOcclusion();
        if (Boolean.TRUE.equals(properties.get("randomTicks"))) result.randomTicks();
        if (Boolean.TRUE.equals(properties.get("ignitedByLava"))) result.ignitedByLava();
        if (Boolean.TRUE.equals(properties.get("replaceable"))) result.replaceable();
        if (properties.get("pushReaction") instanceof String value) {
            result.pushReaction(enumValue(value, PushReaction.class, "pushReaction"));
        }
        if (properties.get("instrument") instanceof String value) {
            result.instrument(enumValue(value, NoteBlockInstrument.class, "instrument"));
        }
        if (properties.get("lightLevel") instanceof Number value) {
            int level = value.intValue();
            result.lightLevel(state -> level);
        }
        if (properties.get("friction") instanceof Number value) result.friction(value.floatValue());
        if (properties.get("speedFactor") instanceof Number value) result.speedFactor(value.floatValue());
        if (properties.get("jumpFactor") instanceof Number value) result.jumpFactor(value.floatValue());
        if (properties.get("sound") instanceof String value) result.sound(sound(value));
        if (properties.get("mapColor") instanceof String value) result.mapColor(color(value));
        return result;
    }

    private BlockBehaviour.Properties copyProperties(Map<String, Object> properties) {
        Object copy = properties.get("copy");
        Block source = copy instanceof String id
            ? BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null)
            : Blocks.STONE;
        if (source == null) throw new IllegalArgumentException("[MLua] Unknown block in copy property: " + copy);
        return BlockBehaviour.Properties.ofFullCopy(source);
    }

    private static Object aliased(Map<String, Object> properties, String canonical, String alternate) {
        Object primary = properties.get(canonical);
        Object alias = properties.get(alternate);
        if (primary != null && alias != null) {
            throw new IllegalArgumentException("[MLua] Provide only one of block properties '" + canonical + "' and '" + alternate + "'");
        }
        return primary != null ? primary : alias;
    }

    private static void validateNumber(Object raw, String name, double minimum, double maximum) {
        validateNumber(raw, name, minimum, maximum, false);
    }

    private static void validateNumber(Object raw, String name, double minimum, double maximum, boolean wholeNumber) {
        if (raw == null) return;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() > maximum
                || wholeNumber && number.doubleValue() != Math.rint(number.doubleValue())) {
            String requirement = wholeNumber ? "a whole number from " : "a finite number from ";
            throw new IllegalArgumentException("[MLua] Block property '" + name + "' must be " + requirement + minimum + " to " + maximum);
        }
    }

    private static <T extends Enum<T>> void validateEnum(Map<String, Object> properties, String property, Class<T> type) {
        Object value = properties.get(property);
        if (value != null && (!(value instanceof String name) || name.isBlank())) {
            throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a non-empty string");
        }
        if (value instanceof String name) enumValue(name, type, property);
    }

    private static <T extends Enum<T>> T enumValue(String name, Class<T> type, String property) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            String catalog = "instrument".equals(property) ? "Instruments" : "PushReactions";
            throw new IllegalArgumentException("[MLua] Invalid block " + property + " '" + name
                + "'. Use Registry.Blocks:Get" + catalog + "() to list valid values.", invalid);
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
        MapColor limeTerracotta = values.get("terracotta_light_green");
        if (limeTerracotta != null) values.putIfAbsent("lime_terracotta", limeTerracotta);
        return Collections.unmodifiableMap(values);
    }

    private static <T> Map<String, T> namedValues(Class<?> owner, Class<T> valueType) {
        Map<String, T> values = new TreeMap<>();
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !valueType.isAssignableFrom(field.getType())) continue;
            try {
                Object value = field.get(null);
                if (value != null) values.put(normalize(field.getName()), valueType.cast(value));
            } catch (IllegalAccessException ignored) {
                // Omit inaccessible fields from the current Minecraft version's catalog.
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static SoundType sound(String name) {
        String normalized = normalize(name);
        SoundType value = SOUNDS.get(normalized);
        if (value == null) value = SOUNDS.get(normalized.replace("_", ""));
        if (value == null) throw new IllegalArgumentException("[MLua] Unknown block sound: " + name
            + ". Use Registry.Blocks:GetSounds() to list values available in this Minecraft version.");
        return value;
    }

    private static MapColor color(String name) {
        String normalized = normalize(name);
        MapColor value = MAP_COLORS.get(normalized);
        if (value == null) value = MAP_COLORS.get(normalized.replace("_", ""));
        if (value == null) throw new IllegalArgumentException("[MLua] Unknown block mapColor: " + name
            + ". Use Registry.Blocks:GetMapColors() to list values available in this Minecraft version.");
        return value;
    }
}
