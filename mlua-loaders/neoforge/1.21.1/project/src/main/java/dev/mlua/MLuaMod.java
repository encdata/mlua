package dev.mlua;

import dev.mlua.runtime.MLuaBootstrap;
import dev.mlua.runtime.neoforge.NeoForgeLoaderProvider;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

@Mod("mlua")
public final class MLuaMod {
    public static final String MOD_ID = "mlua";

    public MLuaMod(IEventBus modEventBus) {
        MLuaBootstrap.initialize(new NeoForgeLoaderProvider(modEventBus));
    }
}
