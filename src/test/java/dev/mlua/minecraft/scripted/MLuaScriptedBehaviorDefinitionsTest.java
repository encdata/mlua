package dev.mlua.minecraft.scripted;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedBehaviorDefinitionsTest {
    private static final String DOMAIN = "block";
    private static final String KEY = "mlua_test_behavior:registry_owner";

    @Test
    void rollbackRestoresPreviousCallbackSetOnlyWhileFailedDefinitionIsCurrent() {
        LuaValue originalCallback = callback();
        LuaValue failedCallback = callback();
        LuaValue laterCallback = callback();
        try {
            Map<String, LuaValue> original = MLuaBehaviorRegistry.registerBehaviors(
                    DOMAIN, KEY, Map.of("onUse", originalCallback));
            MLuaBehaviorRegistry.Snapshot previous = MLuaBehaviorRegistry.snapshotBehaviors(DOMAIN, KEY);

            Map<String, LuaValue> failed = MLuaBehaviorRegistry.registerBehaviors(
                    DOMAIN, KEY, Map.of("onUse", failedCallback));
            assertTrue(MLuaBehaviorRegistry.restoreBehaviors(DOMAIN, KEY, failed, previous, null));
            assertSame(originalCallback, MLuaBehaviorRegistry.getBehavior(DOMAIN, KEY, "onUse"));

            Map<String, LuaValue> stale = MLuaBehaviorRegistry.registerBehaviors(
                    DOMAIN, KEY, Map.of("onUse", failedCallback));
            MLuaBehaviorRegistry.registerBehaviors(DOMAIN, KEY, Map.of("onUse", laterCallback));
            assertFalse(MLuaBehaviorRegistry.restoreBehaviors(DOMAIN, KEY, stale, previous, null));
            assertSame(laterCallback, MLuaBehaviorRegistry.getBehavior(DOMAIN, KEY, "onUse"));
        } finally {
            MLuaBehaviorRegistry.clearOwner("mlua_test_behavior");
        }
    }

    @Test
    void individualCallbackUpdatesRetainOtherEventsAndNilRemovesOneEvent() {
        LuaValue useCallback = callback();
        LuaValue breakCallback = callback();
        try {
            MLuaBehaviorRegistry.registerBehaviors(DOMAIN, KEY,
                    Map.of("onUse", useCallback, "onBreak", breakCallback));

            MLuaBehaviorRegistry.registerBehavior(DOMAIN, KEY, "onUse", LuaValue.NIL);

            assertTrue(MLuaBehaviorRegistry.getBehavior(DOMAIN, KEY, "onUse").isnil());
            assertSame(breakCallback, MLuaBehaviorRegistry.getBehavior(DOMAIN, KEY, "onBreak"));
        } finally {
            MLuaBehaviorRegistry.clearOwner("mlua_test_behavior");
        }
    }

    private static LuaValue callback() {
        return new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        };
    }
}
