package dev.mlua.modules.entity;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

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
}
