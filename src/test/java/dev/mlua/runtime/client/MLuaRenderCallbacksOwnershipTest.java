package dev.mlua.runtime.client;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaRenderCallbacksOwnershipTest {
    @Test
    void addonTeardownReleasesOnlyItsCallbacksAndRuntimeTeardownReleasesTheRest() {
        MLuaRuntimeSession session = new MLuaRuntimeSession();
        MLuaContext firstAddon = context(session, "first_addon");
        MLuaContext secondAddon = context(session, "second_addon");
        int initialCount = MLuaRenderCallbacks.registeredCallbackCount();
        try {
            MLuaRenderCallbacks.registerEntityDraw("minecraft:pig", "test:first", callback(), firstAddon);
            MLuaRenderCallbacks.registerEntityDraw("minecraft:pig", "test:second", callback(), secondAddon);

            assertEquals(initialCount + 2, MLuaRenderCallbacks.registeredCallbackCount());
            assertEquals(1, MLuaRenderCallbacks.registeredCallbackCount(firstAddon));
            assertEquals(1, MLuaRenderCallbacks.registeredCallbackCount(secondAddon));

            MLuaRenderCallbacks.clearOwned(session, "first_addon");
            assertEquals(initialCount + 1, MLuaRenderCallbacks.registeredCallbackCount());
            assertEquals(0, MLuaRenderCallbacks.registeredCallbackCount(firstAddon));
            assertEquals(1, MLuaRenderCallbacks.registeredCallbackCount(secondAddon));

            MLuaRenderCallbacks.clearRuntime(session);
            assertEquals(initialCount, MLuaRenderCallbacks.registeredCallbackCount());
            assertEquals(0, MLuaRenderCallbacks.registeredCallbackCount(secondAddon));
        } finally {
            MLuaRenderCallbacks.clearRuntime(session);
        }
    }

    private static LuaValue callback() {
        return new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        };
    }

    private static MLuaContext context(MLuaRuntimeSession session, String owner) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        MLuaScript script = new MLuaScript(owner, ScriptType.CLIENT, root,
            root.resolve(owner + ".client.lua"), owner, "");
        return new MLuaContext(script, Side.CLIENT, RegistrationPhase.RUNTIME, new MLuaServiceRegistry(),
            new MLuaTaskScheduler(), new MLuaModuleCache(), null, root.resolve("data"), null, session);
    }
}
