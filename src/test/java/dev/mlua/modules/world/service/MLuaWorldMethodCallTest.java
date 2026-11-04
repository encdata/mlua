package dev.mlua.modules.world.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

class MLuaWorldMethodCallTest {
    @Test
    void prependsWorldAndForwardsArgumentsWithoutChangingTheirOrder() {
        LuaTable world = new LuaTable();
        LuaValue first = LuaValue.valueOf("first");
        LuaValue second = LuaValue.valueOf(2);
        world.set("Combine", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                assertSame(world, args.arg1());
                assertSame(first, args.arg(2));
                assertSame(second, args.arg(3));
                assertEquals(3, args.narg());
                return LuaValue.TRUE;
            }
        });

        assertSame(LuaValue.TRUE, MLuaWorldMethodCall.invoke(
            world, "Combine", LuaValue.varargsOf(LuaValue.valueOf("service"), first, second), 2));
    }

    @Test
    void forwardsNoArgumentsForWorldMethodsWithoutParameters() {
        LuaTable world = new LuaTable();
        world.set("Reset", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                assertSame(world, args.arg1());
                assertEquals(1, args.narg());
                return LuaValue.NONE;
            }
        });

        assertEquals(0, MLuaWorldMethodCall.invoke(world, "Reset", LuaValue.NONE, 1).narg());
    }

    @Test
    void reportsMissingWorldMethodsConsistently() {
        LuaError failure = assertThrows(LuaError.class,
            () -> MLuaWorldMethodCall.invoke(new LuaTable(), "Missing", LuaValue.NONE, 1));

        assertEquals("[MLua] World instance does not implement 'Missing'.", failure.getMessage());
    }
}
