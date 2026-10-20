package dev.mlua.loader.impl.fabric;

import dev.mlua.mc.impl.fabric.FabricConfigWiring;
import dev.mlua.mc.impl.fabric.FabricServerLifecycleWiring;
import dev.mlua.mc.impl.fabric.FabricServerNetworkWiring;
import dev.mlua.mc.impl.fabric.FabricRegistryWiring;
import dev.mlua.runtime.MLuaBootstrap;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class MLuaFabricMod implements ModInitializer, DedicatedServerModInitializer {
    private static FabricLoaderProvider provider;

    @Override
    public void onInitialize() {
        provider = new FabricLoaderProvider();
        provider.fabricLifecycle().fireConstruct();

        FabricConfigWiring.wire(provider.fabricConfig(), "mlua");
        FabricServerLifecycleWiring.wire(provider.fabricLifecycle());

        MLuaBootstrap.initialize(provider);
        provider.fabricLifecycle().fireSetup();

        FabricRegistryWiring.wire(provider.fabricRegistry());
        provider.fabricLifecycle().fireLoadComplete();

        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
            FabricServerNetworkWiring.wire(provider.fabricNetwork(), server);
        });
    }

    @Override
    public void onInitializeServer() {
    }

    public static FabricLoaderProvider provider() {
        return provider;
    }
}
