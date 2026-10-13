package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.NetworkBridge;
import dev.mlua.minecraft.network.MLuaNetworkManager;

public class ForgeNetworkBridge implements NetworkBridge {
    private final MLuaNetworkManager manager = MLuaNetworkManager.get();

    @Override
    public void init(String modId, String protocolVersion) {
        manager.init();
    }

    @Override
    public void sendToServer(Object packet) {
        throw unsupported("sendToServer");
    }

    @Override
    public void sendToClient(Object packet, String playerId) {
        throw unsupported("sendToClient");
    }

    @Override
    public void sendToAll(Object packet) {
        throw unsupported("sendToAll");
    }

    @Override
    public boolean isRemotePresent(String playerId) {
        throw unsupported("isRemotePresent");
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("NeoForge packet-object networking does not support " + operation
            + "; use MLuaNetworkManager remote events for cross-loader Lua networking.");
    }
}
