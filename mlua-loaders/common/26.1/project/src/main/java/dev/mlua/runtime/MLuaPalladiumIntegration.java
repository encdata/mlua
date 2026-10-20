package dev.mlua.runtime;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptLoader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loads Lua scripts bundled in Palladium addon packs when Palladium is installed. */
public final class MLuaPalladiumIntegration {
    private static boolean installed;

    private MLuaPalladiumIntegration() {}

    public static void install() {
        if (installed) return;
        try {
            Class<?> managerType = Class.forName("net.threetag.palladium.addonpack.AddonPackManager");
            Object manager = managerType.getMethod("getInstance").invoke(null);
            managerType.getMethod("registerReloadListener", PreparableReloadListener.class)
                .invoke(manager, new Listener());
            installed = true;
            MLuaLogger.LOGGER.info("[MLua] Palladium addon-pack script integration enabled");
        } catch (ClassNotFoundException ignored) {
            // Palladium is optional.
        } catch (ReflectiveOperationException exception) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to install Palladium addon-pack integration", exception);
        }
    }

    private static final class Listener implements PreparableReloadListener {
        @Override
        public String getName() {
            return "MLua Palladium scripts";
        }

        @Override
        public CompletableFuture<Void> reload(
            SharedState reloadState,
            Executor taskExecutor,
            PreparationBarrier barrier,
            Executor reloadExecutor
        ) {
            return CompletableFuture.supplyAsync(() -> collect(reloadState.resourceManager()), taskExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(scripts -> MLuaRuntime.get().replaceResourceScripts(scripts), reloadExecutor);
        }

        private static List<MLuaScript> collect(ResourceManager manager) {
            MLuaScriptLoader loader = new MLuaScriptLoader();
            List<MLuaScript> scripts = new ArrayList<>();
            Map<Identifier, Resource> resources = manager.listResources(
                "mlua/scripts", id -> id.getPath().toLowerCase(Locale.ROOT).endsWith(".lua"));

            resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    String relative = entry.getKey().getPath().substring("mlua/scripts/".length());
                    try (var reader = entry.getValue().openAsReader()) {
                        StringWriter source = new StringWriter();
                        reader.transferTo(source);
                        Path root = Path.of("palladium-addonpacks", entry.getKey().getNamespace());
                        scripts.add(loader.fromResource(
                            entry.getKey().getNamespace(), root, relative, source.toString()));
                    } catch (IOException exception) {
                        MLuaLogger.LOGGER.warn("[MLua] Failed to read Palladium script {}", entry.getKey(), exception);
                    }
                });
            return scripts;
        }
    }
}
