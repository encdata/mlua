package dev.mlua.modules.tween.service;

import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaTweenInfoTest {
    @Test
    void defaultsAndLuaTableRoundTripUseTheSameValidatedValues() {
        MLuaTweenInfo created = MLuaTweenInfo.fromArguments(
            LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(0.75)}), 0);

        assertEquals(EasingStyle.Linear, created.easingStyle());
        assertEquals(EasingDirection.Out, created.easingDirection());
        assertEquals(created, MLuaTweenInfo.fromTable(created.toLuaTable()));
    }

    @Test
    void acceptsMatchingStringNamesForEasingValues() {
        MLuaTweenInfo created = MLuaTweenInfo.fromArguments(LuaValue.varargsOf(new LuaValue[]{
            LuaValue.valueOf(1.0), LuaValue.valueOf("Sine"), LuaValue.valueOf("InOut")}), 0);

        assertEquals(EasingStyle.Sine, created.easingStyle());
        assertEquals(EasingDirection.InOut, created.easingDirection());
    }

    @Test
    void rejectsInvalidEasingAndDurationAtConstruction() {
        assertThrows(LuaError.class, () -> MLuaTweenInfo.fromArguments(LuaValue.varargsOf(new LuaValue[]{
            LuaValue.valueOf(1.0), LuaValue.valueOf("NotAnEasingStyle")}), 0));
        assertThrows(LuaError.class, () -> MLuaTweenInfo.fromArguments(LuaValue.varargsOf(new LuaValue[]{
            LuaValue.valueOf(Double.NaN)}), 0));
    }
}
