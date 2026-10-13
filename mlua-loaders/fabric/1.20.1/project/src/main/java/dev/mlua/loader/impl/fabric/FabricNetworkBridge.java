package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.NetworkBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FabricNetworkBridge implements NetworkBridge {
    private String modId;
    private String protocolVersion;
    private final List<BiConsumer<String, Consumer<Object>>> sendToServerHandlers = new ArrayList<>();
    private Consumer<Object> sendToServer;
    private BiConsumer<Object, String> sendToClient;
    private Consumer<Object> sendToAll;
    private volatile Function<String, Boolean> remotePresence;

    @Override
    public void init(String modId, String protocolVersion) {
        this.modId = modId;
        this.protocolVersion = protocolVersion;
    }

    @Override
    public void sendToServer(Object packet) {
        if (sendToServer == null) throw unsupported("sendToServer");
        sendToServer.accept(packet);
    }

    @Override
    public void sendToClient(Object packet, String playerId) {
        if (sendToClient == null) throw unsupported("sendToClient");
        sendToClient.accept(packet, playerId);
    }

    @Override
    public void sendToAll(Object packet) {
        if (sendToAll == null) throw unsupported("sendToAll");
        sendToAll.accept(packet);
    }

    @Override
    public boolean isRemotePresent(String playerId) {
        if (remotePresence == null) throw unsupported("isRemotePresent");
        return remotePresence.apply(playerId);
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Fabric packet-object networking is not wired for " + operation
            + "; use MLuaNetworkManager remote events for cross-loader Lua networking.");
    }

    public String getModId() {
        return modId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setSendToServer(Consumer<Object> handler) {
        this.sendToServer = handler;
    }

    public void setSendToClient(BiConsumer<Object, String> handler) {
        this.sendToClient = handler;
    }

    public void setSendToAll(Consumer<Object> handler) {
        this.sendToAll = handler;
    }

    public void setRemotePresence(Function<String, Boolean> checker) {
        this.remotePresence = checker;
    }
}
