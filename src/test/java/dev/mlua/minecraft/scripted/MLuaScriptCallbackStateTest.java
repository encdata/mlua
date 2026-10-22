package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptCallbackStateTest {
    @Test
    void behaviorReplacementClearsFailuresAndReportsLiveReplacement() {
        MLuaScriptCallbackState state = new MLuaScriptCallbackState();
        LuaValue callback = new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        };
        Object firstDefinition = new Object();
        Object replacement = new Object();

        assertFalse(state.refreshDefinition(firstDefinition));
        assertTrue(state.shouldInvoke("onTick", callback));
        state.disable("onTick");
        assertFalse(state.shouldInvoke("onTick", callback));

        assertTrue(state.refreshDefinition(replacement));
        assertTrue(state.shouldInvoke("onTick", callback));
        assertFalse(state.refreshDefinition(replacement));
    }

    @Test
    void clearingAnEntityDropsItsDefinitionIdentity() {
        MLuaScriptCallbackState state = new MLuaScriptCallbackState();
        Object definition = new Object();

        state.refreshDefinition(definition);
        state.clear();

        assertFalse(state.refreshDefinition(definition));
    }
}
