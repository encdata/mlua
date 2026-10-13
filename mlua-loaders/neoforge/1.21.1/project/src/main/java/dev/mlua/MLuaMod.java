package dev.mlua;

import dev.mlua.runtime.MLuaBootstrap;
import net.neoforged.fml.common.Mod;

@Mod("mlua")
public final class MLuaMod {
    public static final String MOD_ID = "mlua";

    public MLuaMod() {
        MLuaBootstrap.initializeFromProviderClassName("dev.mlua.runtime.forge.ForgeLoaderProvider");
    }
}
