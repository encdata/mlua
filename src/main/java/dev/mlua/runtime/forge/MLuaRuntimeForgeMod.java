package dev.mlua.runtime.forge;

import dev.mlua.runtime.MLuaBootstrap;
import net.minecraftforge.fml.common.Mod;

@Mod("mlua")
public final class MLuaRuntimeForgeMod {
    public MLuaRuntimeForgeMod() {
        MLuaBootstrap.initializeFromProviderClassName("dev.mlua.runtime.forge.ForgeLoaderProvider");
    }
}
