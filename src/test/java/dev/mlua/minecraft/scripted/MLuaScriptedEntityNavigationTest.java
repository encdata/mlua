package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaScriptedEntityNavigationTest {
    @Test
    void readsFiniteCoordinateOverloadAndDefaultSpeed() {
        MLuaScriptedEntityNavigation.Request request = MLuaScriptedEntityNavigation.readRequest(
            LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(1),
                LuaValue.valueOf(2), LuaValue.valueOf(3)}), "MoveTo");

        assertEquals(new MLuaScriptedEntityNavigation.Request(1, 2, 3, 1), request);
    }

    @Test
    void readsOptionalSpeedAndRejectsInvalidCoordinatesOrSpeed() {
        MLuaScriptedEntityNavigation.Request request = MLuaScriptedEntityNavigation.readRequest(
            LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(1),
                LuaValue.valueOf(2), LuaValue.valueOf(3), LuaValue.valueOf(0.5)}), "NavigateTo");
        assertEquals(new MLuaScriptedEntityNavigation.Request(1, 2, 3, 0.5), request);

        assertThrows(LuaError.class, () -> MLuaScriptedEntityNavigation.readRequest(
            LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(Double.NaN),
                LuaValue.valueOf(2), LuaValue.valueOf(3)}), "MoveTo"));
        assertThrows(LuaError.class, () -> MLuaScriptedEntityNavigation.readRequest(
            LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(1),
                LuaValue.valueOf(2), LuaValue.valueOf(3), LuaValue.ZERO}), "NavigateTo"));
    }
}
