package dev.mlua.modules.registry.service;

import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaRegistryContainerDefinitionsTest {
    @Test
    void resolvesContainerAndSlotItemIds() {
        LuaTable items = new LuaTable();
        items.set(1, "stone");
        items.set(2, "minecraft:dirt");
        LuaTable allowed = new LuaTable();
        allowed.set("allowedItems", items);

        assertEquals(Set.of("addon:stone", "minecraft:dirt"),
            MLuaRegistryContainerDefinitions.allowedItems("addon", allowed));

        LuaTable slotItems = new LuaTable();
        slotItems.set(1, "torch");
        LuaTable slot = new LuaTable();
        slot.set("allowedItems", slotItems);
        LuaTable slots = new LuaTable();
        slots.set(1, slot);
        LuaTable definition = new LuaTable();
        definition.set("slots", slots);

        assertEquals(Map.of(1, Set.of("addon:torch")),
            MLuaRegistryContainerDefinitions.slotAllowedItems("addon", definition, 9));
    }

    @Test
    void rejectsSparseAndOverlappingAliasDefinitions() {
        LuaTable slot = new LuaTable();
        LuaTable sparseSlots = new LuaTable();
        sparseSlots.set(2, slot);
        LuaTable sparseDefinition = new LuaTable();
        sparseDefinition.set("slots", sparseSlots);
        assertThrows(LuaError.class,
            () -> MLuaRegistryContainerDefinitions.slotAllowedItems("addon", sparseDefinition, 9));

        LuaTable aliases = new LuaTable();
        aliases.set("rows", 3);
        aliases.set("Rows", 4);
        assertThrows(LuaError.class,
            () -> MLuaRegistryContainerDefinitions.property(aliases, "rows", "Rows"));
    }
}
