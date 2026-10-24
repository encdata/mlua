package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedEntityPersistentDataTest {
    @Test
    void roundTripsMapsArraysAndNativeScalarTypes() {
        MLuaScriptedEntityPersistentData data = new MLuaScriptedEntityPersistentData();
        LuaTable source = new LuaTable();
        source.set("enabled", LuaValue.TRUE);
        source.set("count", LuaValue.valueOf(12));
        source.set("label", LuaValue.valueOf("guard"));
        LuaTable numbers = new LuaTable();
        numbers.set(1, LuaValue.valueOf(2));
        numbers.set(2, LuaValue.valueOf(4));
        source.set("numbers", numbers);

        data.replaceFromLua(source);
        LuaTable result = data.toLua();

        assertTrue(result.get("enabled").toboolean());
        assertEquals(12, result.get("count").toint());
        assertEquals("guard", result.get("label").tojstring());
        assertEquals(2, result.get("numbers").get(1).toint());
        assertEquals(4, result.get("numbers").get(2).toint());
    }

    @Test
    void rejectsInvalidReplacementWithoutDiscardingExistingData() {
        MLuaScriptedEntityPersistentData data = new MLuaScriptedEntityPersistentData();
        LuaTable original = new LuaTable();
        original.set("state", LuaValue.valueOf("idle"));
        data.replaceFromLua(original);

        LuaTable sparse = new LuaTable();
        sparse.set(2, LuaValue.valueOf("gap"));
        LuaTable invalid = new LuaTable();
        invalid.set("values", sparse);
        LuaError failure = assertThrows(LuaError.class, () -> data.replaceFromLua(invalid));

        assertTrue(failure.getMessage().contains("arrays must use dense indexes starting at one"));
        assertEquals("idle", data.toLua().get("state").tojstring());
        assertFalse(data.toLua().get("values").istable());
    }

    @Test
    void rejectsCyclesAndNonFiniteNumbers() {
        MLuaScriptedEntityPersistentData data = new MLuaScriptedEntityPersistentData();
        LuaTable cyclic = new LuaTable();
        cyclic.set("self", cyclic);
        assertTrue(assertThrows(LuaError.class, () -> data.replaceFromLua(cyclic))
            .getMessage().contains("cannot save cyclic Lua tables"));

        LuaTable nonFinite = new LuaTable();
        nonFinite.set("value", LuaValue.valueOf(Double.NaN));
        assertTrue(assertThrows(LuaError.class, () -> data.replaceFromLua(nonFinite))
            .getMessage().contains("cannot save NaN or infinite numbers"));
    }
}
