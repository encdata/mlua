package dev.mlua.modules.registry.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRegistryLuaValuesTest {
    @Test
    void namespaceValidationKeepsTheRegistryIdentifierContract() {
        assertNull(MLuaRegistryLuaValues.optionalNamespace(LuaValue.NIL));
        assertEquals("addon.name-1_x",
            MLuaRegistryLuaValues.optionalNamespace(LuaValue.valueOf("addon.name-1_x")));
        assertThrows(LuaError.class,
            () -> MLuaRegistryLuaValues.optionalNamespace(LuaValue.valueOf("Addon")));
        assertThrows(LuaError.class,
            () -> MLuaRegistryLuaValues.optionalNamespace(LuaValue.valueOf("addon:sub")));
        assertThrows(LuaError.class,
            () -> MLuaRegistryLuaValues.optionalNamespace(LuaValue.valueOf("")));
    }

    @Test
    void stringArraysUseLuaOneBasedIndices() {
        var values = MLuaRegistryLuaValues.stringArray(List.of("first", "second"));

        assertEquals("first", values.get(1).tojstring());
        assertEquals("second", values.get(2).tojstring());
        assertTrue(values.get(0).isnil());
    }
}
