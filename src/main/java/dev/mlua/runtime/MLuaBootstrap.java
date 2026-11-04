package dev.mlua.runtime;

import dev.mlua.api.MLuaApi;
import dev.mlua.api.RuntimeMLuaApiBridge;
import dev.mlua.common.mixin.MLuaMappingResolvers;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.LoaderApi.LoaderProvider;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.mcapi.MCApiRuntimeProvider;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ServiceLoader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class MLuaBootstrap {
    private static final String[] FALLBACK_PROVIDER_CLASSES = {
        "dev.mlua.loader.forge.ForgeLoaderProvider",
        "dev.mlua.loader.impl.fabric.FabricLoaderProvider"
    };

    private MLuaBootstrap() {
    }

    public static void initializeFromDiscoveredProvider() {
        initialize(discoverProvider());
    }

    public static void initializeFromProviderClassName(String className) {
        initialize(discoverProvider(className));
    }

    public static void initialize(LoaderProvider provider) {
        LoaderApi.install(provider);
        installTargetRuntimeProvider();
        if (provider.mappings() != null) {
            MLuaMappingResolvers.install(provider.mappings());
        } else {
            MLuaMappingResolvers.clear();
        }
        MLuaApi.install(new RuntimeMLuaApiBridge());
        ensureRuntimeTransformer();

        MLuaNetworkManager.get().init();

        MLuaRuntime runtime = MLuaRuntime.get();
        runtime.bootstrap();
        MLuaPalladiumIntegration.install();
        runtime.runMixinScripts();
        runtime.runRegistryScripts();
        MLuaConfigRegistry.get().install();
        MLuaLoaderRegistry.registerVisualBlockEntityTypes();
        var projectileType = MLuaLoaderRegistry.register(
            RegistryKind.ENTITY,
            "mlua",
            "scripted_projectile",
            MLuaScriptedProjectileType::createType
        );
        MLuaScriptedProjectileType.setRegistryHandle(projectileType);

        provider.lifecycle().onLoadComplete(LoaderApi.registry()::freeze);
        provider.lifecycle().onServerStarted(server -> runtime.runServerScripts((MinecraftServer) server));
        provider.lifecycle().onServerStopping(server -> runtime.onServerStopping((MinecraftServer) server));
        provider.events().onWorldLoad(world -> {
            if (world instanceof Level level) {
                runtime.runWorldLifecycleEntrypoint(level, true);
            }
        });
        provider.events().onWorldUnload(world -> {
            if (world instanceof Level level) {
                runtime.runWorldLifecycleEntrypoint(level, false);
            }
        });
        provider.lifecycle().onRegisterCommands(dispatcher -> runtime.onRegisterCommands((CommandDispatcher<CommandSourceStack>) dispatcher));
        provider.lifecycle().onServerTickEnd(runtime::onServerTickEnd);
        if (provider.platform().isClient()) {
            initializeClientRuntime(provider, runtime);
        }

        MLuaLogger.LOGGER.info("MLUA_RUNTIME_BOOTSTRAP_OK loader={}", provider.platform().loaderName());
    }

    private static void initializeClientRuntime(LoaderProvider provider, MLuaRuntime runtime) {
        try {
            Class<?> hooks = Class.forName("dev.mlua.runtime.client.MLuaClientRuntimeHooks", true,
                MLuaBootstrap.class.getClassLoader());
            hooks.getMethod("initialize", LoaderProvider.class, MLuaRuntime.class).invoke(null, provider, runtime);
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : failure;
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Failed to initialize MLua client runtime hooks", cause);
        }
    }

    private static void installTargetRuntimeProvider() {
        try {
            Class<?> type = Class.forName("dev.mlua.mc.impl.MCApiVersionRuntimeProvider");
            Object instance = type.getDeclaredConstructor().newInstance();
            if (instance instanceof MCApiRuntimeProvider runtimeProvider) {
                MCApiRuntime.installProvider(runtimeProvider);
            }
        } catch (ClassNotFoundException ignored) {
            // Older or standalone API launches continue using the compatibility provider.
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Failed to install the target MLua runtime provider.", exception);
        }
    }

    private static void ensureRuntimeTransformer() {
        try {
            Class<?> bootstrap = Class.forName("dev.mlua.agent.MLuaAgentBootstrap");
            bootstrap.getMethod("ensureAttached").invoke(null);
        } catch (Throwable throwable) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to prepare runtime transformer before mixin bootstrap", throwable);
        }
    }

    private static LoaderProvider discoverProvider() {
        var providers = ServiceLoader.load(LoaderProvider.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();
        if (providers.size() > 1) {
            var names = providers.stream()
                .map(provider -> provider.getClass().getName())
                .collect(Collectors.joining(", "));
            throw new IllegalStateException("Multiple MLua loader providers found: " + names);
        }
        if (providers.size() == 1) {
            return providers.get(0);
        }

        LoaderProvider fallback = tryFallbackProviders();
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalStateException("No MLua loader provider found via ServiceLoader");
    }

    private static LoaderProvider discoverProvider(String className) {
        Set<ClassLoader> loaders = collectCandidateLoaders();
        for (ClassLoader loader : loaders) {
            LoaderProvider provider = tryInstantiateProvider(className, loader);
            if (provider != null) {
                return provider;
            }
        }
        throw new IllegalStateException("Unable to initialize MLua loader provider: " + className);
    }

    private static LoaderProvider tryFallbackProviders() {
        Set<ClassLoader> loaders = collectCandidateLoaders();

        for (String className : FALLBACK_PROVIDER_CLASSES) {
            for (ClassLoader loader : loaders) {
                LoaderProvider provider = tryInstantiateProvider(className, loader);
                if (provider != null) {
                    return provider;
                }
            }
        }
        return null;
    }

    private static LoaderProvider tryInstantiateProvider(String className, ClassLoader loader) {
        if (loader == null) {
            return null;
        }

        try {
            Class<?> providerClass = Class.forName(className, true, loader);
            if (!LoaderProvider.class.isAssignableFrom(providerClass)) {
                return null;
            }
            return (LoaderProvider) providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            MLuaLogger.LOGGER.warn(
                "[MLua] Failed to instantiate loader provider '{}' with classloader '{}'",
                className,
                loader,
                exception
            );
            return null;
        }
    }

    private static Set<ClassLoader> collectCandidateLoaders() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(MLuaBootstrap.class.getClassLoader());
        loaders.add(LoaderProvider.class.getClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());
        addModuleLoader(loaders, "mlua_loader");
        addModuleLoader(loaders, "mlua");
        return loaders;
    }

    private static void addModuleLoader(Set<ClassLoader> loaders, String moduleName) {
        ModuleLayer layer = MLuaBootstrap.class.getModule().getLayer();
        if (layer != null) {
            try {
                loaders.add(layer.findLoader(moduleName));
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }

        try {
            loaders.add(ModuleLayer.boot().findLoader(moduleName));
        } catch (IllegalArgumentException ignored) {
        }
    }
}
