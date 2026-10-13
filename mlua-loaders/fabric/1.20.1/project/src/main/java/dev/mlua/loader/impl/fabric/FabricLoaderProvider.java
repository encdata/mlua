package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.ConfigBridge;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.LifecycleBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.NetworkBridge;
import dev.mlua.loaderapi.PlatformBridge;
import dev.mlua.loaderapi.RegistryBridge;

public final class FabricLoaderProvider implements LoaderApi.LoaderProvider {
    private final FabricPlatformBridge platform = new FabricPlatformBridge();
    private final FabricEventBridge events = new FabricEventBridge();
    private final FabricRegistryBridge registry = new FabricRegistryBridge();
    private final FabricNetworkBridge network = new FabricNetworkBridge();
    private final FabricConfigBridge config = new FabricConfigBridge();
    private final FabricLifecycleBridge lifecycle = new FabricLifecycleBridge();
    private FabricClientBridge client;

    public void initializeClient() {
        if (client == null) client = new FabricClientBridge();
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

    public FabricPlatformBridge fabricPlatform() {
        return platform;
    }

    public FabricEventBridge fabricEvents() {
        return events;
    }

    public FabricRegistryBridge fabricRegistry() {
        return registry;
    }

    public FabricNetworkBridge fabricNetwork() {
        return network;
    }

    public FabricConfigBridge fabricConfig() {
        return config;
    }

    public FabricLifecycleBridge fabricLifecycle() {
        return lifecycle;
    }

    public FabricClientBridge fabricClient() {
        initializeClient();
        return client;
    }
}
