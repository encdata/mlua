package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.loaderapi.ScriptRootCollector;
import dev.mlua.loaderapi.ScriptRootRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformBridge implements PlatformBridge {
    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean hasMod(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType().name().equalsIgnoreCase("client");
    }

    @Override
    public boolean isProduction() {
        return !FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public Collection<ScriptRootRef> bundledScriptRoots() {
        List<ScriptRootRef> roots = new ArrayList<>();
        FabricLoader.getInstance().getAllMods().forEach(mod -> {
            String modId = mod.getMetadata().getId();
            mod.findPath("assets/" + modId + "/mlua/scripts")
                .ifPresent(path -> ScriptRootCollector.add(roots, modId, path));
            mod.findPath("mlua/scripts")
                .ifPresent(path -> ScriptRootCollector.add(roots, modId, path));
        });
        return roots;
    }
}
