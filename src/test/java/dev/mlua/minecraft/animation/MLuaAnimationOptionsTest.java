package dev.mlua.minecraft.animation;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAnimationOptionsTest {
    @Test
    void keepsTheOriginalJavaConstructorAndLoopAccessor() {
        MLuaAnimationOptions options = new MLuaAnimationOptions(false, 1.5F);

        assertFalse(options.loop());
        assertEquals(Boolean.FALSE, options.loopOverride());
        assertEquals(1.5F, options.speed());
        assertEquals(0.0F, options.fadeDuration());
    }

    @Test
    void leavesLoopChoiceToTheModelWhenOptionIsOmitted() {
        MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(LuaValue.NIL, "test");

        assertNull(options.loopOverride());
        assertTrue(options.loop());
        assertEquals(1.0F, options.speed());
        assertEquals(0.0F, options.fadeDuration());
    }

    @Test
    void retainsAnExplicitFalseLoopOverride() {
        LuaTable table = new LuaTable();
        table.set("Loop", LuaValue.FALSE);

        MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(table, "test");

        assertEquals(Boolean.FALSE, options.loopOverride());
    }

    @Test
    void readsFadeDurationInEitherSupportedCase() {
        LuaTable table = new LuaTable();
        table.set("fadeDuration", LuaValue.valueOf(0.25));

        MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(table, "test");

        assertEquals(0.25F, options.fadeDuration());
    }
}
