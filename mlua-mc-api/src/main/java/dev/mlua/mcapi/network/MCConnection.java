package dev.mlua.mcapi.network;

public interface MCConnection {
    void send(MCPacket packet);
    ConnectionProtocol getProtocol();

    enum ConnectionProtocol {
        HANDSHAKING,
        PLAY,
        STATUS,
        LOGIN
    }
}
