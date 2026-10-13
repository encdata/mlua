package dev.mlua.runtime.forge;

import dev.mlua.loader.impl.forge.*;
import dev.mlua.mc.impl.forge.MLuaForgeNetworkAdapter;
import dev.mlua.loaderapi.*;
import dev.mlua.common.mixin.MLuaGeneratedMappingResolver;
import dev.mlua.runtime.registry.MLuaForgeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;

public class ForgeLoaderProvider implements LoaderApi.LoaderProvider {
    private final ForgePlatformBridge platform;
    private final ForgeEventBridge events;
    private final ForgeRegistryBridge registry;
    private final ForgeNetworkBridge network;
    private final ForgeConfigBridge config;
    private final ForgeLifecycleBridge lifecycle;
    private final ForgeClientBridge client;

    public ForgeLoaderProvider() {
        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        MLuaForgeRegistry.get().attach(modBus);
        modBus.addListener(MLuaForgeRegistry.get()::onEntityAttributeCreation);
        modBus.addListener(MLuaForgeNetworkAdapter::registerPayloadHandlers);
        this.platform = new ForgePlatformBridge();
        this.events = new ForgeEventBridge(modBus);
        this.registry = new ForgeRegistryBridge();
        this.network = new ForgeNetworkBridge();
        this.config = new ForgeConfigBridge();
        this.lifecycle = new ForgeLifecycleBridge(modBus);
        this.client = new ForgeClientBridge(modBus);
    }

    @Override
    public PlatformBridge platform() {
        return platform;
    }

    @Override
    public EventBridge events() {
        return events;
    }

    @Override
    public RegistryBridge registry() {
        return registry;
    }

    @Override
    public NetworkBridge network() {
        return network;
    }

    @Override
    public ConfigBridge config() {
        return config;
    }

    @Override
    public LifecycleBridge lifecycle() {
        return lifecycle;
    }

    @Override
    public ClientBridge client() {
        return client;
    }

    @Override
    public dev.mlua.common.mixin.MLuaMappingResolver mappings() {
        return new MLuaGeneratedMappingResolver("neoforge");
    }
}
