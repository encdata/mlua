package dev.mlua.loader.impl.fabric;

import dev.mlua.mc.impl.fabric.FabricClientWiring;
import dev.mlua.mc.impl.fabric.FabricLifecycleWiring;
import dev.mlua.mc.impl.fabric.FabricNetworkWiring;
import net.fabricmc.api.ClientModInitializer;

/** Client-only Fabric entrypoint; it keeps GLFW and client Fabric API classes off server startup. */
public final class MLuaFabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoaderProvider provider = MLuaFabricMod.provider();
        provider.initializeClient();
        FabricLifecycleWiring.wireRender(provider.fabricLifecycle());
        FabricClientWiring.wire(provider.fabricClient());
        FabricNetworkWiring.wireClient(provider.fabricNetwork());
        provider.fabricLifecycle().fireClientSetup();
    }
}
