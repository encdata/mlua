package dev.mlua.mc.impl.network;

import dev.mlua.mcapi.network.MCConnection;
import dev.mlua.mcapi.network.MCPacket;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class MCConnectionImpl implements MCConnection {
    private final Connection inner;

    public MCConnectionImpl(Connection inner) {
        this.inner = inner;
    }

    public static MCConnectionImpl fromReal(Connection inner) {
        return inner == null ? null : new MCConnectionImpl(inner);
    }

    public static MCConnection fromPlayerListener(ServerGamePacketListenerImpl listener) {
        if (listener == null) return null;
        return new MCConnection() {
            @Override
            public void send(MCPacket packet) {
                if (!(packet instanceof MCPacketImpl impl)) {
                    throw new IllegalArgumentException("Unsupported Minecraft packet adapter");
                }
                listener.send((Packet<?>) impl.inner());
            }

            @Override
            public ConnectionProtocol getProtocol() {
                return ConnectionProtocol.PLAY;
            }
        };
    }

    public Connection inner() {
        return inner;
    }

    @Override
    public void send(MCPacket packet) {
        if (packet instanceof MCPacketImpl impl) {
            inner.send((Packet<?>) impl.inner());
        }
    }

    @Override
    public ConnectionProtocol getProtocol() {
        net.minecraft.network.PacketListener listener = inner.getPacketListener();
        if (listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl || 
            listener instanceof net.minecraft.client.multiplayer.ClientPacketListener) {
            return ConnectionProtocol.PLAY;
        }
        if (listener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl) {
            return ConnectionProtocol.LOGIN;
        }
        if (listener instanceof net.minecraft.server.network.ServerStatusPacketListenerImpl) {
            return ConnectionProtocol.STATUS;
        }
        return ConnectionProtocol.HANDSHAKING;
    }
}
