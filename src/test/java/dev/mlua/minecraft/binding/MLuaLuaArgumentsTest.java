package dev.mlua.minecraft.binding;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaLuaArgumentsTest {
    @Test
    void keepsProvidedPropertyTableWithoutCopyingIt() {
        LuaTable provided = new LuaTable();
        provided.set("Visible", LuaValue.TRUE);

        assertSame(provided, MLuaLuaArguments.tableOrEmpty(provided));
    }

    @Test
    void createsAnEmptyFallbackOnlyForMissingOrNonTableValues() {
        LuaTable missing = MLuaLuaArguments.tableOrEmpty(LuaValue.NIL);
        LuaTable invalid = MLuaLuaArguments.tableOrEmpty(LuaValue.valueOf("not a table"));
        LuaTable absent = MLuaLuaArguments.tableOrEmpty(null);

        assertNotSame(missing, invalid);
        assertNotSame(missing, absent);
        assertTrue(missing.get("Visible").isnil());
        assertTrue(invalid.get("Visible").isnil());
    }

    @Test
    void readsOptionalFiniteNonNegativeNumbersWithOneValidationContract() {
        assertEquals(12.5, MLuaLuaArguments.optionalFiniteNonNegativeNumber(
            LuaValue.varargsOf(new LuaValue[] { LuaValue.NIL, LuaValue.valueOf(12.5) }),
            2, 100.0, "World:Raycast", "distance"), 0.0);
        assertEquals(100.0, MLuaLuaArguments.optionalFiniteNonNegativeNumber(
            LuaValue.NONE, 1, 100.0, "World:Raycast", "distance"), 0.0);
        assertThrows(LuaError.class, () -> MLuaLuaArguments.optionalFiniteNonNegativeNumber(
            LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf(-1.0) }),
            1, 100.0, "World:Raycast", "distance"));
        assertThrows(LuaError.class, () -> MLuaLuaArguments.optionalFiniteNonNegativeNumber(
            LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf(Double.NaN) }),
            1, 100.0, "World:Raycast", "distance"));
    }
}
