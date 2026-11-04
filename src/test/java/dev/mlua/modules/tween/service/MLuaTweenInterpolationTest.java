package dev.mlua.modules.tween.service;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaTweenInterpolationTest {
    @Test
    void callsNativeLerpWithStartGoalAndSharedAlphaArguments() {
        LuaValue start = LuaValue.valueOf("start");
        LuaValue goal = LuaValue.valueOf("goal");
        LuaValue alpha = LuaValue.valueOf(0.75);
        LuaValue lerp = new VarArgFunction() {
            @Override public Varargs invoke(Varargs arguments) {
                assertEquals(3, arguments.narg());
                assertSame(start, arguments.arg(1));
                assertSame(goal, arguments.arg(2));
                assertSame(alpha, arguments.arg(3));
                return goal;
            }
        };

        assertSame(goal, MLuaTween.interpolate(start, goal, 0.75, alpha, lerp));
    }
}
