package dev.mlua.modules.entity;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaEntityQueryFilterTest {
    @Test
    void emptyFilterAcceptsEntitiesOfAnyType() {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(LuaValue.NIL, null);

        assertTrue(filter.matchesType("minecraft:sheep"));
        assertTrue(filter.matchesType("minecraft:zombie"));
    }

    @Test
    void typeFilterOnlyAcceptsItsRequestedType() {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(LuaValue.valueOf("minecraft:sheep"), null);

        assertTrue(filter.matchesType("minecraft:sheep"));
        assertFalse(filter.matchesType("minecraft:zombie"));
    }

    @Test
    void builtInAliveFiltersPreserveTypeSelection() {
        MLuaEntityQuery.Filter players = MLuaEntityQuery.alivePlayersFilter();
        MLuaEntityQuery.Filter allLivingEntities = MLuaEntityQuery.aliveEntitiesFilter(null);
        MLuaEntityQuery.Filter sheep = MLuaEntityQuery.aliveEntitiesFilter("minecraft:sheep");

        assertTrue(players.matchesType("minecraft:player"));
        assertTrue(allLivingEntities.matchesType("minecraft:zombie"));
        assertTrue(sheep.matchesType("minecraft:sheep"));
        assertFalse(sheep.matchesType("minecraft:zombie"));
    }

    @Test
    void typeSetsIncludeAndExcludeTypes() {
        LuaTable definition = new LuaTable();
        LuaTable included = new LuaTable();
        included.set(1, "minecraft:sheep");
        included.set(2, "minecraft:zombie");
        LuaTable excluded = new LuaTable();
        excluded.set(1, "minecraft:zombie");
        definition.set("types", included);
        definition.set("exclude_types", excluded);

        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(definition, null);

        assertTrue(filter.matchesType("minecraft:sheep"));
        assertFalse(filter.matchesType("minecraft:zombie"));
        assertFalse(filter.matchesType("minecraft:pig"));
    }

    @Test
    void filterArraysMustBeDenseAndBounded() {
        LuaTable sparse = new LuaTable();
        sparse.set(2, "minecraft:sheep");
        LuaTable sparseDefinition = new LuaTable();
        sparseDefinition.set("types", sparse);
        assertThrows(LuaError.class, () -> MLuaEntityQuery.compileFilter(sparseDefinition, null));

        LuaTable oversized = new LuaTable();
        for (int index = 1; index <= MLuaEntityQuery.MAX_FILTER_ARRAY_ENTRIES + 1; index++) {
            oversized.set(index, "minecraft:sheep");
        }
        LuaTable oversizedDefinition = new LuaTable();
        oversizedDefinition.set("types", oversized);
        assertThrows(LuaError.class, () -> MLuaEntityQuery.compileFilter(oversizedDefinition, null));
    }
}
