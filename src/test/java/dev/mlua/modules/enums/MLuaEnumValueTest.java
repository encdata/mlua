package dev.mlua.modules.enums;

import dev.mlua.common.enums.AnimationPriority;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaEnumValueTest {
    @Test
    void canonicalEnumValueCannotBeChangedByOneLuaRuntime() {
        LuaValue firstRuntimeValue = MLuaEnumValue.forJava(AnimationPriority.Idle);
        LuaValue secondRuntimeValue = MLuaEnumValue.forJava(AnimationPriority.Idle);
        assertSame(firstRuntimeValue, secondRuntimeValue);

        var firstRuntime = JsePlatform.standardGlobals();
        var secondRuntime = JsePlatform.standardGlobals();
        firstRuntime.set("Priority", firstRuntimeValue);
        secondRuntime.set("Priority", secondRuntimeValue);

        firstRuntime.load("assert(type(Priority) == 'table')").call();
        assertThrows(LuaError.class, () -> firstRuntime.load("Priority.Name = 'Action'").call());
        assertThrows(LuaError.class, () -> firstRuntime.load("rawset(Priority, 'Name', 'Action')").call());
        assertThrows(LuaError.class, () -> firstRuntime.load("setmetatable(Priority, {})").call());
        assertThrows(LuaError.class, () -> firstRuntime.load("table.insert(Priority, 'Action')").call());
        assertEquals("Idle", secondRuntime.get("Priority").get("Name").tojstring());
        assertTrue(MLuaEnumValue.isEnum(secondRuntime.get("Priority"), AnimationPriority.class));
        assertSame(AnimationPriority.Idle,
                MLuaEnumValue.toJava(secondRuntime.get("Priority"), AnimationPriority.class));
    }
}
