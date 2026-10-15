package dev.mlua.runtime.neoforge;

import dev.mlua.runtime.MLuaBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Installs the runtime mixin bridge for NeoForge 26.1, which has no ModLauncher service hook. */
@Mod("mlua")
public final class MLuaNeoForge26Mod {
    public MLuaNeoForge26Mod(IEventBus modEventBus) {
        try {
            Class<?> bootstrap = Class.forName("dev.mlua.agent.MLuaAgentBootstrap", true, getClass().getClassLoader());
            bootstrap.getMethod("ensureAttached").invoke(null);
            MLuaBootstrap.initialize(new NeoForgeLoaderProvider(modEventBus));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not install the MLua runtime mixin transformer", exception);
        }
    }
}
