package dev.mlua;

import dev.mlua.runtime.MLuaBootstrap;
import dev.mlua.core.MLuaRuntime;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod("mlua")
public final class MLuaMod {
    public static final String MOD_ID = "mlua";

    public MLuaMod() {
        MLuaBootstrap.initializeFromProviderClassName("dev.mlua.runtime.forge.ForgeLoaderProvider");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
            MLuaRuntime.get().runServerScripts(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
            MLuaRuntime.get().onServerStopping(event.getServer()));
    }
}
