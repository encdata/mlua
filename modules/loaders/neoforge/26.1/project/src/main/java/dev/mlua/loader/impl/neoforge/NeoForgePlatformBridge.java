package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.loaderapi.ScriptRootCollector;
import dev.mlua.loaderapi.ScriptRootRef;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.ModList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NeoForgePlatformBridge implements PlatformBridge {
    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean hasMod(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FMLLoader.getCurrent().getDist().isClient();
    }

    @Override
    public boolean isProduction() {
        return FMLLoader.getCurrent().isProduction();
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public Collection<ScriptRootRef> bundledScriptRoots() {
        List<ScriptRootRef> roots = new ArrayList<>();
        ModList.get().getMods().forEach(mod -> {
            var modFile = mod.getOwningFile().getFile();
            Path modPath = modFile.getFilePath();
            if (java.nio.file.Files.isDirectory(modPath)) {
                ScriptRootCollector.add(roots, mod.getModId(), modPath.resolve("assets").resolve(mod.getModId()).resolve("mlua").resolve("scripts"));
                ScriptRootCollector.add(roots, mod.getModId(), modPath.resolve("mlua").resolve("scripts"));
            }
        });
        return roots;
    }

}
