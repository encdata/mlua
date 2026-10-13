package dev.mlua.runtime;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptLoader;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Optional integration with Palladium's addon-pack resource manager. */
public final class MLuaPalladiumIntegration {
    private static boolean installed;

    private MLuaPalladiumIntegration() {
    }

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
        } catch (Throwable throwable) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to install Palladium addon-pack integration", throwable);
        }
    }

    private static final class Listener implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                              ProfilerFiller preparationProfiler, ProfilerFiller applicationProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> collect(manager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(scripts -> MLuaRuntime.get().replaceResourceScripts(scripts), gameExecutor);
        }

        private static List<MLuaScript> collect(ResourceManager manager) {
            MLuaScriptLoader loader = new MLuaScriptLoader();
            List<MLuaScript> scripts = new ArrayList<>();
            Map<net.minecraft.resources.ResourceLocation, Resource> resources = manager.listResources(
                "mlua/scripts", path -> path.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".lua"));
            resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .forEach(entry -> {
                    String relative = entry.getKey().getPath().substring("mlua/scripts/".length());
                    try (var reader = entry.getValue().openAsReader()) {
                        String source = reader.lines().reduce("", (a, b) -> a + b + "\n");
                        Path root = Path.of("palladium-addonpacks", entry.getKey().getNamespace());
                        scripts.add(loader.fromResource(entry.getKey().getNamespace(), root, relative, source));
                    } catch (IOException exception) {
                        MLuaLogger.LOGGER.warn("[MLua] Failed to read Palladium script {}", entry.getKey(), exception);
                    }
                });
            return scripts;
        }
    }
}
