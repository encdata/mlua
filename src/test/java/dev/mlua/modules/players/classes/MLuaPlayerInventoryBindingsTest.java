package dev.mlua.modules.players.classes;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.classes.MLuaClassMethods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaPlayerInventoryBindingsTest {
    @Test
    void invokesInventoryInstanceMethodsWithTheInstanceAsSelf() {
        LuaTable inventory = new LuaTable();
        inventory.set("SetSlot", new VarArgFunction() {
            @Override public Varargs invoke(Varargs arguments) {
                assertSame(inventory, arguments.arg(1));
                return arguments.arg(3);
            }
        });

        LuaValue result = MLuaClassMethods.invokeInstanceMethod(
            inventory, "SetSlot", LuaValue.valueOf(3), LuaValue.valueOf("torch"));

        assertEquals("torch", result.tojstring());
    }

    @Test
    void reportsAnIncompleteInventoryClassInsteadOfSilentlyIgnoringIt() {
        assertThrows(LuaError.class,
            () -> MLuaClassMethods.invokeInstanceMethod(new LuaTable(), "SetSlot"));
    }
}
