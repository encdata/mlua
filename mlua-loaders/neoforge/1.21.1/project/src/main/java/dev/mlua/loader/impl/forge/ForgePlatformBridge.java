package dev.mlua.loader.impl.forge;

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

public class ForgePlatformBridge implements PlatformBridge {
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
        return FMLLoader.getDist().isClient();
    }

    @Override
    public boolean isProduction() {
        return FMLLoader.isProduction();
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
            ScriptRootCollector.add(roots, mod.getModId(),
                modFile.findResource("assets", mod.getModId(), "mlua", "scripts"));
            ScriptRootCollector.add(roots, mod.getModId(), modFile.findResource("mlua", "scripts"));
        });
        return roots;
    }

}
