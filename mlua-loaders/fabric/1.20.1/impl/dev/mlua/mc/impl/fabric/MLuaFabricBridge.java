package dev.mlua.mc.impl.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import java.nio.file.Path;
import java.util.Optional;

public final class MLuaFabricBridge {
    private MLuaFabricBridge() {
    }

    public static Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
    }

    public static boolean isProduction() {
        return !FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static Optional<ModContainer> getMod(String modId) {
        return FabricLoader.getInstance().getModContainer(modId);
    }

    public static FabricLoader getLoader() {
        return FabricLoader.getInstance();
    }
}
