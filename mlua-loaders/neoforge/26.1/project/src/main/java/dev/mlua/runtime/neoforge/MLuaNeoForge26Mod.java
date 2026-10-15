package dev.mlua.runtime.neoforge;

import net.neoforged.fml.common.Mod;

/** Installs the runtime mixin bridge for NeoForge 26.1, which has no ModLauncher service hook. */
@Mod("mlua")
public final class MLuaNeoForge26Mod {
    public MLuaNeoForge26Mod() {
        try {
            Class<?> bootstrap = Class.forName("dev.mlua.agent.MLuaAgentBootstrap", true, getClass().getClassLoader());
            bootstrap.getMethod("ensureAttached").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not install the MLua runtime mixin transformer", exception);
        }
    }
}
