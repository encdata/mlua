package dev.mlua.loaderapi;

public interface NetworkBridge {
    void init(String modId, String protocolVersion);
    void sendToServer(Object packet);
    void sendToClient(Object packet, String playerId);
    void sendToAll(Object packet);
    boolean isRemotePresent(String playerId);
}
