package dev.mlua.modules.registry.service;

import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Parses and validates the Lua properties used to register scripted entities. */
final class MLuaRegistryEntityDefinitions {
    private static final Set<String> BUILT_IN_ATTRIBUTES = Set.of(
        "maxHealth", "movementSpeed", "attackDamage", "armor", "armorToughness",
        "attackSpeed", "knockbackResistance", "followRange");

    private MLuaRegistryEntityDefinitions() { }

    static MobCategory mobCategory(LuaTable properties) {
        String value = MLuaRegistryPropertyReaders.stringProperty(
            properties, "category", "Category", "mobCategory", "MobCategory");
        if (value == null || value.isBlank()) return MobCategory.CREATURE;

        return switch (value.trim().toLowerCase()) {
            case "creature" -> MobCategory.CREATURE;
            case "monster" -> MobCategory.MONSTER;
            case "ambient" -> MobCategory.AMBIENT;
            case "water_creature", "watercreature" -> MobCategory.WATER_CREATURE;
            case "water_ambient", "waterambient" -> MobCategory.WATER_AMBIENT;
            case "misc" -> MobCategory.MISC;
            case "underground_water_creature", "undergroundwatercreature" -> MobCategory.UNDERGROUND_WATER_CREATURE;
            case "axolotls", "axolotl" -> MobCategory.AXOLOTLS;
            default -> throw new LuaError("[MLua] Unsupported entity category: " + value);
        };
    }

    static double attributeValue(LuaTable attributes, String key, double fallback) {
        if (attributes == null || attributes.get(key).isnil()) return fallback;
        LuaValue value = attributes.get(key);
        if (!value.isnumber()) {
            throw new LuaError("[MLua] Registry.Entities:Register attributes." + key + " must be a number.");
        }
        double number = value.todouble();
        boolean invalid = !Double.isFinite(number) || number < 0.0
            || ("maxHealth".equals(key) && number == 0.0)
            || ("knockbackResistance".equals(key) && number > 1.0);
        if (invalid) {
            String constraint = "knockbackResistance".equals(key) ? "between 0 and 1"
                : "finite and " + ("maxHealth".equals(key) ? "greater than zero" : "non-negative");
            throw new LuaError("[MLua] Registry.Entities:Register attributes." + key + " must be " + constraint + ".");
        }
        return number;
    }

    static Map<String, Double> additionalAttributes(LuaTable attributes) {
        if (attributes == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = attributes.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING) {
                throw new LuaError("[MLua] Registry.Entities:Register attribute keys must be strings.");
            }

            String name = key.tojstring();
            if (BUILT_IN_ATTRIBUTES.contains(name)) continue;
            if (!name.contains(":")) {
                throw new LuaError("[MLua] Unknown entity attribute '" + name
                    + "'. Use a built-in alias or a registered namespaced attribute id.");
            }
            var id = MLuaResourceLocations.tryParseOrNull(name);
            if (id == null || !id.toString().equals(name)) {
                throw new LuaError("[MLua] Entity attribute '" + name
                    + "' must be a valid lowercase namespaced id.");
            }
            LuaValue value = entry.arg(2);
            if (!value.isnumber() || !Double.isFinite(value.todouble())) {
                throw new LuaError("[MLua] Registry.Entities:Register attribute '" + name
                    + "' must have a finite number value.");
            }
            result.put(id.toString(), value.todouble());
        }
        return Collections.unmodifiableMap(result);
    }

    static void addAttribute(AttributeSupplier.Builder builder, String name, double value) {
        var id = MLuaResourceLocations.tryParseOrNull(name);
        if (id == null) throw new IllegalStateException("Invalid registered entity attribute id: " + name);
        //--version 1.20.1
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            throw new IllegalStateException("Entity attribute is not registered: " + name);
        }
        builder.add(attribute, value);
        //--endversion
        //--version 1.21.1
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
        if (attribute == null) {
            throw new IllegalStateException("Entity attribute is not registered: " + name);
        }
        builder.add(attribute, value);
        //--endversion
        //--version 26.1
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(id).orElse(null);
        if (attribute == null) {
            throw new IllegalStateException("Entity attribute is not registered: " + name);
        }
        builder.add(attribute, value);
        //--endversion
    }

    static void requirePositiveSize(String property, double value) {
        float converted = (float)value;
        if (!Double.isFinite(value) || value <= 0.0 || !Float.isFinite(converted) || converted <= 0.0F) {
            throw new LuaError("[MLua] Registry.Entities:Register " + property
                + " must be finite, greater than zero, and representable as a positive float.");
        }
    }

    static int colorProperty(LuaTable properties, String name, int fallback) {
        if (properties == null || properties.get(name).isnil()) return fallback;
        LuaValue value = properties.get(name);
        if (!value.isnumber()) {
            throw invalidColor(name);
        }
        double color = value.todouble();
        if (!Double.isFinite(color) || color != Math.rint(color) || color < 0.0 || color > 16777215.0) {
            throw invalidColor(name);
        }
        return (int)color;
    }

    private static LuaError invalidColor(String name) {
        return new LuaError("[MLua] Registry.Entities:RegisterSpawnEgg " + name
            + " must be a whole number from 0 to 16777215.");
    }
}
