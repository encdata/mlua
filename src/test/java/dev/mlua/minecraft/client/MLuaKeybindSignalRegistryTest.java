package dev.mlua.minecraft.client;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntimeSession;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaKeybindSignalRegistryTest {
    @Test
    void sameKeybindIdIsIsolatedBetweenRuntimeSessionsAndCleanup() {
        MLuaKeybindSignalRegistry registry = new MLuaKeybindSignalRegistry();
        MLuaRuntimeSession first = new MLuaRuntimeSession();
        MLuaRuntimeSession second = new MLuaRuntimeSession();
        var firstScheduler = first.clientScheduler();
        var secondScheduler = second.clientScheduler();
        var firstGlobals = JsePlatform.standardGlobals();
        var secondGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();

        var firstSignal = registry.signal("example:jump", firstScheduler);
        var secondSignal = registry.signal("example:jump", secondScheduler);
        assertNotSame(firstSignal, secondSignal);
        firstSignal.connectOwned(firstGlobals.load("count = count + 1"), "example", Side.CLIENT,
                firstGlobals, firstScheduler);
        secondSignal.connectOwned(secondGlobals.load("count = count + 1"), "example", Side.CLIENT,
                secondGlobals, secondScheduler);

        assertTrue(registry.hasSignal("example:jump"));
        registry.fireAll("example:jump", Side.CLIENT, LuaValue.NONE);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());

        registry.clearSession(first, Side.CLIENT);
        assertTrue(registry.hasSignal("example:jump"));
        registry.fireAll("example:jump", Side.CLIENT, LuaValue.NONE);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(2, secondGlobals.get("count").toint());
    }
}
