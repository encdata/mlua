package dev.mlua.modules.world;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaWorldGameRuleValueTest {
    @Test
    void convertsOnlyValuesThatMatchTheRuleType() {
        assertEquals(true, MLuaWorldGameRuleValue.convert("doMobSpawning", false, LuaValue.TRUE));
        assertEquals(12, MLuaWorldGameRuleValue.convert("randomTickSpeed", 3, LuaValue.valueOf(12)));
        assertEquals("custom", MLuaWorldGameRuleValue.convert("customRule", "default", LuaValue.valueOf("custom")));

        assertThrows(LuaError.class,
            () -> MLuaWorldGameRuleValue.convert("doMobSpawning", false, LuaValue.valueOf("true")));
        assertThrows(LuaError.class,
            () -> MLuaWorldGameRuleValue.convert("randomTickSpeed", 3, LuaValue.valueOf(1.5)));
        assertThrows(LuaError.class,
            () -> MLuaWorldGameRuleValue.convert("customRule", "default", LuaValue.valueOf(true)));
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeIntegerValues() {
        assertThrows(LuaError.class,
            () -> MLuaWorldGameRuleValue.convert("randomTickSpeed", 3, LuaValue.valueOf(Double.NaN)));
        assertThrows(LuaError.class,
            () -> MLuaWorldGameRuleValue.convert("randomTickSpeed", 3, LuaValue.valueOf((double) Integer.MAX_VALUE + 1)));
    }
}
