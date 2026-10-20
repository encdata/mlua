package dev.mlua.modules.registry.service;

import java.util.Map;
import net.minecraft.world.entity.MobCategory;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaRegistryEntityDefinitionsTest {
    @Test
    void parsesEntityCategoriesAndDefaults() {
        assertEquals(MobCategory.CREATURE, MLuaRegistryEntityDefinitions.mobCategory(null));

        LuaTable properties = new LuaTable();
        properties.set("category", "water_ambient");
        assertEquals(MobCategory.WATER_AMBIENT, MLuaRegistryEntityDefinitions.mobCategory(properties));

        properties.set("category", "unknown");
        assertThrows(LuaError.class, () -> MLuaRegistryEntityDefinitions.mobCategory(properties));
    }

    @Test
    void separatesBuiltInAndNamespacedAttributesAndChecksRanges() {
        LuaTable attributes = new LuaTable();
        attributes.set("maxHealth", 30.0);
        attributes.set("knockbackResistance", 1.1);
        attributes.set("addon:focus", 4.5);

        assertEquals(30.0, MLuaRegistryEntityDefinitions.attributeValue(attributes, "maxHealth", 20.0), 0.0);
        assertEquals(Map.of("addon:focus", 4.5), MLuaRegistryEntityDefinitions.additionalAttributes(attributes));
        assertThrows(LuaError.class,
            () -> MLuaRegistryEntityDefinitions.attributeValue(attributes, "knockbackResistance", 0.0));
        assertThrows(LuaError.class,
            () -> MLuaRegistryEntityDefinitions.additionalAttributes(attributeTable("addon:focus", Double.NaN)));
        assertThrows(LuaError.class,
            () -> MLuaRegistryEntityDefinitions.additionalAttributes(attributeTable("focus", 2.0)));
    }

    @Test
    void rejectsEntitySizesThatCannotBecomePositiveFloats() {
        MLuaRegistryEntityDefinitions.requirePositiveSize("width", 0.25);
        assertThrows(LuaError.class, () -> MLuaRegistryEntityDefinitions.requirePositiveSize("width", 0.0));
        assertThrows(LuaError.class,
            () -> MLuaRegistryEntityDefinitions.requirePositiveSize("width", Double.MAX_VALUE));
    }

    @Test
    void validatesSpawnEggColorAsAnUnsignedRgbValue() {
        LuaTable properties = new LuaTable();
        properties.set("primaryColor", 0x12ABEF);
        assertEquals(0x12ABEF,
            MLuaRegistryEntityDefinitions.colorProperty(properties, "primaryColor", 0));

        properties.set("primaryColor", 16777216);
        assertThrows(LuaError.class,
            () -> MLuaRegistryEntityDefinitions.colorProperty(properties, "primaryColor", 0));
    }

    private static LuaTable attributeTable(String name, double value) {
        LuaTable attributes = new LuaTable();
        attributes.set(name, LuaValue.valueOf(value));
        return attributes;
    }
}
