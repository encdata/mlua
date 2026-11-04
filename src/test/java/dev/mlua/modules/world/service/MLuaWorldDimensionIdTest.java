package dev.mlua.modules.world.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MLuaWorldDimensionIdTest {
    @Test
    void usesMinecraftAsTheDefaultNamespace() {
        assertEquals("minecraft:overworld", MLuaWorldDimensionId.canonicalize("overworld"));
        assertEquals("minecraft:overworld", MLuaWorldDimensionId.canonicalize(" minecraft:overworld "));
    }

    @Test
    void invalidIdentifiersDoNotMatchAnyDimension() {
        assertNull(MLuaWorldDimensionId.canonicalize("bad namespace:overworld"));
        assertNull(MLuaWorldDimensionId.canonicalize("  "));
        assertFalse(MLuaWorldDimensionId.matches("minecraft:overworld", null));
        assertFalse(MLuaWorldDimensionId.matches("minecraft:overworld", "other:overworld"));
        assertTrue(MLuaWorldDimensionId.matches("minecraft:overworld", "minecraft:overworld"));
    }
}
