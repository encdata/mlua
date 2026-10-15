package dev.mlua.runtime.neoforge;

import dev.mlua.common.mixin.MLuaGeneratedMappingResolver;
import dev.mlua.loader.impl.neoforge.NeoForgeClientBridge;
import dev.mlua.loader.impl.neoforge.NeoForgeConfigBridge;
import dev.mlua.loader.impl.neoforge.NeoForgeEventBridge;
import dev.mlua.loader.impl.neoforge.NeoForgeLifecycleBridge;
import dev.mlua.loader.impl.neoforge.NeoForgeNetworkBridge;
import dev.mlua.loader.impl.neoforge.NeoForgePlatformBridge;
import dev.mlua.loader.impl.neoforge.NeoForgeRegistryBridge;
import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.ConfigBridge;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.LifecycleBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.NetworkBridge;
import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.loaderapi.RegistryBridge;
import net.neoforged.bus.api.IEventBus;

/** Loader service implementation for NeoForge 1.21.x. */
public final class NeoForgeLoaderProvider implements LoaderApi.LoaderProvider {
    private final PlatformBridge platform = new NeoForgePlatformBridge();
    private final EventBridge events;
    private final RegistryBridge registry = new NeoForgeRegistryBridge();
    private final NetworkBridge network = new NeoForgeNetworkBridge();
    private final ConfigBridge config = new NeoForgeConfigBridge();
    private final LifecycleBridge lifecycle;
    private final ClientBridge client;

    public NeoForgeLoaderProvider(IEventBus modBus) {
        MLuaNeoForgeRegistry.get().attach(modBus);
        modBus.addListener(MLuaNeoForgeRegistry.get()::onEntityAttributeCreation);
        events = new NeoForgeEventBridge(modBus);
        lifecycle = new NeoForgeLifecycleBridge(modBus);
        client = platform.isClient() ? new NeoForgeClientBridge(modBus) : ClientBridge.unavailable();
    }

    @Override public PlatformBridge platform() { return platform; }
    @Override public EventBridge events() { return events; }
    @Override public RegistryBridge registry() { return registry; }
    @Override public NetworkBridge network() { return network; }
    @Override public ConfigBridge config() { return config; }
    @Override public LifecycleBridge lifecycle() { return lifecycle; }
    @Override public ClientBridge client() { return client; }
    @Override public dev.mlua.common.mixin.MLuaMappingResolver mappings() {
        return new MLuaGeneratedMappingResolver("neoforge");
    }
}
